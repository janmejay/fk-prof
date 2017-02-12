package fk.prof.backend.verticles.http;

import fk.prof.backend.exception.HttpFailure;
import fk.prof.backend.util.ConfigurableHttpClient;
import fk.prof.backend.verticles.leader.election.LeaderDiscoveryStore;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import recording.Recorder;

public class LeaderProxyVerticle extends AbstractVerticle {
  private static Logger logger = LoggerFactory.getLogger(LeaderProxyVerticle.class);

  private JsonObject httpServerConfig;
  private JsonObject httpClientConfig;
  private LeaderDiscoveryStore leaderDiscoveryStore;

  private ConfigurableHttpClient httpClient;

  public LeaderProxyVerticle(JsonObject httpServerConfig, JsonObject httpClientConfig, LeaderDiscoveryStore leaderDiscoveryStore) {
    this.httpServerConfig = httpServerConfig;
    this.httpClientConfig = httpClientConfig;
    this.leaderDiscoveryStore = leaderDiscoveryStore;
  }

  @Override
  public void start(Future<Void> fut) {
    httpClient = ConfigurableHttpClient.newBuilder()
        .keepAlive(httpClientConfig.getBoolean("keepalive", true))
        .useCompression(httpClientConfig.getBoolean("compression", true))
        .setConnectTimeoutInMs(httpClientConfig.getInteger("connect.timeout.ms", 5000))
        .setIdleTimeoutInSeconds(httpClientConfig.getInteger("idle.timeout.secs", 10))
        .setMaxAttempts(httpClientConfig.getInteger("max.attempts", 3))
        .build(vertx);

    Router router = setupRouting();
    vertx.createHttpServer(HttpHelper.getHttpServerOptions(httpServerConfig))
        .requestHandler(router::accept)
        .listen(httpServerConfig.getInteger("port"),
            http -> completeStartup(http, fut));
  }

  private Router setupRouting() {
    Router router = Router.router(vertx);
    router.route().handler(LoggerHandler.create());

    router.post(ApiPathConstants.BACKEND_PUT_ASSOCIATION)
        .handler(BodyHandler.create().setBodyLimit(1024 * 10));
    router.post(ApiPathConstants.BACKEND_PUT_ASSOCIATION)
        .handler(this::handlePutAssociation);

    return router;
  }

  private void completeStartup(AsyncResult<HttpServer> http, Future<Void> fut) {
    if (http.succeeded()) {
      fut.complete();
    } else {
      fut.fail(http.cause());
    }
  }

  public void handlePutAssociation(RoutingContext context) {
    String leaderIPAddress = getLeaderAddressOrAbortRequest(context.response());
    if (leaderIPAddress != null) {
      try {
        //Deserialize to proto message to catch payload related errors early
        Recorder.ProcessGroup processGroup = Recorder.ProcessGroup.parseFrom(context.getBody().getBytes());
        makeRequestGetAssociation(leaderIPAddress, processGroup).setHandler(ar -> {
          if(ar.succeeded()) {
            context.response().setStatusCode(ar.result().getStatusCode());
            context.response().end(ar.result().getResponse());
          } else {
            HttpFailure httpFailure = HttpFailure.failure(ar.cause());
            HttpHelper.handleFailure(context, httpFailure);
          }
        });
      } catch (Exception ex) {
        HttpFailure httpFailure = HttpFailure.failure(ex);
        HttpHelper.handleFailure(context, httpFailure);
      }
    }
  }

  private String getLeaderAddressOrAbortRequest(HttpServerResponse response) {
    if (leaderDiscoveryStore.isLeader()) {
      response.setStatusCode(400).end("Leader refuses to respond to this request");
      return null;
    } else {
      String leaderIPAddress = leaderDiscoveryStore.getLeaderIPAddress();
      if (leaderIPAddress == null) {
        response.setStatusCode(503).putHeader("Retry-After", "10").end("Leader not elected yet");
        return null;
      } else {
        return leaderIPAddress;
      }
    }
  }

  private Future<ConfigurableHttpClient.ResponseWithStatusTuple> makeRequestGetAssociation(String leaderIPAddress, Recorder.ProcessGroup payload) {
    return httpClient.requestAsync(
        HttpMethod.POST,
        leaderIPAddress, httpServerConfig.getInteger("port"), ApiPathConstants.LEADER_PUT_ASSOCIATION,
        Buffer.buffer(payload.toByteArray()));
  }
}