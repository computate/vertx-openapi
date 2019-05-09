package io.vertx.ext.web.openapi.impl;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.Operation;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class OperationImpl implements Operation {

  private String operationId;
  private HttpMethod method;
  private String path;
  private JsonObject pathModel;
  private JsonObject operationModel;

  private List<JsonObject> parameters;
  private List<String> tags;
  private List<Handler<RoutingContext>> userHandlers;
  private List<Handler<RoutingContext>> userFailureHandlers;

  private String ebServiceAddress;
  private String ebServiceMethodName;
  private JsonObject ebServiceDeliveryOptions;

  protected OperationImpl(String operationId, HttpMethod method, String path, JsonObject operationModel, JsonObject pathModel) {
    this.operationId = operationId;
    this.method = method;
    this.path = path;
    this.pathModel = pathModel;
    this.operationModel = operationModel;
    this.tags = operationModel.getJsonArray("tags", new JsonArray()).stream().map(Object::toString).collect(Collectors.toList());
    // Merge parameters
    List<JsonObject> opParams = operationModel
      .getJsonArray("parameters", new JsonArray())
      .stream()
      .map(j -> (JsonObject)j)
      .collect(Collectors.toList());
    List<JsonObject> parentParams = pathModel
      .getJsonArray("parameters", new JsonArray())
      .stream()
      .map(j -> (JsonObject)j)
      .collect(Collectors.toList());
    this.parameters = OpenApi3Utils.mergeParameters(opParams, parentParams);
    this.userHandlers = new ArrayList<>();
    this.userFailureHandlers = new ArrayList<>();
  }

  @Override
  public Operation handler(Handler<RoutingContext> handler) {
    this.userHandlers.add(handler);
    return this;
  }

  @Override
  public Operation failureHandler(Handler<RoutingContext> handler) {
    this.userFailureHandlers.add(handler);
    return this;
  }

  @Override
  public Operation routeToEventBus(String address) {
    mountRouteToService(address);
    return this;
  }

  @Override
  public String getOperationId() {
    return operationId;
  }

  @Override
  public JsonObject getOperationModel() {
    return operationModel;
  }

  @Override
  public HttpMethod getHttpMethod() {
    return method;
  }

  @Override
  public String getOpenAPIPath() {
    return path;
  }

  protected List<JsonObject> getParameters() {
    return parameters;
  }

  protected JsonObject getPathModel() {
    return pathModel;
  }

  protected List<Handler<RoutingContext>> getUserHandlers() {
    return userHandlers;
  }

  protected List<Handler<RoutingContext>> getUserFailureHandlers() {
    return userFailureHandlers;
  }

  protected boolean isConfigured() {
    return userHandlers.size() != 0 || mustMountRouteToService();
  }

  protected List<String> getTags() {
    return tags;
  }

  protected boolean hasTag(String tag) { return tags != null && tags.contains(tag); }

  protected void mountRouteToService(String address) {
    this.ebServiceAddress = address;
    this.ebServiceMethodName = OpenApi3Utils.sanitizeOperationId(operationId);
  }

  protected void mountRouteToService(String address, String methodName) {
    this.ebServiceAddress = address;
    this.ebServiceMethodName = OpenApi3Utils.sanitizeOperationId(methodName);
  }

  protected void mountRouteToService(String address, String methodName, JsonObject deliveryOptions) {
    this.ebServiceAddress = address;
    this.ebServiceMethodName = OpenApi3Utils.sanitizeOperationId(methodName);
    this.ebServiceDeliveryOptions = deliveryOptions;
  }

  protected boolean mustMountRouteToService() {
    return this.ebServiceAddress != null;
  }

  protected String getEbServiceAddress() {
    return ebServiceAddress;
  }

  protected String getEbServiceMethodName() {
    return ebServiceMethodName;
  }

  protected JsonObject getEbServiceDeliveryOptions() {
    return ebServiceDeliveryOptions;
  }
}
