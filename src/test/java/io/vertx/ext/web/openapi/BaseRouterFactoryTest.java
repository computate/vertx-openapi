package io.vertx.ext.web.openapi;


import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.json.schema.SchemaParser;
import io.vertx.ext.json.schema.SchemaParserOptions;
import io.vertx.ext.json.schema.SchemaRouter;
import io.vertx.ext.json.schema.SchemaRouterOptions;
import io.vertx.ext.json.schema.draft7.Draft7SchemaParser;
import io.vertx.ext.json.schema.openapi3.OpenAPI3SchemaParser;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.validation.testutils.ValidationTestUtils;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.function.Consumer;

@ExtendWith(VertxExtension.class)
public abstract class BaseRouterFactoryTest {

  public SchemaRouter schemaRouter;
  public SchemaParser parser;
  public HttpServer server;
  public WebClient client;
  public Router router;

  @BeforeEach
  public void setUp(Vertx vertx) {
    schemaRouter = SchemaRouter.create(vertx, new SchemaRouterOptions());
    parser = OpenAPI3SchemaParser.create(new SchemaParserOptions(), schemaRouter);

    client = WebClient.create(vertx, new WebClientOptions().setDefaultPort(9000).setDefaultHost("localhost"));
  }

  @AfterEach
  public void tearDown(VertxTestContext testContext) {
    if (client != null) client.close();
    if (server != null) server.close(testContext.completing());
    else testContext.completeNow();
  }

  protected Future<Void> startServer(Vertx vertx, RouterFactory factory) {
    Future<Void> fut = Future.future();
    try {
      router = factory.createRouter();
    } catch (Throwable e) {
      return Future.failedFuture(e);
    }
    ValidationTestUtils.mountRouterFailureHandler(router);
    client = WebClient.create(vertx, new WebClientOptions().setDefaultPort(9000).setDefaultHost("localhost"));
    server = vertx
      .createHttpServer()
      .requestHandler(router)
      .listen(9000, h -> {
        if (h.failed()) fut.fail(h.cause());
        else fut.complete();
      });
    return fut;
  }

  protected Future<Void> loadFactoryAndStartServer(Vertx vertx, String specUri, VertxTestContext testContext, Consumer<RouterFactory> configurator) {
    Future<Void> f = Future.future();
    RouterFactory.create(vertx, specUri, testContext.succeeding(rf -> {
        configurator.accept(rf);
        startServer(vertx, rf).setHandler(f);
    }));
    return f;
  }

}
