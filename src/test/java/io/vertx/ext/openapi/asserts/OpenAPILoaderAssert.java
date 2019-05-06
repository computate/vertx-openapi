package io.vertx.ext.openapi.asserts;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.openapi.JsonPointerIteratorWithLoader;
import io.vertx.ext.openapi.OpenAPILoader;
import org.assertj.core.api.AbstractAssert;

import java.net.URI;

public class OpenAPILoaderAssert extends AbstractAssert<OpenAPILoaderAssert, OpenAPILoader> {
  public OpenAPILoaderAssert(OpenAPILoader actual) {
    super(actual, OpenAPILoaderAssert.class);
  }

  public JsonAssert hasCached(JsonPointer pointer) {
    return new JsonAssert(actual.getCached(pointer))
        .isNotNull();
  }

  public JsonAssert hasCached(URI uri) {
    return hasCached(JsonPointer.fromURI(uri));
  }

  public JsonAssert extractingWithRefSolveFrom(JsonObject extractionRoot, JsonPointer pointer) {
    return new JsonAssert(pointer.query(extractionRoot, new JsonPointerIteratorWithLoader(actual)));
  }

  public JsonAssert extractingWithRefSolve(JsonPointer pointer) {
    return new JsonAssert(pointer.query(actual.getOpenAPI(), new JsonPointerIteratorWithLoader(actual)));
  }

}
