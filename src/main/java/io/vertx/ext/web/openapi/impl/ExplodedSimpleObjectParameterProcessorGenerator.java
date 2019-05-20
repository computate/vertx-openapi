package io.vertx.ext.web.openapi.impl;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.json.schema.openapi3.OpenAPI3SchemaParser;
import io.vertx.ext.web.openapi.Operation;
import io.vertx.ext.web.openapi.ParameterProcessorGenerator;
import io.vertx.ext.web.validation.MalformedValueException;
import io.vertx.ext.web.validation.ParameterLocation;
import io.vertx.ext.web.validation.ParameterProcessor;
import io.vertx.ext.web.validation.ValueParser;
import io.vertx.ext.web.validation.impl.*;

import java.util.Map;
import java.util.regex.Pattern;

public class ExplodedSimpleObjectParameterProcessorGenerator implements ParameterProcessorGenerator {

  private static class ExplodedSimpleObjectValueParser extends ObjectParser<String> implements ValueParser<String>  {

    public ExplodedSimpleObjectValueParser(Map<String, ValueParser<String>> propertiesParsers, Map<Pattern, ValueParser<String>> patternPropertiesParsers, ValueParser<String> additionalPropertiesParser) {
      super(propertiesParsers, patternPropertiesParsers, additionalPropertiesParser);
    }

    @Override
    protected ValueParser<String> getAdditionalPropertiesParserIfRequired() {
      return additionalPropertiesParser;
    }

    @Override
    protected boolean mustNullateValue(String serialized, ValueParser<String> parser) {
      return serialized == null || (serialized.isEmpty() && parser != ValueParser.NOOP_PARSER);
    }

    @Override
    public @Nullable Object parse(String serialized) throws MalformedValueException {
      JsonObject result = new JsonObject();
      String[] values = serialized.split(Pattern.quote(","), -1);
      // Key value pairs -> odd length not allowed
      for (String value : values) {
        // empty key not allowed!
        String[] values_internal = value.split("=", -1);
        if (values_internal[0].length() == 0) {
          throw new MalformedValueException("Empty key not allowed");
        } else {
          Map.Entry<String, Object> parsedEntry = parseField(values_internal[0], values_internal[1]);
          if (parsedEntry != null)
            result.put(parsedEntry.getKey(), parsedEntry.getValue());
        }
      }
      return result;
    }
  }

  private final OpenAPI3SchemaParser schemaParser;

  public ExplodedSimpleObjectParameterProcessorGenerator(OpenAPI3SchemaParser schemaParser) {
    this.schemaParser = schemaParser;
  }

  @Override
  public boolean canGenerate(JsonObject parameter, ParameterLocation parsedLocation, String parsedStyle) {
    return parsedStyle.equals("simple") &&
      OpenApi3Utils.resolveExplode(parameter) &&
      OpenApi3Utils.isSchemaObjectOrCombinators(parameter.getJsonObject("schema", new JsonObject()));
  }

  @Override
  public ParameterProcessor generate(JsonObject parameter, JsonPointer parameterPointer, ParameterLocation parsedLocation, String parsedStyle, Operation operation) {
    JsonObject fakeSchema = OpenApi3Utils.mergeCombinatorsWithOnlyObjectSchemaIfNecessary(parameter.getJsonObject("schema", new JsonObject()));
    return new ParameterProcessorImpl(
      parameter.getString("name"),
      parsedLocation,
      !parameter.getBoolean("required", false),
      new SingleValueParameterParser(
        parameter.getString("name"),
        new ExplodedSimpleObjectValueParser(
          ValueParserInferenceUtils.infeerPropertiesParsersForObjectSchema(fakeSchema),
          ValueParserInferenceUtils.infeerPatternPropertiesParsersForObjectSchema(fakeSchema),
          ValueParserInferenceUtils.infeerAdditionalPropertiesParserForObjectSchema(fakeSchema)
        )
      ),
      new SchemaValidator(schemaParser.parse(parameter.getJsonObject("schema"), parameterPointer.copy().append("schema")))
    );
  }
}
