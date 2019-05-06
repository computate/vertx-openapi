package io.vertx.ext.openapi;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AbstractUser;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.json.schema.ValidationException;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BasicAuthHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static io.vertx.ext.openapi.asserts.MyAssertions.assertThat;

@ExtendWith(VertxExtension.class)
@Timeout(value = 1, timeUnit = TimeUnit.SECONDS)
public class OpenAPILoaderTest {

  private HttpServer schemaServer;

  private static final Handler<RoutingContext> queryParamAuthMock = rc -> {
    if (rc.queryParam("francesco") == null || !"slinky".equals(rc.queryParam("francesco").get(0)))
      rc.fail(401);
    else
      rc.next();
  };
  private static final Handler<RoutingContext> headerAuthMock = BasicAuthHandler.create((jsonObject, handler) -> {
    if ("francesco".equals(jsonObject.getString("username")) && "slinky".equals(jsonObject.getString("password")))
      handler.handle(Future.succeededFuture(new AbstractUser() {
        @Override
        protected void doIsPermitted(String s, Handler<AsyncResult<Boolean>> handler) {
          handler.handle(Future.succeededFuture(true));
        }

        @Override
        public JsonObject principal() {
          return jsonObject;
        }

        @Override
        public void setAuthProvider(AuthProvider authProvider) { }
      }));
    else
      handler.handle(Future.failedFuture("Not match"));
  });

  @AfterEach
  public void tearDown(VertxTestContext testContext) throws Exception {
    stopSchemaServer(testContext.completing());
  }

  @Test
  public void loadFromFileNoRef(Vertx vertx, VertxTestContext testContext) {
    OpenAPILoader parser = OpenAPILoader.create(vertx, new OpenAPILoaderOptions());

    parser.loadOpenAPI("yaml/valid/simple_spec.yaml").setHandler(testContext.succeeding(container -> {
      testContext.verify(() -> {
        assertThat(container)
            .extracting(JsonPointer.from("/info/title"))
            .isEqualTo("Simple spec no $refs");

        assertThat(container)
            .extracting(JsonPointer.create()
                .append("paths")
                .append("/simple")
                .append("post")
                .append("requestBody")
                .append("content")
                .append("multipart/form-data")
                .append("encoding")
                .append("fileName")
                .append("contentType")
            )
            .isEqualTo("text/plain");

        assertThat(container)
            .extracting(JsonPointer.create()
                .append("paths")
                .append("/simple")
                .append("post")
                .append("responses")
                .append("default")
                .append("description")
            )
            .isEqualTo("unexpected error");
      });
      testContext.completeNow();
    }));
  }

  @Test
  public void loadInvalidFromFileNoRef(Vertx vertx, VertxTestContext testContext) {
    OpenAPILoader parser = OpenAPILoader.create(vertx, new OpenAPILoaderOptions());

    parser.loadOpenAPI("yaml/invalid/simple_spec.yaml").setHandler(testContext.failing(err -> {
      testContext.verify(() -> {
        assertThat(err).isInstanceOf(ValidationException.class);
      });
      testContext.completeNow();
    }));
  }

  @Test
  public void loadFromFile(Vertx vertx, VertxTestContext testContext) {
    OpenAPILoader parser = OpenAPILoader.create(vertx, new OpenAPILoaderOptions());

    parser.loadOpenAPI("yaml/valid/inner_refs.yaml").setHandler(testContext.succeeding(openapi -> {
      testContext.verify(() -> {
        assertThat(openapi)
            .extracting(JsonPointer.create()
                .append("paths")
                .append("/simple")
                .append("post")
                .append("operationId")
            )
            .isEqualTo("simple");


        assertThat(openapi)
            .extracting(JsonPointer.create()
                .append("paths")
                .append("/simple")
                .append("post")
                .append("requestBody")
                .append("content")
                .append("multipart/form-data")
                .append("schema")
                .append("$ref")
            )
            .isEqualTo("yaml/valid/inner_refs.yaml#/components/schemas/Simple")
            .satisfies(ref ->
                assertThat(parser)
                    .hasCached(URI.create((String)ref))
                    .extracting(JsonPointer.create().append("properties").append("fileName").append("type"))
                    .isEqualTo("string")
            );

        assertThat(parser)
            .hasCached(URI.create("#/components/schemas/Simple"))
            .extracting(JsonPointer.create().append("properties").append("fileName").append("type"))
            .isEqualTo("string");

        assertThat(parser)
            .extractingWithRefSolveFrom(openapi, JsonPointer.create()
                .append("paths")
                .append("/simple")
                .append("post")
                .append("requestBody")
                .append("content")
                .append("multipart/form-data")
                .append("schema")
                .append("properties")
                .append("fileName")
                .append("type")
            ).isEqualTo("string");
      });
      testContext.completeNow();
    }));
  }

