package io.vertx.ext.web.openapi.asserts;

import io.vertx.core.json.pointer.JsonPointer;
import org.assertj.core.api.AbstractAssert;

import java.net.URI;

public class JsonAssert extends AbstractAssert<JsonAssert, Object> {
  public JsonAssert(Object actual) {
    super(actual, JsonAssert.class);
  }

  public JsonAssert extracting(JsonPointer pointer) {
    return new JsonAssert(pointer.queryJson(actual));
  }

  public JsonAssert extracting(URI unparsedPointer) {
    return extracting(JsonPointer.fromURI(unparsedPointer));
  }

}
