package fk.prof.backend.verticles.http;

import fk.prof.backend.exception.HttpFailure;
import fk.prof.backend.model.association.BackendAssociationStore;
import fk.prof.backend.model.association.ReportLoadPayload;
import fk.prof.backend.verticles.http.handler.ReportLoadHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import org.apache.curator.framework.CuratorFramework;

public class LeaderHttpVerticle extends AbstractVerticle {
  private static Logger logger = LoggerFactory.getLogger(LeaderHttpVerticle.class);

  private CuratorFramework curatorClient;
  private BackendAssociationStore backendAssociationStore;
  private int httpPort;

  public LeaderHttpVerticle(int httpPort, CuratorFramework curatorClient, BackendAssociationStore backendAssociationStore) {
    this.httpPort = httpPort;
    this.curatorClient = curatorClient;
    this.backendAssociationStore = backendAssociationStore;
  }

  @Override
  public void start(Future<Void> fut) {
    Router router = setupRouting();
    vertx.createHttpServer()
        .requestHandler(router::accept)
        .listen(httpPort,
            http -> completeStartup(http, fut));
  }

  private Router setupRouting() {
    Router router = Router.router(vertx);
    router.route().handler(LoggerHandler.create());
    router.post(ApiPathConstants.LEADER_POST_LOAD)
        .consumes("*/json")
        .produces("application/json")
        .handler(BodyHandler.create().setBodyLimit(64));
    router.post(ApiPathConstants.LEADER_POST_LOAD)
        .consumes("*/json")
        .produces("application/json")
        .handler(this::handlePostLoad);
    return router;
  }

  private void completeStartup(AsyncResult<HttpServer> http, Future<Void> fut) {
    if (http.succeeded()) {
      fut.complete();
    } else {
      fut.fail(http.cause());
    }
  }

  private void handlePostLoad(RoutingContext context) {
    try {
      ReportLoadPayload payload = Json.decodeValue(context.getBodyAsString(), ReportLoadPayload.class);
      context.response().end(Json.encode(payload));
    } catch (Exception ex) {
      HttpFailure httpFailure = HttpFailure.failure(ex);
      HttpHelper.handleFailure(context, httpFailure);
    }
  }
}
