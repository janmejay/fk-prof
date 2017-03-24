package fk.prof.backend.http;

import fk.prof.backend.exception.HttpFailure;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class HttpHelper {
  private static Logger logger = LoggerFactory.getLogger(HttpHelper.class);

  public static void handleFailure(RoutingContext context, HttpFailure exception) {
    long currentTimeMillis = System.currentTimeMillis();
    String requestPath = context.normalisedPath();

    final JsonObject error = new JsonObject()
        .put("timestamp", currentTimeMillis)
        .put("status", exception.getStatusCode())
        .put("error", HttpResponseStatus.valueOf(exception.getStatusCode()).reasonPhrase())
        .put("path", requestPath);

    if (exception.getMessage() != null) {
      error.put("message", exception.getMessage());
    }
    logger.error("Http error path={}, time={}, {}", exception, requestPath, currentTimeMillis);

    if (!context.response().ended()) {
      context.response().setStatusCode(exception.getStatusCode());
      context.response().end(error.encode());
    }
  }

  public static HttpServerOptions getHttpServerOptions(JsonObject httpServerConfig) {
    HttpServerOptions serverOptions = new HttpServerOptions();
    serverOptions
        .setCompressionSupported(true)
        .setIdleTimeout(httpServerConfig.getInteger("idle.timeout.secs", 120));
    return serverOptions;
  }

  @SafeVarargs
  public static void attachHandlersToRoute(Router router, HttpMethod method, String route, Handler<RoutingContext>... requestHandlers) {
    for(Handler<RoutingContext> handler: requestHandlers) {
      router.route(method, route).handler(handler);
    }
  }
}
