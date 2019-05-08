package io.vertx.ext.web.openapi;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.multipart.MultipartForm;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.vertx.ext.web.validation.testutils.TestRequest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * This tests are about RouterFactory behaviours
 *
 * @author Francesco Guardiani @slinkydeveloper
 */
@SuppressWarnings("unchecked")
public class RouterFactoryTest extends BaseRouterFactoryTest {

  private Future<Void> startFileServer(Vertx vertx, VertxTestContext testContext) {
    Future<Void> f = Future.future();
    Router router = Router.router(vertx);
    router.route().handler(StaticHandler.create("src/test/resources"));
    vertx.createHttpServer(new HttpServerOptions().setPort(9001))
      .requestHandler(router)
      .listen(testContext.succeeding(h -> f.complete()));
    return f;
  }

  private Future<Void> startSecuredFileServer(Vertx vertx, VertxTestContext testContext) {
    Future<Void> f = Future.future();
    Router router = Router.router(vertx);
    router.route()
      .handler((RoutingContext ctx) -> {
        if (ctx.request().getHeader("Authorization") == null) ctx.fail(HttpResponseStatus.FORBIDDEN.code());
        else ctx.next();
      })
      .handler(StaticHandler.create("src/test/resources"));
    vertx.createHttpServer(new HttpServerOptions().setPort(9001))
      .requestHandler(router)
      .listen(testContext.succeeding(h -> f.complete()));
    return f;
  }

  @Test
  public void loadSpecFromFile(Vertx vertx, VertxTestContext testContext) {
    RouterFactory.create(vertx, "src/test/resources/specs/router_factory_test.yaml",
      routerFactoryAsyncResult -> {
        assertThat(routerFactoryAsyncResult.succeeded()).isTrue();
        assertThat(routerFactoryAsyncResult.result()).isNotNull();
        testContext.completeNow();
      });
  }

  @Test
  public void failLoadSpecFromFile(Vertx vertx, VertxTestContext testContext) {
    RouterFactory.create(vertx, "src/test/resources/specs/aaa.yaml",
      routerFactoryAsyncResult -> {
        assertThat(routerFactoryAsyncResult.failed()).isTrue();
        assertThat(routerFactoryAsyncResult.cause().getClass())
          .isEqualTo(RouterFactoryException.class);
        assertThat(((RouterFactoryException) routerFactoryAsyncResult.cause()).type())
          .isEqualTo(RouterFactoryException.ErrorType.INVALID_FILE);
        testContext.completeNow();
      });
  }

  @Test
  public void loadWrongSpecFromFile(Vertx vertx, VertxTestContext testContext) {
    RouterFactory.create(vertx, "src/test/resources/specs/bad_spec.yaml",
      routerFactoryAsyncResult -> {
        assertThat(routerFactoryAsyncResult.failed()).isTrue();
        assertThat(routerFactoryAsyncResult.cause().getClass())
          .isEqualTo(RouterFactoryException.class);
        assertThat(((RouterFactoryException) routerFactoryAsyncResult.cause()).type())
          .isEqualTo(RouterFactoryException.ErrorType.INVALID_SPEC);
        testContext.completeNow();
      });
  }

  @Test
  public void loadSpecFromURL(Vertx vertx, VertxTestContext testContext) {
    startFileServer(vertx, testContext).setHandler(h -> {
      RouterFactory.create(vertx, "http://localhost:8081/specs/router_factory_test.yaml",
        routerFactoryAsyncResult -> {
          assertThat(routerFactoryAsyncResult.succeeded()).isTrue();
          assertThat(routerFactoryAsyncResult.result()).isNotNull();
          testContext.completeNow();
        });
    });
  }

  @Test
  public void loadSpecFromURLWithAuthorizationValues(Vertx vertx, VertxTestContext testContext) {
    startSecuredFileServer(vertx, testContext).setHandler(h -> {
      RouterFactory.create(
        vertx,
        "http://localhost:8081/specs/router_factory_test.yaml",
        new OpenAPILoaderOptions()
          .putAuthHeader("Authorization", "Bearer xx.yy.zz"),
        routerFactoryAsyncResult -> {
          assertThat(routerFactoryAsyncResult.succeeded()).isTrue();
          assertThat(routerFactoryAsyncResult.result()).isNotNull();
          testContext.completeNow();
        });
    });
  }

