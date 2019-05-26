package io.vertx.ext.web.openapi;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;

@VertxGen
public interface OpenAPIHolder {

  JsonObject getCached(JsonPointer pointer);
  JsonObject solveIfNeeded(JsonObject obj);
  JsonObject getOpenAPI();

}
