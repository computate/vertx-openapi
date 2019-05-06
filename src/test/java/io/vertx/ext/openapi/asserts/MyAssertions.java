package io.vertx.ext.openapi.asserts;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.openapi.OpenAPILoader;

public class MyAssertions {

  public static JsonAssert assertThat(JsonObject actual) { return new JsonAssert(actual); }

  public static JsonAssert assertThat(JsonArray actual) { return new JsonAssert(actual); }

  public static OpenAPILoaderAssert assertThat(OpenAPILoader actual) { return new OpenAPILoaderAssert(actual); }

  public static JsonAssert assertThatJson(Object actual) { return new JsonAssert(actual); }

}