  @Test
  public void failLoadSpecFromURL(Vertx vertx, VertxTestContext testContext) {
    startFileServer(vertx, testContext).setHandler(h -> {
      RouterFactory.create(vertx, "http://localhost:8081/specs/does_not_exist.yaml",
        routerFactoryAsyncResult -> {
          assertThat(routerFactoryAsyncResult.failed()).isTrue();
          assertThat(routerFactoryAsyncResult.cause().getClass()).isEqualTo(RouterFactoryException.class);
          assertThat(((RouterFactoryException) routerFactoryAsyncResult.cause()).type()).isEqualTo(RouterFactoryException.ErrorType.INVALID_FILE);
          testContext.completeNow();
        });
    });
  }

  private RouterFactoryOptions HANDLERS_TESTS_OPTIONS = new RouterFactoryOptions()
    .setRequireSecurityHandlers(false);

  @Test
  public void mountHandlerTest(Vertx vertx, VertxTestContext testContext) {
    Checkpoint checkpoint = testContext.checkpoint();
    loadFactoryAndStartServer(vertx, "src/test/resources/specs/router_factory_test.yaml", testContext, routerFactory -> {
      routerFactory.setOptions(HANDLERS_TESTS_OPTIONS);

      routerFactory.operation("listPets").handler(routingContext ->
        routingContext
          .response()
          .setStatusCode(200)
          .end()
      );
    }).setHandler(h ->
      testRequest(client, HttpMethod.GET, "/pets")
        .asserts(statusCode(200))
        .send(testContext, checkpoint)
    );
  }

  @Test
  public void mountFailureHandlerTest(Vertx vertx, VertxTestContext testContext) {
    Checkpoint checkpoint = testContext.checkpoint();
    loadFactoryAndStartServer(vertx, "src/test/resources/specs/router_factory_test.yaml", testContext, routerFactory -> {
      routerFactory.setOptions(HANDLERS_TESTS_OPTIONS);

      routerFactory
        .operation("listPets")
        .handler(routingContext -> routingContext.fail(null))
        .failureHandler(routingContext -> routingContext
          .response()
          .setStatusCode(500)
          .setStatusMessage("ERROR")
          .end()
        );
    }).setHandler(h ->
      testRequest(client, HttpMethod.GET, "/pets")
        .asserts(statusCode(500), statusMessage("ERROR"))
        .send(testContext, checkpoint)
    );
  }

  @Test
  public void mountMultipleHandlers(Vertx vertx, VertxTestContext testContext) {
    Checkpoint checkpoint = testContext.checkpoint();
    loadFactoryAndStartServer(vertx, "src/test/resources/specs/router_factory_test.yaml", testContext, routerFactory -> {
      routerFactory.setOptions(HANDLERS_TESTS_OPTIONS);

      routerFactory
        .operation("listPets")
        .handler(routingContext ->
          routingContext.put("message", "A").next()
        )
        .handler(routingContext -> {
          routingContext.put("message", routingContext.get("message") + "B");
          routingContext.fail(500);
        });
      routerFactory
        .operation("listPets")
        .failureHandler(routingContext ->
          routingContext.put("message", routingContext.get("message") + "E").next()
        )
        .failureHandler(routingContext ->
          routingContext
            .response()
            .setStatusCode(500)
            .setStatusMessage(routingContext.get("message"))
            .end()
        );
    }).setHandler(h ->
      testRequest(client, HttpMethod.GET, "/pets")
        .asserts(statusCode(500), statusMessage("ABE"))
        .send(testContext, checkpoint)
    );
  }

