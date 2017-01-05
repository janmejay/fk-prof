package fk.prof.backend.verticles.http;

import fk.prof.backend.exception.HttpFailure;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class HttpHelper {
  public static void handleFailure(RoutingContext context, HttpFailure exception) {
    final JsonObject error = new JsonObject()
        .put("timestamp", System.nanoTime())
        .put("status", exception.getStatusCode())
        .put("error", HttpResponseStatus.valueOf(exception.getStatusCode()).reasonPhrase())
        .put("path", context.normalisedPath());

    if (exception.getMessage() != null) {
      error.put("message", exception.getMessage());
    }

    if (!context.response().ended()) {
      context.response().setStatusCode(exception.getStatusCode());
      context.response().end(error.encode());
    }
  }
}
