package io.vertx.ext.web.openapi;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.openapi.impl.OpenAPI3RouterFactoryAdapter;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Interface for OpenAPI3RouterFactory. <br/>
 * To add an handler, use {@link OpenAPI3RouterFactory#addHandlerByOperationId(String, Handler)}<br/>
 * Usage example:
 * <pre>
 * {@code
 * OpenAPI3RouterFactory.create(vertx, "src/resources/spec.yaml", asyncResult -> {
 *  if (!asyncResult.succeeded()) {
 *     // IO failure or spec invalid
 *  } else {
 *     OpenAPI3RouterFactory routerFactory = asyncResult.result();
 *     routerFactory.addHandlerByOperationId("operation_id", routingContext -> {
 *        // Do something
 *     }, routingContext -> {
 *        // Do something with failure handler
 *     });
 *     Router router = routerFactory.getRouter();
 *  }
 * });
 * }
 * </pre>
 * <br/>
 * Handlers are loaded in this order:<br/>
 *  <ol>
 *   <li>Body handler (Customizable with {@link this#setBodyHandler(BodyHandler)}</li>
 *   <li>Custom global handlers configurable with {@link this#addGlobalHandler(Handler)}</li>
 *   <li>Global security handlers defined in upper spec level</li>
 *   <li>Operation specific security handlers</li>
 *   <li>Generated validation handler</li>
 *   <li>User handlers or "Not implemented" handler</li>
 * </ol>
 *
 * @author Francesco Guardiani @slinkydeveloper
 */
@VertxGen
@Deprecated
public interface OpenAPI3RouterFactory {

  /**
   * Mount to paths that have to follow a security schema a security handler
   *
   * @param securitySchemaName
   * @param handler
   * @return
   */
  @Fluent
  OpenAPI3RouterFactory addSecurityHandler(String securitySchemaName, Handler<RoutingContext> handler);

  /**
   * Set options of router factory. For more info {@link RouterFactoryOptions}
   *
   * @param options
   * @return
   */
  @Fluent
  OpenAPI3RouterFactory setOptions(RouterFactoryOptions options);

  /**
   * Get options of router factory. For more info {@link RouterFactoryOptions}
   *
   * @return
   */
  RouterFactoryOptions getOptions();

  /**
   * Construct a new router based on spec. It will fail if you are trying to mount a spec with security schemes
   * without assigned handlers<br/>
   * <b>Note:</b> Router is constructed in this function, so it will be respected the path definition ordering.
   *
   * @return
   */
  Router getRouter();

  /**
   * Supply your own BodyHandler if you would like to control body limit, uploads directory and deletion of uploaded files
   * @param bodyHandler
   * @return self
   */
  @Fluent
  OpenAPI3RouterFactory setBodyHandler(BodyHandler bodyHandler);

  /**
   * Add global handler to be applied prior to {@link io.vertx.ext.web.Router} being generated. <br/>
   * Please note that you should not add a body handler inside that list. If you want to modify the body handler, please use {@link RouterFactory#setBodyHandler(BodyHandler)}
   *
   * @param globalHandler
   * @return this object
   */
  @Fluent
  OpenAPI3RouterFactory addGlobalHandler(Handler<RoutingContext> globalHandler);

  /**
   * When set, this function is called while creating the payload of {@link io.vertx.ext.web.api.OperationRequest}
   * @param extraOperationContextPayloadMapper
   * @return
   */
  @Fluent
  OpenAPI3RouterFactory setExtraOperationContextPayloadMapper(Function<RoutingContext, JsonObject> extraOperationContextPayloadMapper);

  /**
   * Add a particular scope validator. The main security schema will not be called if a specific scope validator is
   * configured
   *
   * @param securitySchemaName
   * @param scopeName
   * @param handler
   * @return this factory
   */
  @Fluent
  OpenAPI3RouterFactory addSecuritySchemaScopeValidator(String securitySchemaName, String scopeName, Handler<RoutingContext> handler);

  /**
   * Add an handler by operation_id field in Operation object
   *
   * @param operationId
   * @param handler
   * @return this factory
   */
  @Fluent
  OpenAPI3RouterFactory addHandlerByOperationId(String operationId, Handler<RoutingContext> handler); //TODO

  /**
   * Add a failure handler by operation_id field in Operation object
   *
   * @param operationId
   * @param failureHandler
   * @return this factory
   */
  @Fluent
  OpenAPI3RouterFactory addFailureHandlerByOperationId(String operationId, Handler<RoutingContext> failureHandler); //TODO

  /**
   * Specify to route an incoming request for specified operation id to a Web Api Service mounted at the specified address on event bus. Please give a look at <a href="https://vertx.io/docs/vertx-web-api-service/java/">vertx-web-api-service documentation</a> for more informations
   *
   * @param operationId
   * @param address
   * @return this factory
   */
  @Fluent
  OpenAPI3RouterFactory mountOperationToEventBus(String operationId, String address); //TODO

  /**
   * Specify to route an incoming request for all operations that contains the specified tag to a Web Api Service mounted at the specified address on event bus.
   * The request is handled by the method that matches the operation id. Please give a look at <a href="https://vertx.io/docs/vertx-web-api-service/java/">vertx-web-api-service documentation</a> for more informations
   *
   * @param tag
   * @param address
   * @return this factory
   */
  @Fluent
  OpenAPI3RouterFactory mountServiceFromTag(String tag, String address);

  /**
   * Introspect the OpenAPI spec to mount handlers for all operations that specifies a x-vertx-event-bus annotation. Please give a look at <a href="https://vertx.io/docs/vertx-web-api-service/java/">vertx-web-api-service documentation</a> for more informations
   *
   * @return this factory
   */
  @Fluent
  OpenAPI3RouterFactory mountServicesFromExtensions();

  /**
   * Introspect the Web Api Service interface to route to service all matching method names with operation ids. Please give a look at <a href="https://vertx.io/docs/vertx-web-api-service/java/">vertx-web-api-service documentation</a> for more informations
   *
   * @return this factory
   */
  @Fluent
  @GenIgnore
  OpenAPI3RouterFactory mountServiceInterface(Class interfaceClass, String address);

  /**
   * Create a new OpenAPI3RouterFactory
   *
   * @param vertx
   * @param url location of your spec. It can be an absolute path, a local path or remote url (with HTTP protocol)
   * @param handler  When specification is loaded, this handler will be called with AsyncResult<OpenAPI3RouterFactory>
   */
  static void create(Vertx vertx, String url, Handler<AsyncResult<OpenAPI3RouterFactory>> handler) {
    create(vertx, url, new OpenAPILoaderOptions(), handler);
  }

  /**
   * Create a new OpenAPI3RouterFactory
   *
   * @param vertx
   * @param url location of your spec. It can be an absolute path, a local path or remote url (with HTTP protocol)
   * @param options loader options
   * @param handler  When specification is loaded, this handler will be called with AsyncResult<OpenAPI3RouterFactory>
   */
  static void create(Vertx vertx,
                     String url,
                     OpenAPILoaderOptions options,
                     Handler<AsyncResult<OpenAPI3RouterFactory>> handler) {
    RouterFactory.create(vertx, url, options, ar -> {
      if (ar.failed()) handler.handle(Future.failedFuture(ar.cause()));
      else handler.handle(Future.succeededFuture(new OpenAPI3RouterFactoryAdapter(ar.result())));
    });
  }
}
