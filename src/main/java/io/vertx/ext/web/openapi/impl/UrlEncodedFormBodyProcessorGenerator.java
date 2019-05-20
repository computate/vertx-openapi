package io.vertx.ext.web.openapi.impl;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.json.schema.Schema;
import io.vertx.ext.json.schema.openapi3.OpenAPI3SchemaParser;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.BodyProcessorGenerator;
import io.vertx.ext.web.openapi.Operation;
import io.vertx.ext.web.validation.BodyProcessor;
import io.vertx.ext.web.validation.RequestPredicateResult;
import io.vertx.ext.web.validation.impl.FormBodyProcessorImpl;
import io.vertx.ext.web.validation.impl.SchemaValidator;
import io.vertx.ext.web.validation.impl.ValueParserInferenceUtils;

import java.util.List;
import java.util.function.Function;

public class UrlEncodedFormBodyProcessorGenerator implements BodyProcessorGenerator {

  private final OpenAPI3SchemaParser schemaParser;

  public UrlEncodedFormBodyProcessorGenerator(OpenAPI3SchemaParser schemaParser) {
    this.schemaParser = schemaParser;
  }

  @Override
  public boolean canGenerate(String mediaTypeName, JsonObject mediaTypeObject) {
    return mediaTypeName.equals("application/x-www-form-urlencoded");
  }

  @Override
  public BodyProcessor generate(String mediaTypeName, JsonObject mediaTypeObject, JsonPointer mediaTypePointer, Operation operation, List<Function<RoutingContext, RequestPredicateResult>> predicates) {
    Schema schema = schemaParser.parse(
      mediaTypeObject.getJsonObject("schema", new JsonObject()),
      mediaTypePointer.copy().append("schema")
    );
    JsonObject mergedSchema = OpenApi3Utils.mergeCombinatorsWithOnlyObjectSchemaIfNecessary(mediaTypeObject.getJsonObject("schema", new JsonObject()));
    return new FormBodyProcessorImpl(
      ValueParserInferenceUtils.infeerPropertiesFormValueParserForObjectSchema(mergedSchema),
      ValueParserInferenceUtils.infeerPatternPropertiesFormValueParserForObjectSchema(mergedSchema),
      ValueParserInferenceUtils.infeerAdditionalPropertiesFormValueParserForObjectSchema(mergedSchema),
      mediaTypeName,
      new SchemaValidator(schema)
    );
  }
}
