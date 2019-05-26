package io.vertx.ext.web.openapi.impl;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.web.validation.ParameterLocation;
import io.vertx.ext.web.validation.ParameterProcessor;
import io.vertx.ext.web.validation.impl.ExplodedArrayValueParameterParser;
import io.vertx.ext.web.validation.impl.ParameterProcessorImpl;
import io.vertx.ext.web.validation.impl.ValueParserInferenceUtils;

public class ExplodedArrayParameterProcessorGenerator implements ParameterProcessorGenerator {

  @Override
  public boolean canGenerate(JsonObject parameter, JsonObject fakeSchema, ParameterLocation parsedLocation, String parsedStyle) {
    return OpenApi3Utils.isSchemaArray(fakeSchema) &&
      OpenApi3Utils.resolveExplode(parameter) &&
      ("form".equals(parsedStyle) ||
        ("label".equals(parsedStyle) && !parsedLocation.equals(ParameterLocation.PATH)) ||
        ("simple".equals(parsedStyle) && !parsedLocation.equals(ParameterLocation.PATH) && !parsedLocation.equals(ParameterLocation.HEADER))
      );
  }

  @Override
  public ParameterProcessor generate(JsonObject parameter, JsonObject fakeSchema, JsonPointer parameterPointer, ParameterLocation parsedLocation, String parsedStyle, GeneratorContext context) {
    SchemaHolder schemas = context.getSchemaHolder(
      parameter.getJsonObject("schema", new JsonObject()),
      fakeSchema,
      parameterPointer.copy().append("schema")
    );

    return new ParameterProcessorImpl(
      parameter.getString("name"),
      parsedLocation,
      !parameter.getBoolean("required", false),
      new ExplodedArrayValueParameterParser(
        ValueParserInferenceUtils.infeerItemsParserForArraySchema(schemas.getFakeSchema()),
        parameter.getString("name")
      ),
      schemas.getValidator()
    );
  }
}
