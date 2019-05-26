package io.vertx.ext.web.openapi.impl;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.web.validation.ParameterLocation;
import io.vertx.ext.web.validation.ParameterProcessor;
import io.vertx.ext.web.validation.ValueParser;
import io.vertx.ext.web.validation.impl.ParameterProcessorImpl;
import io.vertx.ext.web.validation.impl.SingleValueParameterParser;
import io.vertx.ext.web.validation.impl.ValueParserInferenceUtils;

public class DefaultParameterProcessorGenerator implements ParameterProcessorGenerator {

  @Override
  public boolean canGenerate(JsonObject parameter, JsonObject fakeSchema, ParameterLocation parsedLocation, String parsedStyle) {
    return !parameter.containsKey("content");
  }

  @Override
  public ParameterProcessor generate(JsonObject parameter, JsonObject fakeSchema, JsonPointer parameterPointer, ParameterLocation parsedLocation, String parsedStyle, GeneratorContext context) {
    SchemaHolder schemas = context.getSchemaHolder(
      parameter.getJsonObject("schema", new JsonObject()),
      fakeSchema,
      parameterPointer.copy().append("schema")
    );

    ValueParser<String> valueParser;
    if (OpenApi3Utils.isSchemaObjectOrCombinators(fakeSchema)) {
      valueParser = generateValueParserForObjectParameter(schemas, parsedStyle);
    } else if (OpenApi3Utils.isSchemaArray(fakeSchema)) {
      valueParser = generateForArrayParameter(schemas, parsedStyle);
    } else {
      valueParser = generateForPrimitiveParameter(schemas);
    }

    return new ParameterProcessorImpl(
      parameter.getString("name"),
      parsedLocation,
      !parameter.getBoolean("required", false),
      new SingleValueParameterParser(
        parameter.getString("name"),
        valueParser
      ),
      schemas.getValidator()
    );
  }

  private ValueParser<String> generateValueParserForObjectParameter(SchemaHolder schemas, String parsedStyle) {
    return ContainerSerializationStyles.resolve(parsedStyle).getObjectFactory().newObjectParser(
      ValueParserInferenceUtils.infeerPropertiesParsersForObjectSchema(schemas.getFakeSchema()),
      ValueParserInferenceUtils.infeerPatternPropertiesParsersForObjectSchema(schemas.getFakeSchema()),
      ValueParserInferenceUtils.infeerAdditionalPropertiesParserForObjectSchema(schemas.getFakeSchema())
    );
  }

  private ValueParser<String> generateForArrayParameter(SchemaHolder schemas, String parsedStyle) {
    return ContainerSerializationStyles.resolve(parsedStyle).getArrayFactory().newArrayParser(
      ValueParserInferenceUtils.infeerItemsParserForArraySchema(schemas.getFakeSchema())
    );
  }

  private ValueParser<String> generateForPrimitiveParameter(SchemaHolder schemas) {
    return ValueParserInferenceUtils.infeerPrimitiveParser(schemas.getFakeSchema());
  }

}
