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

public class JsonParameterProcessorGenerator implements ParameterProcessorGenerator {

  private final static JsonPointer CONTENT_JSON_POINTER = JsonPointer.create().append("content").append("application/json");
  private final static JsonPointer SCHEMA_POINTER = CONTENT_JSON_POINTER.copy().append("schema");

  private final OpenAPI3SchemaParser schemaParser;

  public JsonParameterProcessorGenerator(OpenAPI3SchemaParser schemaParser) {
    this.schemaParser = schemaParser;
  }

  @Override
  public boolean canGenerate(JsonObject parameter, ParameterLocation parsedLocation, String parsedStyle) {
    return CONTENT_JSON_POINTER.queryJson(parameter) != null;
  }

  @Override
  public ParameterProcessor generate(JsonObject parameter, JsonPointer parameterPointer, ParameterLocation parsedLocation, String parsedStyle, Operation operation) {
    return new ParameterProcessorImpl(
      parameter.getString("name"),
      parsedLocation,
      !parameter.getBoolean("required", false),
      new SingleValueParameterParser(parameter.getString("name"), ValueParser.JSON_PARSER),
      new SchemaValidator(schemaParser.parse(
        SCHEMA_POINTER.queryJson(parameter),
        parameterPointer.copy().append("content").append("application/json").append("schema")
      ))
    );
  }

}
