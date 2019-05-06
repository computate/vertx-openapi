package io.vertx.ext.openapi;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.openapi.impl.OpenAPILoaderImpl;

public interface OpenAPILoader {

  Future<JsonObject> loadOpenAPI(String uri);
  JsonObject getCached(JsonPointer pointer);
  JsonObject solveIfNeeded(JsonObject obj);
  JsonObject getOpenAPI();
  JsonObject getOpenAPIResolved();

  static OpenAPILoader create(Vertx vertx, OpenAPILoaderOptions options) {
    return new OpenAPILoaderImpl(vertx.createHttpClient(), vertx.fileSystem(), options);
  }


}
