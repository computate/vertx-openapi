package io.vertx.ext.web.openapi;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.impl.JsonPointerIteratorImpl;

public class JsonPointerIteratorWithLoader extends JsonPointerIteratorImpl {
  private final OpenAPILoader loader;

  public JsonPointerIteratorWithLoader(OpenAPILoader loader) {
    super();
    this.loader = loader;
  }

  @Override
  public boolean objectContainsKey(Object value, String key) {
    if (value instanceof JsonObject)
      value = loader.solveIfNeeded((JsonObject) value);
    return super.objectContainsKey(value, key);
  }

  @Override
  public Object getObjectParameter(Object value, String key, boolean createOnMissing) {
    if (value instanceof JsonObject)
      value = loader.solveIfNeeded((JsonObject) value);
    return super.getObjectParameter(value, key, createOnMissing);
  }

}
