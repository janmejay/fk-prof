package fk.prof.backend.http;

import fk.prof.backend.ConfigManager;
import fk.prof.backend.exception.HttpFailure;
import fk.prof.backend.model.association.BackendAssociationStore;
import fk.prof.backend.model.policy.PolicyStore;
import fk.prof.backend.proto.BackendDTO;
import fk.prof.backend.util.ProtoUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import recording.Recorder;

import java.io.IOException;

public class LeaderHttpVerticle extends AbstractVerticle {
  private final ConfigManager configManager;
  private final BackendAssociationStore backendAssociationStore;
  private final PolicyStore policyStore;

  public LeaderHttpVerticle(ConfigManager configManager,
                            BackendAssociationStore backendAssociationStore,
                            PolicyStore policyStore) {
    this.configManager = configManager;
    this.backendAssociationStore = backendAssociationStore;
    this.policyStore = policyStore;
  }

  @Override
  public void start(Future<Void> fut) {
    Router router = setupRouting();
    vertx.createHttpServer(HttpHelper.getHttpServerOptions(configManager.getLeaderHttpServerConfig()))
        .requestHandler(router::accept)
        .listen(configManager.getLeaderHttpPort(),
            http -> completeStartup(http, fut));
  }

  private Router setupRouting() {
    Router router = Router.router(vertx);
    router.route().handler(LoggerHandler.create());

    router.post(ApiPathConstants.LEADER_POST_LOAD)
        .handler(BodyHandler.create().setBodyLimit(64));
    router.post(ApiPathConstants.LEADER_POST_LOAD)
        .handler(this::handlePostLoad);

    router.put(ApiPathConstants.LEADER_PUT_ASSOCIATION)
        .handler(BodyHandler.create().setBodyLimit(1024 * 10));
    router.put(ApiPathConstants.LEADER_PUT_ASSOCIATION)
        .handler(this::handlePutAssociation);

    String apiPathForGetWork = ApiPathConstants.LEADER_GET_WORK + "/:appId/:clusterId/:procName";
    router.get(apiPathForGetWork)
        .handler(BodyHandler.create().setBodyLimit(1024 * 10));
    router.get(apiPathForGetWork)
        .handler(this::handleGetWork);

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
      BackendDTO.LoadReportRequest payload = ProtoUtil.buildProtoFromBuffer(BackendDTO.LoadReportRequest.parser(), context.getBody());
      backendAssociationStore.reportBackendLoad(payload).setHandler(ar -> {
        if(ar.succeeded()) {
          try {
            Buffer responseBuffer = ProtoUtil.buildBufferFromProto(ar.result());
            context.response().end(responseBuffer);
          } catch (IOException ex) {
            HttpFailure httpFailure = HttpFailure.failure(ar.cause());
            HttpHelper.handleFailure(context, httpFailure);
          }
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

  private void handlePutAssociation(RoutingContext context) {
    try {
      Recorder.ProcessGroup processGroup = ProtoUtil.buildProtoFromBuffer(Recorder.ProcessGroup.parser(), context.getBody());
      backendAssociationStore.getAssociatedBackend(processGroup).setHandler(ar -> {
        //TODO: Evaluate if this lambda can be extracted out as a static variable/function if this is repetitive across the codebase
        if(ar.succeeded()) {
          try {
            context.response().end(ProtoUtil.buildBufferFromProto(ar.result()));
          } catch (Exception ex) {
            HttpFailure httpFailure = HttpFailure.failure(ex);
            HttpHelper.handleFailure(context, httpFailure);
          }
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

  private void handleGetWork(RoutingContext context) {
    try {
      String appId = context.request().getParam("appId");
      String clusterId = context.request().getParam("clusterId");
      String procName = context.request().getParam("procName");
      Recorder.ProcessGroup processGroup = Recorder.ProcessGroup.newBuilder().setAppId(appId).setCluster(clusterId).setProcName(procName).build();

      BackendDTO.WorkProfile workProfile = this.policyStore.get(processGroup);
      context.response().end(ProtoUtil.buildBufferFromProto(workProfile));
    } catch (Exception ex) {
      HttpFailure httpFailure = HttpFailure.failure(ex);
      HttpHelper.handleFailure(context, httpFailure);
    }
  }

}