  @Test
  public void mountSecurityHandlers(Vertx vertx, VertxTestContext testContext) {
    Checkpoint checkpoint = testContext.checkpoint();
    loadFactoryAndStartServer(vertx, "src/test/resources/specs/router_factory_test.yaml", testContext, routerFactory -> {
      routerFactory.setOptions(new RouterFactoryOptions().setRequireSecurityHandlers(true));

      routerFactory.operation("listPetsSecurity").handler(routingContext -> routingContext
        .response()
        .setStatusCode(200)
        .setStatusMessage(routingContext.get("first_level") + "-" +
          routingContext.get("second_level") + "-" + routingContext.get("third_level_one") +
          "-" + routingContext.get("third_level_two") + "-Done")
        .end());

      routerFactory.securityHandler("api_key",
        routingContext -> routingContext.put("first_level", "User").next()
      );

      routerFactory.securityHandler("second_api_key", "moderator",
        routingContext -> routingContext.put("second_level", "Moderator").next()
      );

      routerFactory.securityHandler("third_api_key", "admin",
        routingContext -> routingContext.put("third_level_one", "Admin").next()
      );

      routerFactory.securityHandler("third_api_key", "useless",
        routingContext -> routingContext.put("third_level_one", "Wrong!").next()
      );

      routerFactory.securityHandler("third_api_key", "super_admin",
        routingContext -> routingContext.put("third_level_two", "SuperAdmin").next()
      );
    }).setHandler(h ->
      testRequest(client, HttpMethod.GET, "/pets_security_test")
        .asserts(statusCode(200), statusMessage("User-Moderator-Admin-SuperAdmin-Done"))
        .send(testContext, checkpoint)
    );
  }

  @Test
  public void mountMultipleSecurityHandlers(Vertx vertx, VertxTestContext testContext) {
    Checkpoint checkpoint = testContext.checkpoint();

    loadFactoryAndStartServer(vertx, "src/test/resources/specs/router_factory_test.yaml", testContext, routerFactory -> {
      routerFactory.setOptions(new RouterFactoryOptions().setRequireSecurityHandlers(true));

      routerFactory.operation("listPetsSecurity").handler(routingContext ->
        routingContext
          .response()
          .setStatusCode(200)
          .setStatusMessage("First handler: " + routingContext.get("firstHandler") + ", Second handler: " + routingContext.get("secondHandler") + ", Second api key: " + routingContext.get("secondApiKey") + ", Third api key: " + routingContext.get("thirdApiKey"))
          .end()
      );

      routerFactory.securityHandler("api_key", routingContext -> routingContext.put("firstHandler", "OK").next());
      routerFactory.securityHandler("api_key", routingContext -> routingContext.put("secondHandler", "OK").next());
      routerFactory.securityHandler("second_api_key", routingContext -> routingContext.put("secondApiKey", "OK").next());
      routerFactory.securityHandler("third_api_key", routingContext -> routingContext.put("thirdApiKey", "OK").next());

    }).setHandler(h ->
      testRequest(client, HttpMethod.GET, "/pets_security_test")
        .asserts(statusCode(200), statusMessage("First handler: OK, Second handler: OK, Second api key: OK, Third api key: OK"))
        .send(testContext, checkpoint)
    );
  }

  @Test
  public void requireSecurityHandler(Vertx vertx, VertxTestContext testContext) {
    RouterFactory.create(vertx, "src/test/resources/specs/router_factory_test.yaml", testContext.succeeding(routerFactory -> {
      routerFactory.setOptions(new RouterFactoryOptions().setRequireSecurityHandlers(true));

      routerFactory.operation("listPets").handler(routingContext -> routingContext
        .response()
        .setStatusCode(200)
        .setStatusMessage(routingContext.get("message") + "OK")
        .end()
      );

      testContext.verify(() ->
        assertThatCode(routerFactory::createRouter)
          .isInstanceOfSatisfying(RouterFactoryException.class, rfe ->
            assertThat(rfe.type())
              .isEqualTo(RouterFactoryException.ErrorType.MISSING_SECURITY_HANDLER)
          )
      );

      routerFactory.securityHandler("api_key", RoutingContext::next);
      routerFactory.securityHandler("second_api_key", RoutingContext::next);
      routerFactory.securityHandler("third_api_key", RoutingContext::next);

      testContext.verify(() ->
        assertThatCode(routerFactory::createRouter)
          .doesNotThrowAnyException()
      );
      testContext.completeNow();

    }));

  }


