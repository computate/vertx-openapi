package io.vertx.ext.web.openapi.impl;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.json.schema.openapi3.OpenAPI3SchemaParser;
import io.vertx.ext.web.openapi.Operation;
import io.vertx.ext.web.openapi.ParameterProcessorGenerator;
import io.vertx.ext.web.validation.ParameterLocation;
import io.vertx.ext.web.validation.ParameterProcessor;
import io.vertx.ext.web.validation.impl.*;

public class ExplodedObjectParameterProcessorGenerator implements ParameterProcessorGenerator {

  private final OpenAPI3SchemaParser schemaParser;

  public ExplodedObjectParameterProcessorGenerator(OpenAPI3SchemaParser schemaParser) {
    this.schemaParser = schemaParser;
  }

  @Override
  public boolean canGenerate(JsonObject parameter, ParameterLocation parsedLocation, String parsedStyle) {
    return OpenApi3Utils.isSchemaObjectOrCombinators(parameter.getJsonObject("schema", new JsonObject())) &&
      OpenApi3Utils.resolveExplode(parameter) &&
      ("form".equals(parsedStyle) || "matrix".equals(parsedStyle) || "label".equals(parsedStyle));
  }

  @Override
  public ParameterProcessor generate(JsonObject parameter, JsonPointer parameterPointer, ParameterLocation parsedLocation, String parsedStyle, Operation operation) {
    JsonObject schema = parameter.getJsonObject("schema", new JsonObject());
    JsonObject fakeSchema = OpenApi3Utils.mergeCombinatorsWithOnlyObjectSchemaIfNecessary(schema);
    return new ParameterProcessorImpl(
      parameter.getString("name"),
      parsedLocation,
      !parameter.getBoolean("required", false),
      new ExplodedObjectValueParameterParser(
        ValueParserInferenceUtils.infeerPropertiesParsersForObjectSchema(fakeSchema),
        ValueParserInferenceUtils.infeerPatternPropertiesParsersForObjectSchema(fakeSchema),
        ValueParserInferenceUtils.infeerAdditionalPropertiesParserForObjectSchema(fakeSchema),
        parameter.getString("name")
      ),
      new SchemaValidator(schemaParser.parse(
        parameter.getJsonObject("schema", new JsonObject()),
        parameterPointer.copy().append("schema")
      ))
    );
  }

}
