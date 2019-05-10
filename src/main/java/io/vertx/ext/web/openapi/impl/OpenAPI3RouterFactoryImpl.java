package io.vertx.ext.web.openapi.impl;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.ResponseContentTypeHandler;
import io.vertx.ext.web.impl.RouteImpl;
import io.vertx.ext.web.openapi.OpenAPIHolder;
import io.vertx.ext.web.openapi.Operation;
import io.vertx.ext.web.openapi.RouterFactory;
import io.vertx.ext.web.openapi.RouterFactoryOptions;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Francesco Guardiani @slinkydeveloper
 */
public class OpenAPI3RouterFactoryImpl implements RouterFactory {

  private final static String OPENAPI_EXTENSION = "x-vertx-event-bus";
  private final static String OPENAPI_EXTENSION_ADDRESS = "address";
  private final static String OPENAPI_EXTENSION_METHOD_NAME = "method";

  private final static Handler<RoutingContext> NOT_IMPLEMENTED_HANDLER = rc -> rc.fail(501);

  private static Handler<RoutingContext> generateNotAllowedHandler(List<HttpMethod> allowedMethods) {
    return rc -> {
      rc.addHeadersEndHandler(v ->
          rc.response().headers().add("Allow", Strings.join(", ",
            allowedMethods.stream().map(HttpMethod::toString).collect(Collectors.toList())
          ))
        );
      rc.fail(405);
    };
  }

  private Vertx vertx;
  private OpenAPIHolder openapi;
  private RouterFactoryOptions options;
  private Map<String, OperationImpl> operations;
  private BodyHandler bodyHandler;
  private SecurityHandlersStore securityHandlers;
  private List<Handler<RoutingContext>> globalHandlers;
  private Function<RoutingContext, JsonObject> extraOperationContextPayloadMapper;

  public OpenAPI3RouterFactoryImpl(Vertx vertx, OpenAPIHolder spec) {
    this.vertx = vertx;
    this.openapi = spec;
    this.options = new RouterFactoryOptions();
    this.bodyHandler = BodyHandler.create();
    this.globalHandlers = new ArrayList<>();

    this.operations = new LinkedHashMap<>();
    this.securityHandlers = new SecurityHandlersStore();

    /* --- Initialization of all arrays and maps --- */
    for (Map.Entry<String, ? extends PathItem> pathEntry : spec.getPaths().entrySet()) {
      for (Map.Entry<PathItem.HttpMethod, ? extends Operation> opEntry : pathEntry.getValue().readOperationsMap().entrySet()) {
        this.operations.put(opEntry.getValue().getOperationId(), new OperationImpl(
          HttpMethod.valueOf(opEntry.getKey().name()),
          pathEntry.getKey(),
          opEntry.getValue(),
          pathEntry.getValue()
        ));
      }
    }
  }

  @Override
  public RouterFactory setOptions(RouterFactoryOptions options) {
    Objects.requireNonNull(options);
    this.options = options;
    return this;
  }

  @Override
  public RouterFactoryOptions getOptions() {
    return options;
  }

  @Override
  public RouterFactory securityHandler(String securitySchemaName, Handler<RoutingContext> handler) {
    Objects.requireNonNull(securitySchemaName);
    Objects.requireNonNull(handler);
    securityHandlers.addSecurityRequirement(securitySchemaName, handler);
    return this;
  }

  @Override
  public RouterFactory securityHandler(String securitySchemaName, String scopeName, Handler<RoutingContext> handler) {
    Objects.requireNonNull(securitySchemaName);
    Objects.requireNonNull(scopeName);
    Objects.requireNonNull(handler);
    securityHandlers.addSecurityRequirement(securitySchemaName, scopeName, handler);
    return this;
  }

  @Override
  public List<Operation> operations() {
    return this.operations.entrySet().stream().map(Map.Entry::getValue).collect(Collectors.toList());
  }

  @Override
  public Operation operation(String operationId) {
    Objects.requireNonNull(operationId);
    return this.operations.get(operationId);
  }

  @Override
  public RouterFactory bodyHandler(BodyHandler bodyHandler) {
    Objects.requireNonNull(bodyHandler);
    this.bodyHandler = bodyHandler;
    return this;
  }

  @Override
  public OpenAPI3RouterFactory addHandlerByOperationId(String operationId, Handler<RoutingContext> handler) {
    if (handler != null) {
      OperationImpl op = operations.get(operationId);
      if (op == null) throw RouterFactoryException.createOperationIdNotFoundException(operationId);
      op.addUserHandler(handler);
    }
    return this;
  }