  @Test
  public void testGlobalSecurityHandler(Vertx vertx, VertxTestContext testContext) {
    final Handler<RoutingContext> handler = routingContext -> {
      routingContext
        .response()
        .setStatusCode(200)
        .setStatusMessage(((routingContext.get("message") != null) ? routingContext.get("message") + "-OK" : "OK"))
        .end();
    };

    Checkpoint checkpoint = testContext.checkpoint(3);

    loadFactoryAndStartServer(vertx, "src/test/resources/specs/global_security_test.yaml", testContext, routerFactory -> {
      routerFactory.setOptions(new RouterFactoryOptions().setRequireSecurityHandlers(true));

      routerFactory.operation("listPetsWithoutSecurity").handler(handler);
      routerFactory.operation("listPetsWithOverride").handler(handler);
      routerFactory.operation("listPetsWithoutOverride").handler(handler);

      testContext.verify(() ->
        assertThatCode(routerFactory::createRouter)
          .isInstanceOfSatisfying(RouterFactoryException.class, rfe ->
            assertThat(rfe.type())
              .isEqualTo(RouterFactoryException.ErrorType.MISSING_SECURITY_HANDLER)
          )
      );

      routerFactory.securityHandler("global_api_key",
        routingContext -> routingContext.put("message", "Global").next()
      );

      routerFactory.securityHandler("api_key",
        routingContext -> routingContext.put("message", "Local").next()
      );

    }).setHandler(h -> {
      testRequest(client, HttpMethod.GET, "/petsWithoutSecurity")
        .asserts(statusCode(200), statusMessage("OK"))
        .send(testContext, checkpoint);
      testRequest(client, HttpMethod.GET, "/petsWithOverride")
        .asserts(statusCode(200), statusMessage("Local-OK"))
        .send(testContext, checkpoint);
      testRequest(client, HttpMethod.GET, "/petsWithoutOverride")
        .asserts(statusCode(200), statusMessage("Global-OK"))
        .send(testContext, checkpoint);
    });
  }

  @Test
  public void notRequireSecurityHandler(Vertx vertx, VertxTestContext testContext) {
    RouterFactory.create(vertx, "src/test/resources/specs/router_factory_test.yaml",
      routerFactoryAsyncResult -> {
        RouterFactory routerFactory = routerFactoryAsyncResult.result();

        routerFactory.setOptions(new RouterFactoryOptions().setRequireSecurityHandlers(false));

        routerFactory.operation("listPets").handler(routingContext -> routingContext
          .response()
          .setStatusCode(200)
          .setStatusMessage(routingContext.get("message") + "OK")
          .end()
        );

        testContext.verify(() -> assertThatCode(routerFactory::createRouter).doesNotThrowAnyException());

        testContext.completeNow();
      });
  }

  @Test
  public void mountNotImplementedHandler(Vertx vertx, VertxTestContext testContext) {
    Checkpoint checkpoint = testContext.checkpoint();
    loadFactoryAndStartServer(vertx, "src/test/resources/specs/router_factory_test.yaml", testContext, routerFactory -> {
      routerFactory.setOptions(
        new RouterFactoryOptions()
          .setRequireSecurityHandlers(false)
          .setMountNotImplementedHandler(true)
      );
      routerFactory.operation("showPetById").handler(RoutingContext::next);
    }).setHandler(h ->
      testRequest(client, HttpMethod.GET, "/pets")
        .asserts(statusCode(501), statusMessage("Not Implemented"))
        .send(testContext, checkpoint)
    );
  }

