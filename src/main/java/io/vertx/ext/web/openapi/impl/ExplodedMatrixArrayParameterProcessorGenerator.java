package io.vertx.ext.web.openapi.impl;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.core.net.impl.URIDecoder;
import io.vertx.ext.json.schema.openapi3.OpenAPI3SchemaParser;
import io.vertx.ext.web.openapi.Operation;
import io.vertx.ext.web.openapi.ParameterProcessorGenerator;
import io.vertx.ext.web.validation.MalformedValueException;
import io.vertx.ext.web.validation.ParameterLocation;
import io.vertx.ext.web.validation.ParameterProcessor;
import io.vertx.ext.web.validation.ValueParser;
import io.vertx.ext.web.validation.impl.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ExplodedMatrixArrayParameterProcessorGenerator implements ParameterProcessorGenerator {

  private static class ExplodedMatrixArrayValueParser extends ArrayParser implements ValueParser<String>  {

    private final Pattern MATRIX_PARAMETER = Pattern.compile(";(?<key>[^;=]*)=(?<value>[^\\/\\;\\?\\:\\@\\&\\\"\\<\\>\\#\\%\\{\\}\\|\\\\\\^\\~\\[\\]\\`]*)");

    public ExplodedMatrixArrayValueParser(ValueParser<String> itemsParser) {
      super(itemsParser);
    }

    @Override
    protected boolean mustNullateValue(String serialized) {
      return serialized == null || (serialized.isEmpty() && itemsParser != ValueParser.NOOP_PARSER);
    }

    @Override
    public @Nullable JsonArray parse(String serialized) throws MalformedValueException {
      return deserializeArray(serialized)
        .map(this::parseValue)
        .reduce(new JsonArray(), JsonArray::add, JsonArray::addAll);
    }

    private Stream<String> deserializeArray(String serialized) throws MalformedValueException {
      Stream.Builder<String> values = Stream.builder();
      Matcher m = MATRIX_PARAMETER.matcher(serialized);
      while (m.find())
        values.add(URIDecoder.decodeURIComponent(m.group("value"), false));
      return values.build();
    }
  }

  private final OpenAPI3SchemaParser schemaParser;

  public ExplodedMatrixArrayParameterProcessorGenerator(OpenAPI3SchemaParser schemaParser) {
    this.schemaParser = schemaParser;
  }

  @Override
  public boolean canGenerate(JsonObject parameter, ParameterLocation parsedLocation, String parsedStyle) {
    return parsedStyle.equals("matrix") &&
      OpenApi3Utils.resolveExplode(parameter) &&
      OpenApi3Utils.isSchemaArray(parameter.getJsonObject("schema", new JsonObject()));
  }

  @Override
  public ParameterProcessor generate(JsonObject parameter, JsonPointer parameterPointer, ParameterLocation parsedLocation, String parsedStyle, Operation operation) {
    JsonObject schema = parameter.getJsonObject("schema", new JsonObject());
    JsonObject fakeSchema = OpenApi3Utils.mergeCombinatorsWithOnlyObjectSchemaIfNecessary(schema);
    return new ParameterProcessorImpl(
      parameter.getString("name"),
      parsedLocation,
      !parameter.getBoolean("required", false),
      new SingleValueParameterParser(
        parameter.getString("name"),
        new ExplodedMatrixArrayValueParser(ValueParserInferenceUtils.infeerItemsParserForArraySchema(fakeSchema))
      ),
      new SchemaValidator(schemaParser.parse(
        parameter.getJsonObject("schema", new JsonObject()),
        parameterPointer.copy().append("schema")
      ))
    );
  }
}
