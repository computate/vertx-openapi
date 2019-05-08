package io.vertx.ext.web.openapi.asserts;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.web.openapi.JsonPointerIteratorWithLoader;
import io.vertx.ext.web.openapi.OpenAPILoader;
import org.assertj.core.api.AbstractAssert;

import java.net.URI;

public class OpenAPILoaderAssert extends AbstractAssert<OpenAPILoaderAssert, OpenAPILoader> {

  private final JsonPointerIteratorWithLoader iterator;

  public OpenAPILoaderAssert(OpenAPILoader actual) {
    super(actual, OpenAPILoaderAssert.class);
    iterator = new JsonPointerIteratorWithLoader(actual);
  }

  public JsonAssert hasCached(JsonPointer pointer) {
    return new JsonAssert(actual.getCached(pointer))
        .isNotNull();
  }

  public JsonAssert hasCached(URI uri) {
    return hasCached(JsonPointer.fromURI(uri));
  }

  public JsonAssert extractingWithRefSolveFrom(JsonObject extractionRoot, JsonPointer pointer) {
    return new JsonAssert(pointer.query(extractionRoot, iterator));
  }

  public JsonAssert extractingWithRefSolve(JsonPointer pointer) {
    return new JsonAssert(pointer.query(actual.getOpenAPI(), iterator));
  }

}