  @Test
  public void mountNotAllowedHandler(Vertx vertx, VertxTestContext testContext) {
    Checkpoint checkpoint = testContext.checkpoint();

    loadFactoryAndStartServer(vertx, "src/test/resources/specs/router_factory_test.yaml", testContext, routerFactory -> {
      routerFactory.setOptions(
        new RouterFactoryOptions()
          .setRequireSecurityHandlers(false)
          .setMountNotImplementedHandler(true)
      );

      routerFactory.operation("deletePets").handler(RoutingContext::next);
      routerFactory.operation("createPets").handler(RoutingContext::next);
    }).setHandler(rc ->
      testRequest(client, HttpMethod.GET, "/pets")
        .asserts(statusCode(405), statusMessage("Method Not Allowed"))
        .asserts(resp ->
          assertThat(new HashSet<>(Arrays.asList(resp.getHeader("Allow").split(Pattern.quote(", ")))))
            .isEqualTo(Stream.of("DELETE", "POST").collect(Collectors.toSet()))
        ).send(testContext, checkpoint)
    );
  }

  @Test
  public void addGlobalHandlersTest(Vertx vertx, VertxTestContext testContext) {
    Checkpoint checkpoint = testContext.checkpoint();

    loadFactoryAndStartServer(vertx, "src/test/resources/specs/router_factory_test.yaml", testContext, routerFactory -> {
      routerFactory.setOptions(new RouterFactoryOptions().setRequireSecurityHandlers(false));

      routerFactory.rootHandler(rc -> {
        rc.response().putHeader("header-from-global-handler", "some dummy data");
        rc.next();
      });
      routerFactory.rootHandler(rc -> {
        rc.response().putHeader("header-from-global-handler", "some more dummy data");
        rc.next();
      });

      routerFactory.operation("listPets").handler(routingContext -> routingContext
        .response()
        .setStatusCode(200)
        .setStatusMessage("OK")
        .end());
    }).setHandler(h ->
      testRequest(client, HttpMethod.GET, "/pets")
        .asserts(statusCode(200))
        .asserts(headerResponse("header-from-global-handler", "some more dummy data"))

    );
  }

  @Test
  public void exposeConfigurationTest(Vertx vertx, VertxTestContext testContext) {
    Checkpoint checkpoint = testContext.checkpoint();

    loadFactoryAndStartServer(vertx, "src/test/resources/specs/router_factory_test.yaml", testContext, routerFactory -> {
        routerFactory.setOptions(new RouterFactoryOptions().setRequireSecurityHandlers(false).setOperationModelKey("fooBarKey"));

        routerFactory.operation("listPets").handler(routingContext -> {
          JsonObject operation = routingContext.get("fooBarKey");

          routingContext
            .response()
            .setStatusCode(200)
            .setStatusMessage(operation.getString("operationId"))
            .end();
        });
    }).setHandler(h ->
        testRequest(client, HttpMethod.GET, "/pets")
          .asserts(statusCode(200), statusMessage("listPets"))
          .send(testContext, checkpoint)
    );
  }

  @Test
  public void consumesTest(Vertx vertx, VertxTestContext testContext) {
    Checkpoint checkpoint = testContext.checkpoint(3);

    loadFactoryAndStartServer(vertx, "src/test/resources/specs/produces_consumes_test.yaml", testContext, routerFactory -> {
        routerFactory.setOptions(new RouterFactoryOptions().setMountNotImplementedHandler(false));

        routerFactory.operation("consumesTest").handler(routingContext -> {
          RequestParameters params = routingContext.get("parsedParameters");
          if (params.body() != null && params.body().isJsonObject()) {
            routingContext
              .response()
              .setStatusCode(200)
              .putHeader("Content-Type", "application/json")
              .end(params.body().getJsonObject().encode());
          } else {
            routingContext
              .response()
              .setStatusCode(200)
              .end();
          }
        });
    }).setHandler(h -> {
      JsonObject obj = new JsonObject().put("name", "francesco");
      testRequest(client, HttpMethod.POST, "/consumesTest")
        .asserts(statusCode(200))
        .asserts(jsonBodyResponse(obj))
        .sendJson(obj, testContext, checkpoint);

      MultiMap form = MultiMap.caseInsensitiveMultiMap();
      form.add("name", "francesco");
      testRequest(client, HttpMethod.POST, "/consumesTest")
        .asserts(statusCode(200))
        .asserts(jsonBodyResponse(obj))
        .sendURLEncodedForm(form, testContext, checkpoint);

      MultipartForm multipartForm = MultipartForm.create();
      form.add("name", "francesco");
      testRequest(client, HttpMethod.POST, "/consumesTest")
        .asserts(statusCode(200))
        .asserts(jsonBodyResponse(obj))
        .sendMultipartForm(multipartForm, testContext, checkpoint);
    });
  }