  @Override
  public OpenAPI3RouterFactory addFailureHandlerByOperationId(String operationId, Handler<RoutingContext> failureHandler) {
    if (failureHandler != null) {
      OperationImpl op = operations.get(operationId);
      if (op == null) throw RouterFactoryException.createOperationIdNotFoundException(operationId);
      op.addUserFailureHandler(failureHandler);
    }
    return this;
  }

  @Override
  public OpenAPI3RouterFactory mountServiceFromTag(String tag, String address) {
    for (Map.Entry<String, OperationImpl> op : operations.entrySet()) {
      if (op.getValue().hasTag(tag))
        op.getValue().mountRouteToService(address);
    }
    return this;
  }

  @Override
  public OpenAPI3RouterFactory mountServiceInterface(Class interfaceClass, String address) {
    for (Method m : interfaceClass.getMethods()) {
      if (OpenApi3Utils.serviceProxyMethodIsCompatibleHandler(m)) {
        String methodName = m.getName();
        OperationImpl op = Optional
          .ofNullable(this.operations.get(methodName))
          .orElseGet(() ->
            this.operations.entrySet().stream().filter(e -> OpenApi3Utils.sanitizeOperationId(e.getKey()).equals(methodName)).map(Map.Entry::getValue).findFirst().orElseGet(() -> null)
          );
        if (op != null) {
          op.mountRouteToService(address, methodName);
        }
      }
    }
    return this;
  }

  @Override
  public OpenAPI3RouterFactory mountOperationToEventBus(String operationId, String address) {
    OperationImpl op = operations.get(operationId);
    if (op == null) throw RouterFactoryException.createOperationIdNotFoundException(operationId);
    op.mountRouteToService(address, operationId);
    return this;
  }

  @Override
  public OpenAPI3RouterFactory mountServicesFromExtensions() {
    for (Map.Entry<String, OperationImpl> opEntry : operations.entrySet()) {
      OperationImpl operation = opEntry.getValue();
      Object extensionVal = OpenApi3Utils.getAndMergeServiceExtension(OPENAPI_EXTENSION, OPENAPI_EXTENSION_ADDRESS, OPENAPI_EXTENSION_METHOD_NAME, operation.pathModel, operation.operationModel);

      if (extensionVal != null) {
        if (extensionVal instanceof String) {
          operation.mountRouteToService((String) extensionVal, opEntry.getKey());
        } else if (extensionVal instanceof Map) {
          JsonObject extensionMap = new JsonObject((Map<String, Object>) extensionVal);
          String address = extensionMap.getString(OPENAPI_EXTENSION_ADDRESS);
          String methodName = extensionMap.getString(OPENAPI_EXTENSION_METHOD_NAME);
          JsonObject sanitizedMap = OpenApi3Utils.sanitizeDeliveryOptionsExtension(extensionMap);
          if (address == null)
            throw RouterFactoryException.createWrongExtension("Extension " + OPENAPI_EXTENSION + " must define " + OPENAPI_EXTENSION_ADDRESS);
          if (methodName == null)
            operation.mountRouteToService(address, opEntry.getKey());
          else
            operation.mountRouteToService(address, methodName, sanitizedMap);
        } else {
          throw RouterFactoryException.createWrongExtension("Extension " + OPENAPI_EXTENSION + " must be or string or a JsonObject");
        }
      }
    }
    return this;
  }

