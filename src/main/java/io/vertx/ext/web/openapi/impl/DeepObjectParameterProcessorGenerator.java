package io.vertx.ext.web.openapi.impl;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.json.schema.openapi3.OpenAPI3SchemaParser;
import io.vertx.ext.web.openapi.Operation;
import io.vertx.ext.web.openapi.ParameterProcessorGenerator;
import io.vertx.ext.web.validation.ParameterLocation;
import io.vertx.ext.web.validation.ParameterProcessor;
import io.vertx.ext.web.validation.impl.DeepObjectValueParameterParser;
import io.vertx.ext.web.validation.impl.ParameterProcessorImpl;
import io.vertx.ext.web.validation.impl.SchemaValidator;
import io.vertx.ext.web.validation.impl.ValueParserInferenceUtils;

public class DeepObjectParameterProcessorGenerator implements ParameterProcessorGenerator {

  private final OpenAPI3SchemaParser schemaParser;

  public DeepObjectParameterProcessorGenerator(OpenAPI3SchemaParser schemaParser) {
    this.schemaParser = schemaParser;
  }

  @Override
  public boolean canGenerate(JsonObject parameter, ParameterLocation parsedLocation, String parsedStyle) {
    return parsedStyle.equals("deepObject");
  }

  @Override
  public ParameterProcessor generate(JsonObject parameter, JsonPointer parameterPointer, ParameterLocation parsedLocation, String parsedStyle, Operation operation) {
    JsonObject fakeSchema = OpenApi3Utils.mergeCombinatorsWithOnlyObjectSchemaIfNecessary(parameter.getJsonObject("schema", new JsonObject()));
    return new ParameterProcessorImpl(
      parameter.getString("name"),
      parsedLocation,
      !parameter.getBoolean("required", false),
      new DeepObjectValueParameterParser(
        ValueParserInferenceUtils.infeerPropertiesParsersForObjectSchema(fakeSchema),
        ValueParserInferenceUtils.infeerPatternPropertiesParsersForObjectSchema(fakeSchema),
        ValueParserInferenceUtils.infeerAdditionalPropertiesParserForObjectSchema(fakeSchema),
        parameter.getString("name")
      ),
      new SchemaValidator(schemaParser.parse(parameter.getJsonObject("schema"), parameterPointer.copy().append("schema")))
    );
  }
}