  @Test
  public void producesTest(Vertx vertx, VertxTestContext testContext) {
    Checkpoint checkpoint = testContext.checkpoint(2);

    loadFactoryAndStartServer(vertx, "src/test/resources/specs/produces_consumes_test.yaml", testContext, routerFactory -> {
        routerFactory.setOptions(new RouterFactoryOptions().setMountNotImplementedHandler(false));

        routerFactory.operation("producesTest").handler(routingContext -> {
          if (((RequestParameters) routingContext.get("parsedParameters")).queryParameter("fail").getBoolean())
            routingContext
              .response()
              .putHeader("content-type", "text/plain")
              .setStatusCode(500)
              .end("Hate it");
          else
            routingContext.response().setStatusCode(200).end("{}"); // ResponseContentTypeHandler does the job for me
        });
    }).setHandler(h -> {
      String acceptableContentTypes = String.join(", ", "application/json", "text/plain");
      testRequest(client, HttpMethod.GET, "/producesTest")
        .transformations(header("Accept", acceptableContentTypes))
        .asserts(statusCode(200), headerResponse("Content-type", "application/json"))
        .send(testContext, checkpoint);
      testRequest(client, HttpMethod.GET, "/producesTest?fail=true")
        .transformations(header("Accept", acceptableContentTypes))
        .asserts(statusCode(500), headerResponse("Content-type", "text/plain"))
        .send(testContext, checkpoint);
    });
  }

  @Test
  public void mountHandlersOrderTest(Vertx vertx, VertxTestContext testContext) {
    Checkpoint checkpoint = testContext.checkpoint(2);

    loadFactoryAndStartServer(vertx, "src/test/resources/specs/test_order_spec.yaml", testContext, routerFactory -> {
        routerFactory.setOptions(new RouterFactoryOptions().setMountNotImplementedHandler(false));

        routerFactory.operation("showSpecialProduct").handler(routingContext ->
          routingContext.response().setStatusMessage("special").end()
        );

        routerFactory.operation("showProductById").handler(routingContext -> {
          RequestParameters params = routingContext.get("parsedParameters");
          routingContext.response().setStatusMessage(params.pathParameter("id").getInteger().toString()).end();
        });

        testContext.completeNow();
    }).setHandler(h -> {
      testRequest(client, HttpMethod.GET, "/product/special")
        .asserts(statusCode(200), statusMessage("special"))
        .send(testContext, checkpoint);
      testRequest(client, HttpMethod.GET, "/product/123")
        .asserts(statusCode(200), statusMessage("123"))
        .send(testContext, checkpoint);
    });
  }

  @Test
  public void mountHandlerEncodedTest(Vertx vertx, VertxTestContext testContext) {
    Checkpoint checkpoint = testContext.checkpoint();

    loadFactoryAndStartServer(vertx, "src/test/resources/specs/router_factory_test.yaml", testContext, routerFactory -> {
        routerFactory.setOptions(HANDLERS_TESTS_OPTIONS);

        routerFactory.operation("encodedParamTest").handler(routingContext -> {
          RequestParameters params = routingContext.get("parsedParameters");
          assertThat(params.pathParameter("p1").toString()).isEqualTo("a:b");
          assertThat(params.queryParameter("p2").toString()).isEqualTo("a:b");
          routingContext
            .response()
            .setStatusCode(200)
            .setStatusMessage(params.pathParameter("p1").toString())
            .end();
        });

        testContext.completeNow();
    }).setHandler(h ->
      testRequest(client, HttpMethod.GET, "/foo/a%3Ab?p2=a%3Ab")
        .asserts(statusCode(200), statusMessage("a:b"))
        .send(testContext, checkpoint)
    );
  }

