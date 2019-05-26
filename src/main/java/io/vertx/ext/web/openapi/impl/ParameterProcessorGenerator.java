package io.vertx.ext.web.openapi.impl;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.web.validation.ParameterLocation;
import io.vertx.ext.web.validation.ParameterProcessor;

public interface ParameterProcessorGenerator {

  boolean canGenerate(JsonObject parameter, JsonObject fakeParameterSchema, ParameterLocation parsedLocation, String parsedStyle);

  ParameterProcessor generate(
    JsonObject parameter,
    JsonObject fakeParameterSchema,
    JsonPointer parameterPointer,
    ParameterLocation parsedLocation,
    String parsedStyle,
    GeneratorContext context
  );
}
