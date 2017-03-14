package fk.prof.backend.http;

import com.google.common.primitives.Ints;
import fk.prof.backend.ConfigManager;
import fk.prof.backend.aggregator.AggregationWindow;
import fk.prof.backend.exception.HttpFailure;
import fk.prof.backend.model.assignment.ProcessGroupContextForPolling;
import fk.prof.backend.model.assignment.ProcessGroupDiscoveryContext;
import fk.prof.backend.model.election.LeaderReadContext;
import fk.prof.backend.proto.BackendDTO;
import fk.prof.backend.request.CompositeByteBufInputStream;
import fk.prof.backend.request.profile.RecordedProfileProcessor;
import fk.prof.backend.request.profile.impl.SharedMapBasedSingleProcessingOfProfileGate;
import fk.prof.backend.model.aggregation.AggregationWindowDiscoveryContext;
import fk.prof.backend.http.handler.RecordedProfileRequestHandler;
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

  private final ConfigManager configManager;
  private final LeaderReadContext leaderReadContext;
  private final AggregationWindowDiscoveryContext aggregationWindowDiscoveryContext;
  private final ProcessGroupDiscoveryContext processGroupDiscoveryContext;
  private final int leaderHttpPort;
  private final int backendHttpPort;
  private final String ipAddress;
  private final int backendVersion;

  private LocalMap<Long, Boolean> workIdsInPipeline;
  private ProfHttpClient httpClient;

  public BackendHttpVerticle(ConfigManager configManager,
                             LeaderReadContext leaderReadContext,
                             AggregationWindowDiscoveryContext aggregationWindowDiscoveryContext,
                             ProcessGroupDiscoveryContext processGroupDiscoveryContext) {
    this.configManager = configManager;
    this.leaderHttpPort = configManager.getLeaderHttpPort();
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
    httpClient = ProfHttpClient.newBuilder()
        .keepAlive(httpClientConfig.getBoolean("keepalive", true))
        .useCompression(httpClientConfig.getBoolean("compression", true))
        .setConnectTimeoutInMs(httpClientConfig.getInteger("connect.timeout.ms", 5000))
        .setIdleTimeoutInSeconds(httpClientConfig.getInteger("idle.timeout.secs", 120))
        .setMaxAttempts(httpClientConfig.getInteger("max.attempts", 3))
        .build(vertx);

    Router router = setupRouting();
    workIdsInPipeline = vertx.sharedData().getLocalMap("WORK_ID_PIPELINE");
    vertx.createHttpServer(HttpHelper.getHttpServerOptions(configManager.getBackendHttpServerConfig()))
        .requestHandler(router::accept)
        .listen(configManager.getBackendHttpPort(), http -> completeStartup(http, fut));
  }

  private Router setupRouting() {
    Router router = Router.router(vertx);
    router.route().handler(LoggerHandler.create());

    router.post(ApiPathConstants.AGGREGATOR_POST_PROFILE).handler(this::handlePostProfile);

    router.put(ApiPathConstants.BACKEND_PUT_ASSOCIATION)
        .handler(BodyHandler.create().setBodyLimit(1024 * 10));
    router.put(ApiPathConstants.BACKEND_PUT_ASSOCIATION)
        .handler(this::handlePutAssociation);

    router.put(ApiPathConstants.BACKEND_POST_POLL)
        .handler(BodyHandler.create().setBodyLimit(1024 * 100));
    router.put(ApiPathConstants.BACKEND_POST_POLL)
        .handler(this::handlePostPoll);

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
    CompositeByteBufInputStream inputStream = new CompositeByteBufInputStream();
    RecordedProfileProcessor profileProcessor = new RecordedProfileProcessor(
        aggregationWindowDiscoveryContext,
        new SharedMapBasedSingleProcessingOfProfileGate(workIdsInPipeline),
        config().getJsonObject("parser").getInteger("recordingheader.max.bytes", 1024),
        config().getJsonObject("parser").getInteger("parser.wse.max.bytes", 1024 * 1024));

    RecordedProfileRequestHandler requestHandler = new RecordedProfileRequestHandler(context, inputStream, profileProcessor);
    context.request()
        .handler(requestHandler)
        .endHandler(v -> {
          try {
            if (!context.response().ended()) {
              //Can safely attempt to close the profile processor here because endHandler is called once the entire body has been read
              //and example in vertx docs also indicates that this handler will execute once all chunk handlers have completed execution
              //http://vertx.io/docs/vertx-core/java/#_handling_requests
              inputStream.close();
              profileProcessor.close();
              context.response().end();
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
      Recorder.ProcessGroup processGroup = RecorderProtoUtil.mapRecorderInfoToProcessGroup(pollReq.getRecorderInfo());
      ProcessGroupContextForPolling processGroupContextForPolling = this.processGroupDiscoveryContext.getProcessGroupContextForPolling(processGroup);
      if(processGroupContextForPolling == null) {
        throw new IllegalArgumentException("Process group " + RecorderProtoUtil.processGroupCompactRepr(processGroup) + " not associated with the backend");
      }

      Recorder.WorkAssignment nextWorkAssignment = processGroupContextForPolling.receivePoll(pollReq);
      Recorder.PollRes.Builder pollResBuilder = Recorder.PollRes.newBuilder()
          .setControllerVersion(backendVersion)
          .setControllerId(Ints.fromByteArray(ipAddress.getBytes("UTF-8")))
          .setLocalTime(nextWorkAssignment == null
              ? LocalDateTime.now(Clock.systemUTC()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
              : nextWorkAssignment.getIssueTime());
      Recorder.PollRes pollRes;
      if(nextWorkAssignment == null) {
        pollRes = pollResBuilder.build();
      } else {
        pollRes = pollResBuilder.setAssignment(nextWorkAssignment).build();
        AggregationWindow aggregationWindow = aggregationWindowDiscoveryContext.getAssociatedAggregationWindow(nextWorkAssignment.getWorkId());
        if (aggregationWindow == null) {
          throw new IllegalArgumentException(String.format("workId=%d not found, cannot associate recorder info with aggregated profile. aborting send of work assignment",
              nextWorkAssignment.getWorkId()));
        }
        aggregationWindow.updateRecorderInfo(nextWorkAssignment.getWorkId(), pollReq.getRecorderInfo());
      }
      context.response().end(ProtoUtil.buildBufferFromProto(pollRes));
    } catch (Exception ex) {
      HttpFailure httpFailure = HttpFailure.failure(ex);
      HttpHelper.handleFailure(context, httpFailure);
    }
  }

  // /association API is requested over ELB, routed to some backend which in turns proxies it to a leader
  private void handlePutAssociation(RoutingContext context) {
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
        makeRequestGetAssociation(leaderDetail, recorderInfo).setHandler(ar -> {
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
      response.setStatusCode(400).end("Leader refuses to respond to this request");
      return null;
    } else {
      BackendDTO.LeaderDetail leaderDetail = leaderReadContext.getLeader();
      if (leaderDetail == null) {
        response.setStatusCode(503).putHeader("Retry-After", "10").end("Leader not elected yet");
        return null;
      } else {
        return leaderDetail;
      }
    }
  }

  private Future<ProfHttpClient.ResponseWithStatusTuple> makeRequestGetAssociation(BackendDTO.LeaderDetail leaderDetail, Recorder.RecorderInfo payload)
      throws IOException {
    Buffer payloadAsBuffer = ProtoUtil.buildBufferFromProto(payload);
    return httpClient.requestAsyncWithRetry(
        HttpMethod.PUT,
        leaderDetail.getHost(), leaderDetail.getPort(), ApiPathConstants.LEADER_PUT_ASSOCIATION,
        payloadAsBuffer);
  }
}
