package fk.prof.backend.http;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.google.common.primitives.Ints;
import fk.prof.aggregation.ProcessGroupTag;
import fk.prof.backend.ConfigManager;
import fk.prof.backend.aggregator.AggregationWindow;
import fk.prof.backend.exception.AggregationFailure;
import fk.prof.backend.exception.BadRequestException;
import fk.prof.backend.exception.HttpFailure;
import fk.prof.backend.model.assignment.ProcessGroupContextForPolling;
import fk.prof.backend.model.assignment.ProcessGroupDiscoveryContext;
import fk.prof.backend.model.election.LeaderReadContext;
import fk.prof.backend.proto.BackendDTO;
import fk.prof.backend.request.profile.RecordedProfileProcessor;
import fk.prof.backend.request.profile.impl.SharedMapBasedSingleProcessingOfProfileGate;
import fk.prof.backend.model.aggregation.AggregationWindowDiscoveryContext;
import fk.prof.backend.util.ProtoUtil;
import fk.prof.backend.util.proto.RecorderProtoUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import recording.Recorder;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BackendHttpVerticle extends AbstractVerticle {
  private static Logger logger = LoggerFactory.getLogger(BackendHttpVerticle.class);

  private final ConfigManager configManager;
  private final LeaderReadContext leaderReadContext;
  private final AggregationWindowDiscoveryContext aggregationWindowDiscoveryContext;
  private final ProcessGroupDiscoveryContext processGroupDiscoveryContext;
  private final int backendHttpPort;
  private final String ipAddress;
  private final int backendVersion;

  private LocalMap<Long, Boolean> workIdsInPipeline;
  private ProfHttpClient httpClient;

  private MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(ConfigManager.METRIC_REGISTRY);
  private Counter ctrLeaderSelfReq = metricRegistry.counter(MetricRegistry.name(BackendHttpVerticle.class, "req", "ldr", "self"));
  private Counter ctrLeaderUnknownReq = metricRegistry.counter(MetricRegistry.name(BackendHttpVerticle.class, "req", "ldr", "unknown"));

  public BackendHttpVerticle(ConfigManager configManager,
                             LeaderReadContext leaderReadContext,
                             AggregationWindowDiscoveryContext aggregationWindowDiscoveryContext,
                             ProcessGroupDiscoveryContext processGroupDiscoveryContext) {
    this.configManager = configManager;
    this.backendHttpPort = configManager.getBackendHttpPort();
    this.ipAddress = configManager.getIPAddress();
    this.backendVersion = configManager.getBackendVersion();

    this.leaderReadContext = leaderReadContext;
    this.aggregationWindowDiscoveryContext = aggregationWindowDiscoveryContext;
    this.processGroupDiscoveryContext = processGroupDiscoveryContext;
  }

  @Override
  public void start(Future<Void> fut) {
    JsonObject httpClientConfig = configManager.getHttpClientConfig();
    httpClient = ProfHttpClient.newBuilder().setConfig(httpClientConfig).build(vertx);

    Router router = setupRouting();
    workIdsInPipeline = vertx.sharedData().getLocalMap("WORK_ID_PIPELINE");
    vertx.createHttpServer(HttpHelper.getHttpServerOptions(configManager.getBackendHttpServerConfig()))
        .requestHandler(router::accept)
        .listen(configManager.getBackendHttpPort(), http -> completeStartup(http, fut));
  }

  private Router setupRouting() {
    Router router = Router.router(vertx);
    router.route().handler(LoggerHandler.create());

    HttpHelper.attachHandlersToRoute(router, HttpMethod.POST, ApiPathConstants.AGGREGATOR_POST_PROFILE,
        this::handlePostProfile);

    HttpHelper.attachHandlersToRoute(router, HttpMethod.POST, ApiPathConstants.BACKEND_POST_ASSOCIATION,
        BodyHandler.create().setBodyLimit(1024 * 10), this::handlePostAssociation);

    HttpHelper.attachHandlersToRoute(router, HttpMethod.POST, ApiPathConstants.BACKEND_POST_POLL,
        BodyHandler.create().setBodyLimit(1024 * 100), this::handlePostPoll);

    HttpHelper.attachHandlersToRoute(router, HttpMethod.GET, ApiPathConstants.BACKEND_HEALTHCHECK, this::handleGetHealthCheck);

    return router;
  }

  private void completeStartup(AsyncResult<HttpServer> http, Future<Void> fut) {
    if (http.succeeded()) {
      fut.complete();
    } else {
      fut.fail(http.cause());
    }
  }

  private void handlePostProfile(RoutingContext context) {
    RecordedProfileProcessor profileProcessor = new RecordedProfileProcessor(
        context,
        aggregationWindowDiscoveryContext,
        new SharedMapBasedSingleProcessingOfProfileGate(workIdsInPipeline),
        config().getJsonObject("parser").getInteger("recordingheader.max.bytes", 1024),
        config().getJsonObject("parser").getInteger("parser.wse.max.bytes", 1024 * 1024));

    context.response().endHandler(v -> {
      try {
        profileProcessor.close();
      } catch (Exception ex) {
        logger.error("Unexpected error when closing profile: {}", ex, profileProcessor);
      }
    });

    context.request()
        .handler(profileProcessor)
        .exceptionHandler(th -> {
          HttpFailure httpFailure = HttpFailure.failure(th);
          HttpHelper.handleFailure(context, httpFailure);
        })
        .endHandler(v -> {
          try {
            if (!context.response().ended()) {
              if(profileProcessor.isProcessed()) {
                context.response().end();
              } else {
                throw new AggregationFailure("Incomplete profile received: " + profileProcessor);
              }
            }
          } catch (Exception ex) {
            HttpFailure httpFailure = HttpFailure.failure(ex);
            HttpHelper.handleFailure(context, httpFailure);
          }
        });
  }

  private void handlePostPoll(RoutingContext context) {
    try {
      Recorder.PollReq pollReq = ProtoUtil.buildProtoFromBuffer(Recorder.PollReq.parser(), context.getBody());
      if(logger.isDebugEnabled()) {
        logger.debug("Poll request: " + RecorderProtoUtil.pollReqCompactRepr(pollReq));
      }

      Recorder.ProcessGroup processGroup = RecorderProtoUtil.mapRecorderInfoToProcessGroup(pollReq.getRecorderInfo());
      ProcessGroupTag processGroupTag = new ProcessGroupTag(processGroup.getAppId(), processGroup.getCluster(), processGroup.getProcName());
      Meter mtrAssocMiss = metricRegistry.meter(MetricRegistry.name(BackendHttpVerticle.class, "poll.assoc", "miss", processGroupTag.toString()));
      Counter ctrWinMiss = metricRegistry.counter(MetricRegistry.name(BackendHttpVerticle.class, "poll.window", "miss", processGroupTag.toString()));

      ProcessGroupContextForPolling processGroupContextForPolling = this.processGroupDiscoveryContext.getProcessGroupContextForPolling(processGroup);
      if(processGroupContextForPolling == null) {
        mtrAssocMiss.mark();
        throw new BadRequestException("Process group " + RecorderProtoUtil.processGroupCompactRepr(processGroup) + " not associated with the backend");
      }

      Recorder.WorkAssignment nextWorkAssignment = processGroupContextForPolling.getWorkAssignment(pollReq);
      if(nextWorkAssignment != null) {
        AggregationWindow aggregationWindow = aggregationWindowDiscoveryContext.getAssociatedAggregationWindow(nextWorkAssignment.getWorkId());
        if (aggregationWindow == null) {
          ctrWinMiss.inc();
          throw new BadRequestException(String.format("workId=%d not found, cannot associate recorder info with aggregated profile. aborting send of work assignment",
              nextWorkAssignment.getWorkId()));
        }
        aggregationWindow.updateRecorderInfo(nextWorkAssignment.getWorkId(), pollReq.getRecorderInfo());
      }

      Recorder.PollRes.Builder pollResBuilder = Recorder.PollRes.newBuilder()
          .setControllerVersion(backendVersion)
          .setControllerId(Ints.fromByteArray(ipAddress.getBytes("UTF-8")))
          .setLocalTime(nextWorkAssignment == null
              ? LocalDateTime.now(Clock.systemUTC()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
              : nextWorkAssignment.getIssueTime());
      if(nextWorkAssignment != null) {
        pollResBuilder.setAssignment(nextWorkAssignment);
      }

      Recorder.PollRes pollRes = pollResBuilder.build();
      if(logger.isDebugEnabled()) {
        logger.debug("Poll response: " + RecorderProtoUtil.pollResCompactRepr(pollRes));
      }
      context.response().end(ProtoUtil.buildBufferFromProto(pollRes));
    } catch (Exception ex) {
      HttpFailure httpFailure = HttpFailure.failure(ex);
      HttpHelper.handleFailure(context, httpFailure);
    }
  }

  // /association API is requested over ELB, routed to some backend which in turns proxies it to a leader
  private void handlePostAssociation(RoutingContext context) {
    BackendDTO.LeaderDetail leaderDetail = verifyLeaderAvailabilityOrFail(context.response());
    if (leaderDetail != null) {
      try {
        Recorder.RecorderInfo recorderInfo = ProtoUtil.buildProtoFromBuffer(Recorder.RecorderInfo.parser(), context.getBody());
        Recorder.ProcessGroup processGroup = RecorderProtoUtil.mapRecorderInfoToProcessGroup(recorderInfo);
        ProcessGroupContextForPolling processGroupContextForPolling = this.processGroupDiscoveryContext.getProcessGroupContextForPolling(processGroup);
        if(processGroupContextForPolling != null) {
          Recorder.AssignedBackend assignedBackend = Recorder.AssignedBackend.newBuilder().setHost(ipAddress).setPort(backendHttpPort).build();
          context.response().end(ProtoUtil.buildBufferFromProto(assignedBackend));
          return;
        }

        //Proxy request to leader if self(backend) is not associated with the recorder
        makeRequestPostAssociation(leaderDetail, recorderInfo).setHandler(ar -> {
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

  private BackendDTO.LeaderDetail verifyLeaderAvailabilityOrFail(HttpServerResponse response) {
    if (leaderReadContext.isLeader()) {
      ctrLeaderSelfReq.inc();
      response.setStatusCode(400).end("Leader refuses to respond to this request");
      return null;
    } else {
      BackendDTO.LeaderDetail leaderDetail = leaderReadContext.getLeader();
      if (leaderDetail == null) {
        ctrLeaderUnknownReq.inc();
        response.setStatusCode(503).putHeader("Retry-After", "10").end("Leader not elected yet");
        return null;
      } else {
        return leaderDetail;
      }
    }
  }

  private Future<ProfHttpClient.ResponseWithStatusTuple> makeRequestPostAssociation(BackendDTO.LeaderDetail leaderDetail, Recorder.RecorderInfo payload)
      throws IOException {
    Buffer payloadAsBuffer = ProtoUtil.buildBufferFromProto(payload);
    return httpClient.requestAsyncWithRetry(
        HttpMethod.POST,
        leaderDetail.getHost(), leaderDetail.getPort(), ApiPathConstants.LEADER_POST_ASSOCIATION,
        payloadAsBuffer);
  }

  private void handleGetHealthCheck(RoutingContext routingContext) {
    JsonObject response = new JsonObject();
    response.put("leader", leaderReadContext.getLeader().getHost() + ":" + leaderReadContext.getLeader().getPort());
    routingContext.response().setStatusCode(200).end(response.encode());
  }
}
