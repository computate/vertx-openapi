package io.vertx.ext.web.openapi.impl;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.json.schema.Schema;
import io.vertx.ext.json.schema.SchemaRouter;
import io.vertx.ext.json.schema.openapi3.OpenAPI3SchemaParser;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.Utils;
import io.vertx.ext.web.openapi.BodyProcessorGenerator;
import io.vertx.ext.web.openapi.Operation;
import io.vertx.ext.web.validation.BodyProcessor;
import io.vertx.ext.web.validation.RequestPredicateResult;
import io.vertx.ext.web.validation.impl.JsonBodyProcessorImpl;
import io.vertx.ext.web.validation.impl.SchemaValidator;

import java.util.List;
import java.util.function.Function;

public class JsonBodyProcessorGenerator implements BodyProcessorGenerator {

  private final OpenAPI3SchemaParser schemaParser;

  public JsonBodyProcessorGenerator(OpenAPI3SchemaParser schemaParser) {
    this.schemaParser = schemaParser;
  }

  @Override
  public boolean canGenerate(String mediaTypeName, JsonObject mediaTypeObject) {
    return Utils.isJsonContentType(mediaTypeName);
  }

  @Override
  public BodyProcessor generate(String mediaTypeName, JsonObject mediaTypeObject, JsonPointer mediaTypePointer, Operation operation, List<Function<RoutingContext, RequestPredicateResult>> predicates) {
    Schema schema = schemaParser.parse(
      mediaTypeObject.getJsonObject("schema", new JsonObject()),
      mediaTypePointer.copy().append("schema")
    );
    return new JsonBodyProcessorImpl(new SchemaValidator(schema));
  }
}
