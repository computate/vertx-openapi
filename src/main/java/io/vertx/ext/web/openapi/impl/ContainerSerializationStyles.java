package io.vertx.ext.web.openapi.impl;

import io.vertx.ext.web.validation.ParameterLocation;
import io.vertx.ext.web.validation.dsl.ArrayParserFactory;
import io.vertx.ext.web.validation.dsl.ObjectParserFactory;
import io.vertx.ext.web.validation.impl.SplitterCharArrayParser;
import io.vertx.ext.web.validation.impl.SplitterCharObjectParser;

public enum ContainerSerializationStyles {
  CSV(
    itemsParser -> new SplitterCharArrayParser(itemsParser, ","),
    (propertiesParser, patternPropertiesParser, additionalPropertiesParser) -> new SplitterCharObjectParser(propertiesParser, patternPropertiesParser, additionalPropertiesParser, ",")
  ),
  DSV(
    itemsParser -> new SplitterCharArrayParser(itemsParser, "."),
    (propertiesParser, patternPropertiesParser, additionalPropertiesParser) -> new SplitterCharObjectParser(propertiesParser, patternPropertiesParser, additionalPropertiesParser, ".")
  );

  private final ArrayParserFactory arrayFactory;
  private final ObjectParserFactory objectFactory;

  ContainerSerializationStyles(ArrayParserFactory arrayFactory, ObjectParserFactory objectFactory) {
    this.arrayFactory = arrayFactory;
    this.objectFactory = objectFactory;
  }

  public ArrayParserFactory getArrayFactory() {
    return arrayFactory;
  }

  public ObjectParserFactory getObjectFactory() {
    return objectFactory;
  }

  public static ContainerSerializationStyles resolve(String style) {
    switch (style) {
      case "form":
      case "simple":
      case "matrix":
        return CSV;
      case "label":
        return DSV;
      default:
        return null;
    }
  }
}
