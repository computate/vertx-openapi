package io.vertx.ext.web.openapi;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public interface Operation {

  @Fluent Operation handler(Handler<RoutingContext> handler);

  @Fluent Operation failureHandler(Handler<RoutingContext> handler);

  @Fluent
  Operation routeToEventBus(String address);

  String getOperationId();

  JsonObject getOperationModel();

  HttpMethod getHttpMethod();

  String getOpenAPIPath();

}
