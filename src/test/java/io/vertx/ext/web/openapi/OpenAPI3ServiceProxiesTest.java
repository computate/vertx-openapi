package io.vertx.ext.web.openapi;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.serviceproxy.ServiceBinder;

import java.util.concurrent.CountDownLatch;

/**
 * These tests are about OpenAPI3RouterFactory and Service Proxy integrations
 * @author Francesco Guardiani @slinkydeveloper
 */
public class OpenAPI3ServiceProxiesTest extends ApiWebTestBase {

  private OpenAPI3RouterFactory routerFactory;

  private RouterFactoryOptions HANDLERS_TESTS_OPTIONS = new RouterFactoryOptions()
    .setRequireSecurityHandlers(false)
    .setMountNotImplementedHandler(false);

  @Override
  public void setUp() throws Exception {
    super.setUp();
    stopServer(); // Have to stop default server of WebTestBase
    client = vertx.createHttpClient(new HttpClientOptions().setDefaultPort(8080));
  }

  @Override
  public void tearDown() throws Exception {
    stopServer();
    if (client != null) {
      try {
        client.close();
      } catch (IllegalStateException e) {
      }
    }
    super.tearDown();
  }

  @Test
  public void testOperationIdSanitizer() {
    assertThat(OpenApi3Utils.sanitizeOperationId("operationId")).isEqualTo("operationId");
    assertThat(OpenApi3Utils.sanitizeOperationId("operation id")).isEqualTo("operationId");
    assertThat(OpenApi3Utils.sanitizeOperationId("operation Id")).isEqualTo("operationId");
    assertThat(OpenApi3Utils.sanitizeOperationId("operation-id")).isEqualTo("operationId");
    assertThat(OpenApi3Utils.sanitizeOperationId("operation_id")).isEqualTo("operationId");
    assertThat(OpenApi3Utils.sanitizeOperationId("operation__id-")).isEqualTo("operationId");
    assertThat(OpenApi3Utils.sanitizeOperationId("operation_- id ")).isEqualTo("operationId");
    assertThat(OpenApi3Utils.sanitizeOperationId("operation_- A B")).isEqualTo("operationAB");
  }

  @Test
  public void serviceProxyManualTest() throws Exception {
    TestService service = new TestServiceImpl(vertx);

    final ServiceBinder serviceBinder = new ServiceBinder(vertx).setAddress("someAddress");
    MessageConsumer<JsonObject> consumer = serviceBinder.register(TestService.class, service);

    CountDownLatch latch = new CountDownLatch(1);
    OpenAPI3RouterFactory.create(this.vertx, "src/test/resources/swaggers/service_proxy_test.yaml",
      openAPI3RouterFactoryAsyncResult -> {
        routerFactory = openAPI3RouterFactoryAsyncResult.result();
        routerFactory.setOptions(HANDLERS_TESTS_OPTIONS);

        routerFactory.mountOperationToEventBus("testA", "someAddress");

        latch.countDown();
      });
    awaitLatch(latch);

    startServer();

    testRequestWithJSON(
      HttpMethod.POST,
      "/testA",
      new JsonObject().put("hello", "Ciao").put("name", "Francesco").toBuffer(),
      200,
      "OK",
      new JsonObject().put("result", "Ciao Francesco!").toBuffer()
    );

    consumer.unregister();
  }

  @Test
  public void serviceProxyWithReflectionsTest() throws Exception {
    TestService service = new TestServiceImpl(vertx);

    final ServiceBinder serviceBinder = new ServiceBinder(vertx).setAddress("someAddress");
    MessageConsumer<JsonObject> consumer = serviceBinder.register(TestService.class, service);

    CountDownLatch latch = new CountDownLatch(1);
    OpenAPI3RouterFactory.create(this.vertx, "src/test/resources/swaggers/service_proxy_test.yaml",
      openAPI3RouterFactoryAsyncResult -> {
        routerFactory = openAPI3RouterFactoryAsyncResult.result();
        routerFactory.setOptions(HANDLERS_TESTS_OPTIONS.setMountValidationFailureHandler(true));

        routerFactory.mountServiceInterface(service.getClass(), "someAddress");

        latch.countDown();
      });
    awaitLatch(latch);

    startServer();

    testRequestWithJSON(
      HttpMethod.POST,
      "/testA",
      new JsonObject().put("hello", "Ciao").put("name", "Francesco").toBuffer(),
      200,
      "OK",
      new JsonObject().put("result", "Ciao Francesco!").toBuffer()
    );

    testRequestWithJSON(
      HttpMethod.POST,
      "/testB",
      new JsonObject().put("hello", "Ciao").put("name", "Francesco").toBuffer(),
      200,
      "OK",
      new JsonObject().put("result", "Ciao Francesco?").toBuffer()
    );

    testRequestWithJSON(HttpMethod.POST, "/testB", new JsonObject().put("hello", "Ciao").toBuffer(), 400, "Bad Request");

    consumer.unregister();
  }

  @Test
  public void serviceProxyWithTagsTest() throws Exception {
    TestService service = new TestServiceImpl(vertx);
    final ServiceBinder serviceBinder = new ServiceBinder(vertx).setAddress("address");
    MessageConsumer<JsonObject> serviceConsumer = serviceBinder.register(TestService.class, service);

    AnotherTestService anotherService = AnotherTestService.create(vertx);
    final ServiceBinder anotherServiceBinder = new ServiceBinder(vertx).setAddress("anotherAddress");
    MessageConsumer<JsonObject> anotherServiceConsumer = anotherServiceBinder.register(AnotherTestService.class, anotherService);

    CountDownLatch latch = new CountDownLatch(1);
    OpenAPI3RouterFactory.create(this.vertx, "src/test/resources/swaggers/service_proxy_test.yaml",
      openAPI3RouterFactoryAsyncResult -> {
        routerFactory = openAPI3RouterFactoryAsyncResult.result();
        routerFactory.setOptions(HANDLERS_TESTS_OPTIONS);

        routerFactory.mountServiceFromTag("test", "address");
        routerFactory.mountServiceFromTag("anotherTest", "anotherAddress");

        latch.countDown();
      });
    awaitLatch(latch);

    startServer();

    testRequestWithJSON(
      HttpMethod.POST,
      "/testA",
      new JsonObject().put("hello", "Ciao").put("name", "Francesco").toBuffer(),
      200,
      "OK",
      new JsonObject().put("result", "Ciao Francesco!").toBuffer()
    );

    testRequestWithJSON(
      HttpMethod.POST,
      "/testB",
      new JsonObject().put("hello", "Ciao").put("name", "Francesco").toBuffer(),
      200,
      "OK",
      new JsonObject().put("result", "Ciao Francesco?").toBuffer()
    );

    testRequestWithJSON(
      HttpMethod.POST,
      "/testC",
      new JsonObject().put("hello", "Ciao").put("name", "Francesco").toBuffer(),
      200,
      "OK",
      new JsonObject().put("anotherResult", "Francesco Ciao!").toBuffer()
    );

    testRequestWithJSON(
      HttpMethod.POST,
      "/testD",
      new JsonObject().put("hello", "Ciao").put("name", "Francesco").toBuffer(),
      200,
      "OK",
      new JsonObject().put("content-type", "application/json").put("anotherResult", "Francesco Ciao?").toBuffer()
    );

    serviceConsumer.unregister();
    anotherServiceConsumer.unregister();
  }
}