  @Test
  public void loadInvalidFromFile(Vertx vertx, VertxTestContext testContext) {
    OpenAPILoader parser = OpenAPILoader.create(vertx, new OpenAPILoaderOptions());

    parser.loadOpenAPI("yaml/invalid/inner_refs.yaml").setHandler(testContext.failing(err -> {
      testContext.verify(() -> {
        assertThat(err)
            .isInstanceOf(ValidationException.class);
      });
      testContext.completeNow();
    }));
  }

  @Test
  public void loadFromFileLocalRelativeRef(Vertx vertx, VertxTestContext testContext) {
    OpenAPILoader loader = OpenAPILoader.create(vertx, new OpenAPILoaderOptions());

    loader.loadOpenAPI("yaml/valid/local_refs.yaml").setHandler(testContext.succeeding(openapi -> {
      testContext.verify(() -> {
        assertThat(openapi)
            .extracting(JsonPointer.create()
                .append("paths")
                .append("/simple")
                .append("post")
                .append("operationId")
            )
            .isEqualTo("simple");


        assertThat(openapi)
            .extracting(JsonPointer.create()
                .append("paths")
                .append("/simple")
                .append("post")
                .append("requestBody")
                .append("content")
                .append("multipart/form-data")
                .append("schema")
                .append("$ref")
            )
            .isEqualTo("yaml/valid/local_refs.yaml#/components/schemas/Simple");

        assertThat(loader)
            .hasCached(URI.create("yaml/valid/refs/Simple.yaml"));

        assertThat(loader)
            .hasCached(URI.create("yaml/valid/refs/fileName.json"));

        assertThat(loader)
            .extractingWithRefSolve(JsonPointer.create()
                .append("paths")
                .append("/simple")
                .append("post")
                .append("requestBody")
                .append("content")
                .append("multipart/form-data")
                .append("schema")
                .append("properties")
                .append("fileName")
                .append("type")
            ).isEqualTo("string");
      });
      testContext.completeNow();
    }));
  }

  @Test
  public void loadInvalidFromFileLocalRelativeRef(Vertx vertx, VertxTestContext testContext) {
    OpenAPILoader loader = OpenAPILoader.create(vertx, new OpenAPILoaderOptions());

    loader.loadOpenAPI("yaml/invalid/local_refs.yaml").setHandler(testContext.failing(err -> {
      testContext.verify(() -> {
        assertThat(err)
            .isInstanceOf(ValidationException.class);
      });
      testContext.completeNow();
    }));
  }

  @Test
  public void debtsManagerTest(Vertx vertx, VertxTestContext testContext) {
    OpenAPILoader loader = OpenAPILoader.create(vertx, new OpenAPILoaderOptions());

    loader.loadOpenAPI("json/valid/debts_manager_api.json").setHandler(testContext.succeeding(openapi -> {
      testContext.verify(() -> {
        assertThat(loader)
            .extractingWithRefSolveFrom(openapi, JsonPointer.create()
                .append("paths")
                .append("/transactions")
                .append("get")
                .append("responses")
                .append("200")
                .append("content")
                .append("application/json")
                .append("schema")
                .append("items")
                .append("allOf")
                .append("0")
                .append("allOf")
                .append("0")
                .append("properties")
                .append("to")
                .append("minLength")
            ).isEqualTo(5);
      });
      testContext.completeNow();
    }));
  }

  @Test
  public void debtsManagerFailureTest(Vertx vertx, VertxTestContext testContext) {
    OpenAPILoader loader = OpenAPILoader.create(vertx, new OpenAPILoaderOptions());

    loader.loadOpenAPI("json/invalid/debts_manager_api.json").setHandler(testContext.failing(err -> {
      testContext.verify(() -> {
        assertThat(err)
            .isInstanceOf(ValidationException.class);
      });
      testContext.completeNow();
    }));
  }

  @Test
  public void loadFromFileLocalCircularRef(Vertx vertx, VertxTestContext testContext) {
    OpenAPILoader loader = OpenAPILoader.create(vertx, new OpenAPILoaderOptions());

    loader.loadOpenAPI("yaml/valid/local_circular_refs.yaml").setHandler(testContext.succeeding(openapi -> {
      testContext.verify(() -> {
        assertThat(loader)
            .hasCached(URI.create("yaml/valid/refs/Circular.yaml"));

        assertThat(loader)
            .extractingWithRefSolve(JsonPointer.create()
                .append("paths")
                .append("/simple")
                .append("post")
                .append("requestBody")
                .append("content")
                .append("application/json")
                .append("schema")
                .append("properties")
                .append("parent")
                .append("properties")
                .append("childs")
                .append("items")
                .append("properties")
                .append("value")
                .append("type")
            ).isEqualTo("string");
      });
      testContext.completeNow();
    }));
  }

