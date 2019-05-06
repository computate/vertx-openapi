package io.vertx.ext.openapi.impl;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.netty.handler.codec.http.QueryStringEncoder;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.json.schema.*;
import io.vertx.ext.json.schema.draft7.Draft7SchemaParser;
import io.vertx.ext.json.schema.generic.URIUtils;
import io.vertx.ext.json.schema.generic.ObservableFuture;
import io.vertx.ext.openapi.OpenAPILoader;
import io.vertx.ext.openapi.OpenAPILoaderOptions;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class OpenAPILoaderImpl implements OpenAPILoader {

  private final Map<URI, JsonObject> absolutePaths;
  private final HttpClient client;
  private final FileSystem fs;
  private final SchemaRouter router;
  private final SchemaParser parser;
  private final Schema openapiSchema;
  private final OpenAPILoaderOptions options;
  private URI initialScope;
  private String initialScopeDirectory;
  private final Map<URI, ObservableFuture<JsonObject>> externalSolvingRefs;
  private final YAMLMapper yamlMapper;
  private JsonObject openapiRoot;
  private JsonObject openapiRootFlattened;

  private static URI openapiSchemaURI;
  private static JsonObject openapiSchemaJson;

  static {
    try {
      openapiSchemaURI = OpenAPILoaderImpl.class.getResource("/openapi_3_schema.json").toURI();
      openapiSchemaJson = new JsonObject(
          String.join("",
              Files.readAllLines(Paths.get(openapiSchemaURI))
          )
      );
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public OpenAPILoaderImpl(HttpClient client, FileSystem fs, OpenAPILoaderOptions options) {
    absolutePaths = new ConcurrentHashMap<>();
    externalSolvingRefs = new ConcurrentHashMap<>();
    this.client = client;
    this.fs = fs;
    this.options = options;
    this.router = SchemaRouter.create(client, fs, options.toSchemaRouterOptions());
    this.parser = Draft7SchemaParser.create(new SchemaParserOptions(), this.router);
    this.yamlMapper = new YAMLMapper();
    this.openapiSchema = parser.parse(openapiSchemaJson, openapiSchemaURI);
  }

  @Override
  public Future<JsonObject> loadOpenAPI(String u) {
    URI uri = URIUtils.removeFragment(URI.create(u));
    Future<JsonObject> resolvedOpenAPIDocumentUnparsed = (URIUtils.isRemoteURI(uri)) ? solveRemoteRef(uri) : solveLocalRef(uri);
    initialScope = (URIUtils.isRemoteURI(uri)) ? uri : URI.create(sanitizeLocalRef(uri));
    initialScopeDirectory = Paths.get(initialScope.getPath()).resolveSibling("").toString();
    return resolvedOpenAPIDocumentUnparsed
        .compose(openapi -> {
          absolutePaths.put(initialScope, openapi); // Circular refs hell!
          openapiRoot = openapi;
          return walkAndSolve(openapi, initialScope).map(openapi);
        })
        .compose(openapi -> {
          JsonObject openapiCopy = openapi.copy();
          deepSubstituteForValidation(openapiCopy, JsonPointer.fromURI(initialScope));
          openapiRootFlattened = openapiCopy;
          return openapiSchema.validateAsync(openapiCopy).map(openapi);
        });
  }

  @Override
  public JsonObject getCached(JsonPointer pointer) {
    JsonObject startingObj = absolutePaths.get(resolveRefResolutionURIWithoutFragment(pointer.getURIWithoutFragment(), initialScope));
    return (JsonObject) pointer.queryJson(startingObj);
  }

  @Override
  public JsonObject solveIfNeeded(JsonObject obj) {
    if (obj.containsKey("$ref"))
      return getCached(JsonPointer.fromURI(URI.create(obj.getString("$ref"))));
    else return obj;
  }

  @Override
  public JsonObject getOpenAPI() {
    return openapiRoot;
  }

  @Override
  public JsonObject getOpenAPIResolved() {
    return openapiRootFlattened;
  }

  private Future<Void> walkAndSolve(JsonObject obj, URI scope) {
    List<JsonObject> candidateRefs = new ArrayList<>();
    Set<URI> refsToSolve = new HashSet<>();
    deepGetAllRefs(obj, candidateRefs);
    if (candidateRefs.isEmpty()) return Future.succeededFuture();

    for (JsonObject ref : candidateRefs) { // Make refs absolutes and check what refs must be solved
      JsonPointer parsedRef = JsonPointer.fromURI(URI.create(ref.getString("$ref")));
      if (!parsedRef.getURIWithoutFragment().isAbsolute()) // Ref not absolute, make it absolute based on scope
        parsedRef = JsonPointer.fromURI(
            URIUtils.replaceFragment(
                resolveRefResolutionURIWithoutFragment(parsedRef.getURIWithoutFragment(), scope), parsedRef.toURI().getFragment()
            )
        );
      URI solvedURI = parsedRef.toURI();
      ref.put("$ref", solvedURI.toString()); // Replace ref
      if (!absolutePaths.containsKey(parsedRef.getURIWithoutFragment()))
        refsToSolve.add(parsedRef.getURIWithoutFragment());
    }
    return CompositeFuture
        .all(refsToSolve.stream().map(this::resolveExternalRef).collect(Collectors.toList()))
        .compose(cf -> Future.succeededFuture());
  }

  private void deepGetAllRefs(Object obj, List<JsonObject> refsList) {
    if (obj instanceof JsonObject) {
      JsonObject jsonObject = (JsonObject) obj;
      if (jsonObject.containsKey("$ref"))
        refsList.add(jsonObject);
      else
        for (String keys : jsonObject.fieldNames()) deepGetAllRefs(jsonObject.getValue(keys), refsList);
    }
    if (obj instanceof JsonArray) {
      for (Object in : ((JsonArray) obj)) deepGetAllRefs(in, refsList);
    }
  }

  private void deepSubstituteForValidation(Object obj, JsonPointer scope) {
    if (obj instanceof JsonObject) {
      JsonObject jsonObject = (JsonObject) obj;
      if (jsonObject.containsKey("$ref")) {
        JsonPointer pointer = JsonPointer.fromURI(URI.create(jsonObject.getString("$ref")));
        if (!pointer.isParent(scope)) { // Circular refs hell!
          JsonObject resolved = solveIfNeeded(getCached(pointer)).copy();
          jsonObject.remove("$ref");
          jsonObject.mergeIn(resolved);
          jsonObject.put("x-$ref", pointer.toURI().toString());
          deepSubstituteForValidation(jsonObject, pointer);
        }
      } else
        for (String key : jsonObject.fieldNames()) deepSubstituteForValidation(jsonObject.getValue(key), scope.copy().append(key));
    }
    if (obj instanceof JsonArray) {
      for (int i = 0; i < ((JsonArray)obj).size(); i++) deepSubstituteForValidation(((JsonArray)obj).getValue(i), scope.copy().append(Integer.toString(i)));
    }
  }

  private ObservableFuture<JsonObject> resolveExternalRef(final URI ref) {
    return externalSolvingRefs.computeIfAbsent(ref,
        uri ->
          ObservableFuture.wrap(
              ((URIUtils.isRemoteURI(uri)) ? solveRemoteRef(uri) : solveLocalRef(uri))
                  .compose(j -> {
                    absolutePaths.put(uri, j); // Circular refs hell!
                    return walkAndSolve(j, uri).map(j);
                  })
          )
    );
  }

  private Future<JsonObject> solveRemoteRef(final URI ref) {
    Future<JsonObject> fut = Future.future();
    String uri = ref.toString();
    if (!options.getAuthQueryParams().isEmpty()) {
      QueryStringEncoder encoder = new QueryStringEncoder(uri);
      options.getAuthQueryParams().forEach(encoder::addParam);
      uri = encoder.toString();
    }
    HttpClientRequest req = client.getAbs(uri, res -> {
      if (res.failed()) fut.fail(res.cause());
      else {
        res.result().exceptionHandler(fut::fail);
        if (res.result().statusCode() == 200) {
          res.result().bodyHandler(buf -> {
            try {
              fut.complete(buf.toJsonObject());
            } catch (DecodeException e) {
              // Maybe it's yaml
              try {
                fut.complete(new JsonObject(
                  yamlMapper.readTree(buf.toString()).toString()
                ));
              } catch (Exception e1) {
                fut.fail(e1);
              }
            }
          });
        } else {
          fut.fail(new IllegalStateException(
            "Wrong status code " + res.result().statusCode() + " " + res.result().statusMessage() + " received while resolving remote ref"
          ));
        }
      }
    }).putHeader(HttpHeaders.ACCEPT.toString(), "application/json, application/yaml, application/x-yaml");
    options.getAuthHeaders().forEach(req::putHeader);
    req.end();
    return fut;
  }

  private Future<JsonObject> solveLocalRef(final URI ref) {
    Future<JsonObject> fut = Future.future();
    String filePath = sanitizeLocalRef(ref);
    fs.readFile(filePath, res -> {
      if (res.succeeded()) {
        try {
          fut.complete(res.result().toJsonObject());
        } catch (DecodeException e) {
          // Maybe it's yaml
          try {
            fut.complete(new JsonObject(
                yamlMapper.readTree(res.result().toString()).toString()
            ));
          } catch (Exception e1) {
            fut.fail(e1);
          }
        }
      } else {
        fut.fail(res.cause());
      }
    });
    return fut;
  }

  private URI resolveRefResolutionURIWithoutFragment(URI ref, URI scope) {
    if (ref.isAbsolute()) return URIUtils.removeFragment(ref);
    if (ref.getPath() != null && !ref.getPath().isEmpty() && !URIUtils.removeFragment(ref).equals(scope)) {
      if (ref.toString().startsWith(initialScopeDirectory))
        return URIUtils.removeFragment(ref);
      else
        return URIUtils.removeFragment(URIUtils.resolvePath(scope, ref.getPath()));
    }
    return scope;
  }

  private String sanitizeLocalRef(URI ref) {
    return ("jar".equals(ref.getScheme())) ? ref.getSchemeSpecificPart().split("!")[1].substring(1) : ref.getPath();
  }

}