  /**
   * Tests that user can supply customised BodyHandler
   *
   * @throws Exception
   */
  @Test
  public void customBodyHandlerTest(Vertx vertx, VertxTestContext testContext) {
    RouterFactory.create(vertx, "src/test/resources/specs/upload_test.yaml", testContext.succeeding(routerFactory -> {
      routerFactory.setOptions(new RouterFactoryOptions().setRequireSecurityHandlers(false));

      BodyHandler bodyHandler = BodyHandler.create("my-uploads");

      routerFactory.bodyHandler(bodyHandler);

      routerFactory.operation("upload").handler(routingContext -> routingContext.response().setStatusCode(201).end());

      testContext.verify(() -> {
        assertThat(routerFactory.createRouter().getRoutes().get(0)).isSameAs(bodyHandler);
      });

      testContext.completeNow();

    }));
  }

  @Test
  public void testSharedRequestBody(Vertx vertx, VertxTestContext testContext) {
    Checkpoint checkpoint = testContext.checkpoint(2);

    loadFactoryAndStartServer(vertx, "src/test/resources/specs/shared_request_body.yaml", testContext, routerFactory -> {
        routerFactory.setOptions(HANDLERS_TESTS_OPTIONS);

        final Handler<RoutingContext> handler = routingContext -> {
          RequestParameters params = routingContext.get("parsedParameters");
          RequestParameter body = params.body();
          JsonObject jsonBody = body.getJsonObject();
          routingContext
            .response()
            .setStatusCode(200)
            .setStatusMessage("OK")
            .putHeader("Content-Type", "application/json")
            .end(jsonBody.toBuffer());
        };

        routerFactory.operation("thisWayWorks").handler(handler);
        routerFactory.operation("thisWayBroken").handler(handler);
    }).setHandler(h -> {
      JsonObject obj = new JsonObject().put("id", "aaa").put("name", "bla");
      testRequest(client, HttpMethod.POST, "/v1/working")
        .asserts(statusCode(200))
        .asserts(jsonBodyResponse(obj))
        .sendJson(obj, testContext, checkpoint);
      testRequest(client, HttpMethod.POST, "/v1/notworking")
        .asserts(statusCode(200))
        .asserts(jsonBodyResponse(obj))
        .sendJson(obj, testContext, checkpoint);
    });
  }

  @Test
  public void pathResolverShouldNotCreateRegex(Vertx vertx, VertxTestContext testContext) {
    RouterFactory.create(vertx, "src/test/resources/specs/produces_consumes_test.yaml", testContext.succeeding(routerFactory -> {
        routerFactory.setOptions(new RouterFactoryOptions().setMountNotImplementedHandler(false));

        routerFactory.operation("consumesTest").handler(routingContext ->
          routingContext
            .response()
            .setStatusCode(200)
            .setStatusMessage("OK")
        );

        testContext.verify(() ->
          assertThat(routerFactory.createRouter().getRoutes())
            .extracting(Route::getPath)
            .anyMatch("/consumesTest"::equals)
        );

        testContext.completeNow();
    }));
  }

  @Test
  public void testJsonEmptyBody(Vertx vertx, VertxTestContext testContext) {
    Checkpoint checkpoint = testContext.checkpoint(1);

    loadFactoryAndStartServer(vertx, "src/test/resources/specs/router_factory_test.yaml", testContext, routerFactory -> {
        routerFactory.setOptions(new RouterFactoryOptions().setRequireSecurityHandlers(false).setMountNotImplementedHandler(false));

        routerFactory.operation("jsonEmptyBody").handler(routingContext -> {
          RequestParameters params = routingContext.get("parsedParameters");
          RequestParameter body = params.body();
          routingContext
            .response()
            .setStatusCode(200)
            .setStatusMessage("OK")
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("bodyEmpty", body == null).toBuffer());
        });

        testContext.completeNow();
    }).setHandler(h ->
      testRequest(client, HttpMethod.POST, "/jsonBody/empty")
        .asserts(statusCode(200), jsonBodyResponse(new JsonObject().put("bodyEmpty", true)))
        .send(testContext, checkpoint)
    );
  }
}
