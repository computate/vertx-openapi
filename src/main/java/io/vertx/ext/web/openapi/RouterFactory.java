package io.vertx.ext.web.openapi;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.json.schema.ValidationException;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.openapi.impl.OpenAPILoaderImpl;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

//TODO all methods docs

/**
 * Interface for OpenAPI3RouterFactory. <br/>
 * To add an handler, use {@link RouterFactory#addHandlerByOperationId(String, Handler)}<br/>
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
 *   <li>Body handler (Customizable with {@link this#bodyHandler(BodyHandler)}</li>
 *   <li>Custom global handlers configurable with {@link this#rootHandler(Handler)}</li>
 *   <li>Global security handlers defined in upper spec level</li>
 *   <li>Operation specific security handlers</li>
 *   <li>Generated validation handler</li>
 *   <li>User handlers or "Not implemented" handler</li>
 * </ol>
 *
 * @author Francesco Guardiani @slinkydeveloper
 */
@VertxGen
public interface RouterFactory {

  @Fluent
  Operation operation(String operationId);

  @Fluent
  List<Operation> operations();

  /**
   * Supply your own BodyHandler if you would like to control body limit, uploads directory and deletion of uploaded files
   * @param bodyHandler
   * @return self
   */
  @Fluent
  RouterFactory bodyHandler(BodyHandler bodyHandler);

  /**
   * Add global handler to be applied prior to {@link Router} being generated. <br/>
   * Please note that you should not add a body handler inside that list. If you want to modify the body handler, please use {@link RouterFactory#bodyHandler(BodyHandler)}
   *
   * @param globalHandler
   * @return this object
   */
  @Fluent
  RouterFactory rootHandler(Handler<RoutingContext> globalHandler);

  /**
   * Mount to paths that have to follow a security schema a security handler
   *
   * @param securitySchemaName
   * @param handler
   * @return
   */
  @Fluent
  RouterFactory securityHandler(String securitySchemaName, Handler<RoutingContext> handler);

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
  RouterFactory securityHandler(String securitySchemaName, String scopeName, Handler<RoutingContext> handler);

  /**
   * Introspect the OpenAPI spec to mount handlers for all operations that specifies a x-vertx-event-bus annotation. Please give a look at <a href="https://vertx.io/docs/vertx-web-api-service/java/">vertx-web-api-service documentation</a> for more informations
   *
   * @return this factory
   */
  @Fluent
  RouterFactory mountServicesFromExtensions();

  /**
   * Introspect the Web Api Service interface to route to service all matching method names with operation ids. Please give a look at <a href="https://vertx.io/docs/vertx-web-api-service/java/">vertx-web-api-service documentation</a> for more informations
   *
   * @return this factory
   */
  @Fluent
  @GenIgnore
  RouterFactory mountServiceInterface(Class interfaceClass, String address);

  /**
   * Set options of router factory. For more info {@link RouterFactoryOptions}
   *
   * @param options
   * @return
   */
  @Fluent
  RouterFactory setOptions(RouterFactoryOptions options);

  /**
   * Get options of router factory. For more info {@link RouterFactoryOptions}
   *
   * @return
   */
  RouterFactoryOptions getOptions();

  JsonObject getOpenAPI();

  /**
   * When set, this function is called while creating the payload of {@link io.vertx.ext.web.api.OperationRequest}
   * @param serviceExtraPayloadMapper
   * @return
   */
  @Fluent
  RouterFactory serviceExtraPayloadMapper(Function<RoutingContext, JsonObject> serviceExtraPayloadMapper);

  /**
   * Construct a new router based on spec. It will fail if you are trying to mount a spec with security schemes
   * without assigned handlers<br/>
   * <b>Note:</b> Router is constructed in this function, so it will be respected the path definition ordering.
   *
   * @return
   */
  Router createRouter();

  /**
   * Create a new OpenAPI3RouterFactory
   *
   * @param vertx
   * @param url location of your spec. It can be an absolute path, a local path or remote url (with HTTP protocol)
   * @param handler  When specification is loaded, this handler will be called with AsyncResult<OpenAPI3RouterFactory>
   */
  static void create(Vertx vertx, String url, Handler<AsyncResult<RouterFactory>> handler) {
    create(vertx, url, new OpenAPILoaderOptions(), handler);
  }

  /**
   * Create a new OpenAPI3RouterFactory
   *
   * @param vertx
   * @param url location of your spec. It can be an absolute path, a local path or remote url (with HTTP protocol)
   * @param handler  When specification is loaded, this handler will be called with AsyncResult<OpenAPI3RouterFactory>
   */
  static void create(Vertx vertx,
                     String url,
                     OpenAPILoaderOptions options,
                     Handler<AsyncResult<RouterFactory>> handler) {
    OpenAPILoader loader = new OpenAPILoaderImpl(vertx.createHttpClient(), vertx.fileSystem(), options);
    loader.loadOpenAPI(url).setHandler(ar -> {
      if (ar.failed()) {
        if (ar.cause() instanceof ValidationException) {
          handler.handle(Future.failedFuture(RouterFactoryException.createInvalidSpecException(ar.cause())));
        } else {
          handler.handle(Future.failedFuture(RouterFactoryException.createInvalidFileSpec(url, ar.cause())));
        }
      } else {
        //TODO instantiate routerfactoryimpl
      }
    });
  }
}
