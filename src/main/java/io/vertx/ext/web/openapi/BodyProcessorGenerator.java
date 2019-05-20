package io.vertx.ext.web.openapi;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.BodyProcessor;
import io.vertx.ext.web.validation.RequestPredicateResult;

import java.util.List;
import java.util.function.Function;

@VertxGen
public interface BodyProcessorGenerator {

  boolean canGenerate(String mediaTypeName, JsonObject mediaTypeObject);

  @GenIgnore
  BodyProcessor generate(
    String mediaTypeName,
    JsonObject mediaTypeObject,
    JsonPointer mediaTypePointer,
    Operation operation,
    List<Function<RoutingContext, RequestPredicateResult>> predicates
  );
}
