package io.vertx.ext.web.openapi.impl;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.json.schema.SchemaParser;
import io.vertx.ext.json.schema.SchemaRouter;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.BodyProcessorGenerator;
import io.vertx.ext.web.openapi.OpenAPIHolder;
import io.vertx.ext.web.openapi.ParameterProcessorGenerator;
import io.vertx.ext.web.openapi.RouterFactoryException;
import io.vertx.ext.web.validation.*;
import io.vertx.ext.web.validation.impl.ValidationHandlerImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

//TODO this is where the inference logic should move
public class OpenAPI3ValidationHandlerGenerator {

  private static final JsonPointer BODY_REQUIRED_POINTER = JsonPointer.from("/requestBody/required");
  private static final JsonPointer REQUEST_BODY_CONTENT_POINTER = JsonPointer.from("/requestBody/content");

  private final OpenAPIHolder holder;
  private final SchemaRouter schemaRouter;
  private final SchemaParser schemaParser;
  private final List<ParameterProcessorGenerator> parameterProcessorGenerators;
  private final List<BodyProcessorGenerator> bodyProcessorGenerators;

  public OpenAPI3ValidationHandlerGenerator(OpenAPIHolder holder, SchemaRouter schemaRouter, SchemaParser schemaParser) {
    this.holder = holder;
    this.schemaRouter = schemaRouter;
    this.schemaParser = schemaParser;
    this.parameterProcessorGenerators = new ArrayList<>();
    this.bodyProcessorGenerators = new ArrayList<>();
  }

  public OpenAPI3ValidationHandlerGenerator addParameterProcessorGenerator(ParameterProcessorGenerator gen) {
    parameterProcessorGenerators.add(gen);
    return this;
  }

  public OpenAPI3ValidationHandlerGenerator addBodyProcessorGenerator(BodyProcessorGenerator gen) {
    bodyProcessorGenerators.add(gen);
    return this;
  }

  public ValidationHandlerImpl create(OperationImpl operation) {
    //TODO error handling of this function?
    Map<ParameterLocation, List<ParameterProcessor>> parameterProcessors = new HashMap<>();
    List<BodyProcessor> bodyProcessors = new ArrayList<>();
    List<Function<RoutingContext, RequestPredicateResult>> predicates = new ArrayList<>();

    // Parse parameter processors
    for (Map.Entry<JsonPointer, JsonObject> pe : operation.getParameters().entrySet()) {
      ParameterLocation parsedLocation = ParameterLocation.valueOf(pe.getValue().getString("in").toLowerCase());
      String parsedStyle = OpenApi3Utils.resolveStyle(pe.getValue());

      if (pe.getValue().getBoolean("allowReserved", false))
        throw RouterFactoryException
          .createUnsupportedSpecFeature("You are using allowReserved keyword in parameter " + pe.getKey() + " which is not supported");

      ParameterProcessor generated = parameterProcessorGenerators.stream()
        .filter(g -> g.canGenerate(pe.getValue(), parsedLocation, parsedStyle))
        .findFirst().orElseThrow(() -> RouterFactoryException.cannotFindParameterProcessorGenerator(pe.getKey(), pe.getValue()))
        .generate(pe.getValue(), pe.getKey(), parsedLocation, parsedStyle, operation);
      if (!parameterProcessors.containsKey(generated.getLocation()))
        parameterProcessors.put(generated.getLocation(), new ArrayList<>());
      parameterProcessors.get(generated.getLocation()).add(generated);
    }

    // Parse body required predicate
    if (parseIsBodyRequired(operation)) predicates.add(RequestPredicate.BODY_REQUIRED);

    // Parse body processors
    for (Map.Entry<String, Object> mediaType : (JsonObject) REQUEST_BODY_CONTENT_POINTER.queryJsonOrDefault(operation.getOperationModel(), new JsonObject())) {
      JsonObject mediaTypeModel = (JsonObject) mediaType.getValue();
      JsonPointer mediaTypePointer = operation.getPointer().copy().append("requestBody").append("content").append(mediaType.getKey());
      BodyProcessor generated = bodyProcessorGenerators.stream()
        .filter(g -> g.canGenerate(mediaType.getKey(), mediaTypeModel))
        .findFirst().orElseThrow(() -> RouterFactoryException.createBodyNotSupported(mediaTypePointer))
        .generate(mediaType.getKey(), mediaTypeModel, mediaTypePointer, operation, predicates);
      bodyProcessors.add(generated);
    }

    return new ValidationHandlerImpl(parameterProcessors, bodyProcessors, predicates);
  }

  private boolean parseIsBodyRequired(OperationImpl operation) {
    return (boolean) BODY_REQUIRED_POINTER.queryJsonOrDefault(operation.getOperationModel(), false);
  }


}
