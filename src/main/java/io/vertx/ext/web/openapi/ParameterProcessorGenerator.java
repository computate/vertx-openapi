package io.vertx.ext.web.openapi;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.web.validation.ParameterLocation;
import io.vertx.ext.web.validation.ParameterProcessor;

@VertxGen
public interface ParameterProcessorGenerator {

  boolean canGenerate(JsonObject parameter, ParameterLocation parsedLocation, String parsedStyle);

  ParameterProcessor generate(
    JsonObject parameter,
    JsonPointer parameterPointer,
    ParameterLocation parsedLocation,
    String parsedStyle,
    Operation operation
  );
}