  @Test
  public void loadRemoteWithoutAuth(Vertx vertx, VertxTestContext testContext) {
    remoteCircularTest(vertx, testContext, new OpenAPILoaderOptions(), Collections.emptyList());
  }

  @Test
  public void loadRemoteInvalid(Vertx vertx, VertxTestContext testContext) {
    OpenAPILoader loader = OpenAPILoader.create(vertx, new OpenAPILoaderOptions());
    testContext.assertFailure(
        startSchemaServer(vertx, "src/test/resources/yaml/invalid", Collections.emptyList())
            .compose(v -> loader.loadOpenAPI("http://localhost:9000/local_refs.yaml"))
    ).setHandler(ar -> {
      testContext.verify(() -> {
        assertThat(ar.cause())
            .isInstanceOf(ValidationException.class);
      });
      testContext.completeNow();
    });
  }

  @Test
  public void loadRemoteWithQueryAuth(Vertx vertx, VertxTestContext testContext) {
    remoteCircularTest(
        vertx,
        testContext,
        new OpenAPILoaderOptions().putAuthQueryParam("francesco", "slinky"),
        Collections.singletonList(queryParamAuthMock)
    );
  }

  @Test
  public void loadRemoteWithHeaderAuth(Vertx vertx, VertxTestContext testContext) {
    remoteCircularTest(
        vertx,
        testContext,
        new OpenAPILoaderOptions().putAuthHeader("Authorization", "Basic ZnJhbmNlc2NvOnNsaW5reQ=="),
        Collections.singletonList(headerAuthMock)
    );
  }

  @Test
  public void loadRemoteWithBothAuth(Vertx vertx, VertxTestContext testContext) {
    remoteCircularTest(
        vertx,
        testContext,
        new OpenAPILoaderOptions()
            .putAuthQueryParam("francesco", "slinky")
            .putAuthHeader("Authorization", "Basic ZnJhbmNlc2NvOnNsaW5reQ=="),
        Arrays.asList(headerAuthMock, queryParamAuthMock)
    );
  }

  private void remoteCircularTest(Vertx vertx, VertxTestContext testContext, OpenAPILoaderOptions options, List<Handler<RoutingContext>> authHandlers) {
    OpenAPILoader loader = OpenAPILoader.create(vertx, options);
    testContext.assertComplete(
        startSchemaServer(vertx, "src/test/resources/yaml/valid", authHandlers)
            .compose(v -> loader.loadOpenAPI("http://localhost:9000/local_circular_refs.yaml"))
    ).setHandler(ar -> {
      testContext.verify(() -> {
        assertThat(loader)
            .hasCached(URI.create("http://localhost:9000/local_circular_refs.yaml"));

        assertThat(loader)
            .hasCached(URI.create("http://localhost:9000/refs/Circular.yaml"));

        assertThat(loader)
            .extractingWithRefSolve(JsonPointer.create()
                .append("paths")
                .append("/simple")
                .append("post")
                .append("requestBody")
                .append("content")
                .append("application/json")
                .append("schema")
                .append("properties")
                .append("parent")
                .append("properties")
                .append("childs")
                .append("items")
                .append("properties")
                .append("value")
                .append("type")
            ).isEqualTo("string");
      });
      testContext.completeNow();
    });
  }

  private Future<Void> startSchemaServer(Vertx vertx, String path, List<Handler<RoutingContext>> authHandlers) {
    Router r = Router.router(vertx);

    Route route = r.route("/*")
        .produces("application/yaml");
    authHandlers.forEach(route::handler);
    route.handler(StaticHandler.create(path).setCachingEnabled(true));

    Future<Void> fut = Future.future();
    schemaServer = vertx.createHttpServer(new HttpServerOptions().setPort(9000))
        .requestHandler(r)
        .listen(l -> fut.complete());
    return fut;
  }

  private void stopSchemaServer(Handler<AsyncResult<Void>> completion) {
    if (schemaServer != null) {
      try {
        schemaServer.close((asyncResult) -> {
          completion.handle(Future.succeededFuture());
        });
      } catch (IllegalStateException e) { // Server is already open
        completion.handle(Future.succeededFuture());
      }
    } else
      completion.handle(Future.succeededFuture());
  }

}