  @Override
  public Router getRouter() {
    Router router = Router.router(vertx);
    Route globalRoute = router.route();
    globalRoute.handler(this.getBodyHandler());

    List<Handler<RoutingContext>> globalHandlers = this.getGlobalHandlers();
    for (Handler<RoutingContext> globalHandler: globalHandlers) {
      globalRoute.handler(globalHandler);
    }

    List<Handler<RoutingContext>> globalSecurityHandlers = securityHandlers
      .solveSecurityHandlers(spec.getSecurity(), this.getOptions().isRequireSecurityHandlers());
    for (OperationImpl operation : operations.values()) {
      // If user don't want 501 handlers and the operation is not configured, skip it
      if (!options.isMountNotImplementedHandler() && !operation.isConfigured())
        continue;

      List<Handler> handlersToLoad = new ArrayList<>();
      List<Handler> failureHandlersToLoad = new ArrayList<>();

      // Resolve security handlers
      // As https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.1.md#fixed-fields-8 says:
      // Operation specific security requirement overrides global security requirement, even if local security requirement is an empty array
      if (operation.getOperationModel().getSecurity() != null) {
        handlersToLoad.addAll(securityHandlers.solveSecurityHandlers(
          operation.getOperationModel().getSecurity(),
          this.getOptions().isRequireSecurityHandlers()
        ));
      } else {
        handlersToLoad.addAll(globalSecurityHandlers);
      }

      // Generate ValidationHandler
      OpenAPI3RequestValidationHandlerImpl validationHandler = new OpenAPI3RequestValidationHandlerImpl(operation
        .getOperationModel(), operation.getParameters(), this.spec, refsCache);
      handlersToLoad.add(validationHandler);

      // Check if path is set by user
      if (operation.isConfigured()) {
        handlersToLoad.addAll(operation.getUserHandlers());
        failureHandlersToLoad.addAll(operation.getUserFailureHandlers());
        if (operation.mustMountRouteToService()) {
          handlersToLoad.add(
            (operation.getEbServiceDeliveryOptions() != null) ? RouteToEBServiceHandler.build(
              vertx.eventBus(),
              operation.getEbServiceAddress(),
              operation.getEbServiceMethodName(),
              operation.getEbServiceDeliveryOptions(),
              this.getExtraOperationContextPayloadMapper()
            ) : RouteToEBServiceHandler.build(
              vertx.eventBus(),
              operation.getEbServiceAddress(),
              operation.getEbServiceMethodName(),
              this.getExtraOperationContextPayloadMapper()
            )
          );
        }
      } else {
        // Check if not implemented or method not allowed
        List<HttpMethod> configuredMethodsForThisPath = operations
          .values()
          .stream()
          .filter(ov -> operation.path.equals(ov.path))
          .filter(OperationImpl::isConfigured)
          .map(OperationImpl::getMethod)
          .collect(Collectors.toList());

        if (!configuredMethodsForThisPath.isEmpty())
          handlersToLoad.add(generateNotAllowedHandler(configuredMethodsForThisPath));
        else
          handlersToLoad.add(NOT_IMPLEMENTED_HANDLER);
      }

      // Now add all handlers to route
      OpenAPI3PathResolver pathResolver = new OpenAPI3PathResolver(operation.getPath(), operation.getParameters());
      Route route = pathResolver
        .solve() // If this optional is empty, this route doesn't need regex
        .map(solvedRegex -> router.routeWithRegex(operation.getMethod(), solvedRegex.toString()))
        .orElseGet(() -> router.route(operation.getMethod(), operation.getPath()));

      String exposeConfigurationKey = this.getOptions().getOperationModelKey();
      if (exposeConfigurationKey != null)
        route.handler(context -> context.put(exposeConfigurationKey, operation.getOperationModel()).next());

      // Set produces/consumes
      Set<String> consumes = new HashSet<>();
      Set<String> produces = new HashSet<>();
      if (operation.getOperationModel().getRequestBody() != null &&
        operation.getOperationModel().getRequestBody().getContent() != null)
        consumes.addAll(operation.getOperationModel().getRequestBody().getContent().keySet());


      if (operation.getOperationModel().getResponses() != null)
        for (ApiResponse response : operation.getOperationModel().getResponses().values())
          if (response.getContent() != null)
            produces.addAll(response.getContent().keySet());

      for (String ct : consumes)
        route.consumes(ct);

      for (String ct : produces)
        route.produces(ct);

      if (!consumes.isEmpty())
        ((RouteImpl)route).setEmptyBodyPermittedWithConsumes(!validationHandler.isBodyRequired());

      if (options.isMountResponseContentTypeHandler() && produces.size() != 0)
        route.handler(ResponseContentTypeHandler.create());

      route.setRegexGroupsNames(new ArrayList<>(pathResolver.getMappedGroups().values()));
      for (Handler handler : handlersToLoad)
        route.handler(handler);
      for (Handler failureHandler : failureHandlersToLoad)
        route.failureHandler(failureHandler);
    }
    // Check validation failure handler
    if (this.options.isMountValidationFailureHandler()) router.errorHandler(400, this.getValidationFailureHandler());
    if (this.options.isMountNotImplementedHandler()) router.errorHandler(501, this.getNotImplementedFailureHandler());
    return router;
  }

}
