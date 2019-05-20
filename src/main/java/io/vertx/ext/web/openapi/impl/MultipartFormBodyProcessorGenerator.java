package io.vertx.ext.web.openapi.impl;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.json.schema.openapi3.OpenAPI3SchemaParser;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.BodyProcessorGenerator;
import io.vertx.ext.web.openapi.Operation;
import io.vertx.ext.web.validation.*;
import io.vertx.ext.web.validation.impl.FormBodyProcessorImpl;
import io.vertx.ext.web.validation.impl.FormValueParser;
import io.vertx.ext.web.validation.impl.SchemaValidator;
import io.vertx.ext.web.validation.impl.ValueParserInferenceUtils;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.regex.Pattern;

public class MultipartFormBodyProcessorGenerator implements BodyProcessorGenerator {

  private final OpenAPI3SchemaParser schemaParser;

  public MultipartFormBodyProcessorGenerator(OpenAPI3SchemaParser schemaParser) {
    this.schemaParser = schemaParser;
  }

  @Override
  public boolean canGenerate(String mediaTypeName, JsonObject mediaTypeObject) {
    return mediaTypeName.equals("multipart/form-data");
  }

  @Override
  public BodyProcessor generate(String mediaTypeName, JsonObject mediaTypeObject, JsonPointer mediaTypePointer, Operation operation, List<Function<RoutingContext, RequestPredicateResult>> predicates) {
    JsonObject realSchema = mediaTypeObject.getJsonObject("schema", new JsonObject());
    JsonObject fakeSchema = OpenApi3Utils.mergeCombinatorsWithOnlyObjectSchemaIfNecessary(realSchema);
    Map<String, ValueParser<List<String>>> propertiesValueParsers =
      ValueParserInferenceUtils.infeerPropertiesFormValueParserForObjectSchema(fakeSchema);
    Map<Pattern, ValueParser<List<String>>> patternPropertiesValueParsers =
      ValueParserInferenceUtils.infeerPatternPropertiesFormValueParserForObjectSchema(fakeSchema);
    ValueParser<List<String>> additionalPropertiesValueParser = ValueParserInferenceUtils.infeerAdditionalPropertiesFormValueParserForObjectSchema(fakeSchema);

    for (Entry<String, Object> pe : fakeSchema.getJsonObject("properties", new JsonObject())) {
      JsonObject propSchema = (JsonObject) pe.getValue();
      String encoding = (String) JsonPointer.from("/encoding/" + pe.getKey()).queryJson(mediaTypeObject);

      if (encoding == null) {
        if (OpenApi3Utils.isSchemaObjectOrCombinators(propSchema) ||
          (OpenApi3Utils.isSchemaArray(propSchema) &&
            OpenApi3Utils.isSchemaObjectOrAllOfType((propSchema.getJsonObject("items", new JsonObject()))))) {
          propertiesValueParsers.put(pe.getKey(), new FormValueParser(false, ValueParser.JSON_PARSER));
        } else if ("type".equals(propSchema.getString("type")) &&
          ("binary".equals(propSchema.getString("format")) || "base64".equals(propSchema.getString("format")))) {
          predicates.add(
            RequestPredicate.multipartFileUploadExists(pe.getKey(), Pattern.compile(Pattern.quote("application/octet-stream")))
          );
          propertiesValueParsers.remove(pe.getKey());
          searchPropAndRemoveInSchema(realSchema, pe.getKey());
        }
      } else {
        predicates.add(
          RequestPredicate.multipartFileUploadExists(pe.getKey(), Pattern.compile(OpenApi3Utils.resolveContentTypeRegex(encoding)))
        );
        propertiesValueParsers.remove(pe.getKey());
        searchPropAndRemoveInSchema(realSchema, pe.getKey());
      }
    }

    return new FormBodyProcessorImpl(
      propertiesValueParsers,
      patternPropertiesValueParsers,
      additionalPropertiesValueParser,
      mediaTypeName,
      new SchemaValidator(schemaParser.parse(realSchema, mediaTypePointer.copy().append("schema")))
    );
  }

  private void searchPropAndRemoveInSchema(JsonObject object, String propName) {
    if (object.containsKey("allOf") || object.containsKey("anyOf") || object.containsKey("oneOf")) {
      object.getJsonArray("allOf", object.getJsonArray("anyOf", object.getJsonArray("oneOf")))
        .forEach(j -> searchPropAndRemoveInSchema((JsonObject) j, propName));
    } else {
      if (object.containsKey("properties")) {
        object.getJsonObject("properties").remove(propName);
      }
      if (object.containsKey("required")) {
        object.getJsonArray("required").remove(propName);
      }
    }
  }
}
