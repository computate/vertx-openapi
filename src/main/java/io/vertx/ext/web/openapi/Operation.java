package io.vertx.ext.web.openapi;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

@VertxGen
public interface Operation {

  @Fluent Operation handler(Handler<RoutingContext> handler);

  @Fluent Operation failureHandler(Handler<RoutingContext> handler);

  @Fluent
  Operation routeToEventBus(String address);

  @Fluent
  Operation routeToEventBus(String address, DeliveryOptions options);

  String getOperationId();

  JsonObject getOperationModel();

  HttpMethod getHttpMethod();

  String getOpenAPIPath();

}
