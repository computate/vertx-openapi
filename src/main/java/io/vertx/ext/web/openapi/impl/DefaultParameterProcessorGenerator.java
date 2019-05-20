package io.vertx.ext.web.openapi.impl;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.json.schema.openapi3.OpenAPI3SchemaParser;
import io.vertx.ext.web.openapi.Operation;
import io.vertx.ext.web.openapi.ParameterProcessorGenerator;
import io.vertx.ext.web.validation.ParameterLocation;
import io.vertx.ext.web.validation.ParameterProcessor;
import io.vertx.ext.web.validation.ValueParser;
import io.vertx.ext.web.validation.impl.ParameterProcessorImpl;
import io.vertx.ext.web.validation.impl.SchemaValidator;
import io.vertx.ext.web.validation.impl.SingleValueParameterParser;
import io.vertx.ext.web.validation.impl.ValueParserInferenceUtils;

public class DefaultParameterProcessorGenerator implements ParameterProcessorGenerator {

  private final OpenAPI3SchemaParser schemaParser;

  public DefaultParameterProcessorGenerator(OpenAPI3SchemaParser schemaParser) {
    this.schemaParser = schemaParser;
  }

  @Override
  public boolean canGenerate(JsonObject parameter, ParameterLocation parsedLocation, String parsedStyle) {
    return !parameter.containsKey("content") && !OpenApi3Utils.resolveExplode(parameter);
  }

  @Override
  public ParameterProcessor generate(JsonObject parameter, JsonPointer parameterPointer, ParameterLocation parsedLocation, String parsedStyle, Operation operation) {
    JsonObject schema = parameter.getJsonObject("schema", new JsonObject());
    return new ParameterProcessorImpl(
      parameter.getString("name"),
      parsedLocation,
      !parameter.getBoolean("required", false),
      new SingleValueParameterParser(
        parameter.getString("name"),
        OpenApi3Utils.isSchemaObjectOrCombinators(schema) ?
          generateValueParserForObjectParameter(parameter, parsedStyle) :
          OpenApi3Utils.isSchemaArray(schema) ? generateForArrayParameter(parameter, parsedStyle) : generateForPrimitiveParameter(parameter)
      ),
      new SchemaValidator(schemaParser.parse(
        parameter.getJsonObject("schema", new JsonObject()),
        parameterPointer.copy().append("schema")
      ))
    );
  }

  private ValueParser<String> generateValueParserForObjectParameter(JsonObject parameter, String parsedStyle) {
    JsonObject fakeSchema = OpenApi3Utils.mergeCombinatorsWithOnlyObjectSchemaIfNecessary(parameter.getJsonObject("schema", new JsonObject()));
    return ContainerSerializationStyles.resolve(parsedStyle).getObjectFactory().newObjectParser(
      ValueParserInferenceUtils.infeerPropertiesParsersForObjectSchema(fakeSchema),
      ValueParserInferenceUtils.infeerPatternPropertiesParsersForObjectSchema(fakeSchema),
      ValueParserInferenceUtils.infeerAdditionalPropertiesParserForObjectSchema(fakeSchema)
    );
  }

  private ValueParser<String> generateForArrayParameter(JsonObject parameter, String parsedStyle) {
    JsonObject fakeSchema = OpenApi3Utils.mergeCombinatorsWithOnlyObjectSchemaIfNecessary(parameter.getJsonObject("schema", new JsonObject()));
    return ContainerSerializationStyles.resolve(parsedStyle).getArrayFactory().newArrayParser(
      ValueParserInferenceUtils.infeerItemsParserForArraySchema(fakeSchema)
    );
  }

  private ValueParser<String> generateForPrimitiveParameter(JsonObject parameter) {
    return ValueParserInferenceUtils.infeerPrimitiveParser(parameter.getJsonObject("schema", new JsonObject()));
  }

}
