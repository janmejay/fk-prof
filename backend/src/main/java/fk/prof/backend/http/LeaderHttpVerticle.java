package fk.prof.backend.http;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import fk.prof.aggregation.ProcessGroupTag;
import fk.prof.backend.ConfigManager;
import fk.prof.backend.exception.HttpFailure;
import fk.prof.backend.model.assignment.BackendTag;
import fk.prof.backend.model.association.BackendAssociationStore;
import fk.prof.backend.model.policy.PolicyStore;
import fk.prof.backend.proto.BackendDTO;
import fk.prof.backend.util.ProtoUtil;
import fk.prof.backend.util.proto.RecorderProtoUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
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

  private final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(ConfigManager.METRIC_REGISTRY);

  private HttpServer server;

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
    server = vertx.createHttpServer(HttpHelper.getHttpServerOptions(configManager.getLeaderHttpServerConfig()))
        .requestHandler(router::accept)
        .listen(configManager.getLeaderHttpPort(),
            http -> completeStartup(http, fut));
  }

  @Override
  public void stop(Future<Void> stopFuture) throws Exception {
    server.close(stopFuture.completer());
  }

  private Router setupRouting() {
    Router router = Router.router(vertx);
    router.route().handler(LoggerHandler.create());

    HttpHelper.attachHandlersToRoute(router, HttpMethod.POST, ApiPathConstants.LEADER_POST_LOAD,
        BodyHandler.create().setBodyLimit(64), this::handlePostLoad);

    HttpHelper.attachHandlersToRoute(router, HttpMethod.POST, ApiPathConstants.LEADER_POST_ASSOCIATION,
        BodyHandler.create().setBodyLimit(1024 * 10), this::handlePostAssociation);

    String apiPathForGetWork = ApiPathConstants.LEADER_GET_WORK + "/:appId/:clusterId/:procName";
    HttpHelper.attachHandlersToRoute(router, HttpMethod.GET, apiPathForGetWork,
        BodyHandler.create().setBodyLimit(1024 * 100), this::handleGetWork);

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
      BackendTag backendTag = new BackendTag(payload.getIp(), payload.getPort());
      Meter mtrFailure = metricRegistry.meter(MetricRegistry.name(LeaderHttpVerticle.class, "load.report", "fail", backendTag.toString()));
      Meter mtrSuccess = metricRegistry.meter(MetricRegistry.name(LeaderHttpVerticle.class, "load.report", "success", backendTag.toString()));

      backendAssociationStore.reportBackendLoad(payload).setHandler(ar -> {
        mtrSuccess.mark();
        if(ar.succeeded()) {
          try {
            Buffer responseBuffer = ProtoUtil.buildBufferFromProto(ar.result());
            context.response().end(responseBuffer);
          } catch (IOException ex) {
            HttpFailure httpFailure = HttpFailure.failure(ar.cause());
            HttpHelper.handleFailure(context, httpFailure);
          }
        } else {
          mtrFailure.mark();
          HttpFailure httpFailure = HttpFailure.failure(ar.cause());
          HttpHelper.handleFailure(context, httpFailure);
        }
      });
    } catch (Exception ex) {
      HttpFailure httpFailure = HttpFailure.failure(ex);
      HttpHelper.handleFailure(context, httpFailure);
    }
  }

  private void handlePostAssociation(RoutingContext context) {
    try {
      Recorder.RecorderInfo recorderInfo = ProtoUtil.buildProtoFromBuffer(Recorder.RecorderInfo.parser(), context.getBody());
      Recorder.ProcessGroup processGroup = RecorderProtoUtil.mapRecorderInfoToProcessGroup(recorderInfo);

      ProcessGroupTag processGroupTag = new ProcessGroupTag(processGroup.getAppId(), processGroup.getCluster(), processGroup.getProcName());
      Meter mtrFailure = metricRegistry.meter(MetricRegistry.name(LeaderHttpVerticle.class, "assoc", "fail", processGroupTag.toString()));
      Meter mtrSuccess = metricRegistry.meter(MetricRegistry.name(LeaderHttpVerticle.class, "assoc", "success", processGroupTag.toString()));

      backendAssociationStore.associateAndGetBackend(processGroup).setHandler(ar -> {
        //TODO: Evaluate if this lambda can be extracted out as a static variable/function if this is repetitive across the codebase
        if(ar.succeeded()) {
          mtrSuccess.mark();
          try {
            context.response().end(ProtoUtil.buildBufferFromProto(ar.result()));
          } catch (Exception ex) {
            HttpFailure httpFailure = HttpFailure.failure(ex);
            HttpHelper.handleFailure(context, httpFailure);
          }
        } else {
          mtrFailure.mark();
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

      ProcessGroupTag processGroupTag = new ProcessGroupTag(appId, clusterId, procName);
      Meter mtrAssocMiss = metricRegistry.meter(MetricRegistry.name(LeaderHttpVerticle.class, "work.assoc", "miss", processGroupTag.toString()));
      Meter mtrPolicyMiss = metricRegistry.meter(MetricRegistry.name(LeaderHttpVerticle.class, "work.policy", "miss", processGroupTag.toString()));

      String backendIP = context.request().getParam("ip");
      int backendPort = Integer.valueOf(context.request().getParam("port"));
      Recorder.AssignedBackend callingBackend = Recorder.AssignedBackend.newBuilder().setHost(backendIP).setPort(backendPort).build();

      if(!callingBackend.equals(backendAssociationStore.getAssociatedBackend(processGroup))) {
        mtrAssocMiss.mark();
        context.response().setStatusCode(400);
        context.response().end("Calling backend=" + RecorderProtoUtil.assignedBackendCompactRepr(callingBackend) + " not assigned to process_group=" + RecorderProtoUtil.processGroupCompactRepr(processGroup));
      } else {
        BackendDTO.RecordingPolicy recordingPolicy = this.policyStore.get(processGroup);
        if (recordingPolicy == null) {
          mtrPolicyMiss.mark();
          context.response().setStatusCode(400);
          context.response().end("Policy not found for process_group" + RecorderProtoUtil.processGroupCompactRepr(processGroup));
        } else {
          context.response().end(ProtoUtil.buildBufferFromProto(recordingPolicy));
        }
      }
    } catch (Exception ex) {
      HttpFailure httpFailure = HttpFailure.failure(ex);
      HttpHelper.handleFailure(context, httpFailure);
    }
  }

}
