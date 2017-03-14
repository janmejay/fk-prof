package fk.prof.backend.worker;

import fk.prof.backend.ConfigManager;
import fk.prof.backend.http.ApiPathConstants;
import fk.prof.backend.http.ProfHttpClient;
import fk.prof.backend.model.aggregation.AggregationWindowLookupStore;
import fk.prof.backend.model.assignment.AggregationWindowPlannerStore;
import fk.prof.backend.model.assignment.AssociatedProcessGroups;
import fk.prof.backend.model.election.LeaderReadContext;
import fk.prof.backend.model.slot.WorkSlotPool;
import fk.prof.backend.proto.BackendDTO;
import fk.prof.backend.util.ProtoUtil;
import fk.prof.backend.util.proto.RecorderProtoUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import recording.Recorder;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class BackendDaemon extends AbstractVerticle {
  private static Logger logger = LoggerFactory.getLogger(BackendDaemon.class);
  private static String ENCODING = "UTF-8";

  private final ConfigManager configManager;
  private final LeaderReadContext leaderReadContext;
  private final AssociatedProcessGroups associatedProcessGroups;
  private final WorkSlotPool workSlotPool;
  private final AggregationWindowLookupStore aggregationWindowLookupStore;
  private final String ipAddress;
  private final int leaderHttpPort;
  private final int backendHttpPort;

  private AggregationWindowPlannerStore aggregationWindowPlannerStore;
  private ProfHttpClient httpClient;
  private int loadTickCounter = 0;

  public BackendDaemon(ConfigManager configManager,
                       LeaderReadContext leaderReadContext,
                       AssociatedProcessGroups associatedProcessGroups,
                       AggregationWindowLookupStore aggregationWindowLookupStore,
                       WorkSlotPool workSlotPool) {
    this.configManager = configManager;
    this.ipAddress = configManager.getIPAddress();
    this.leaderHttpPort = configManager.getLeaderHttpPort();
    this.backendHttpPort = configManager.getBackendHttpPort();

    this.leaderReadContext = leaderReadContext;
    this.associatedProcessGroups = associatedProcessGroups;
    this.aggregationWindowLookupStore = aggregationWindowLookupStore;
    this.workSlotPool = workSlotPool;
  }

  @Override
  public void start() {
    httpClient = buildHttpClient();
    aggregationWindowPlannerStore = buildAggregationWindowPlannerStore();
    postLoadToLeader();
  }

  private ProfHttpClient buildHttpClient() {
    JsonObject httpClientConfig = configManager.getHttpClientConfig();
    ProfHttpClient httpClient = ProfHttpClient.newBuilder()
        .keepAlive(httpClientConfig.getBoolean("keepalive", false))
        .useCompression(httpClientConfig.getBoolean("compression", true))
        .setConnectTimeoutInMs(httpClientConfig.getInteger("connect.timeout.ms", 2000))
        .setIdleTimeoutInSeconds(httpClientConfig.getInteger("idle.timeout.secs", 3))
        .setMaxAttempts(httpClientConfig.getInteger("max.attempts", 1))
        .build(vertx);
    return httpClient;
  }

  private AggregationWindowPlannerStore buildAggregationWindowPlannerStore() {
    return new AggregationWindowPlannerStore(
        vertx,
        configManager.getBackendId(),
        config().getInteger("aggregation.window.duration.mins", 30),
        config().getInteger("aggregation.window.end.tolerance.secs", 120),
        config().getInteger("policy.refresh.offset.secs", 300),
        config().getInteger("scheduling.buffer.secs", 30),
        config().getInteger("work.assignment.max.delay.secs", 120),
        workSlotPool,
        aggregationWindowLookupStore,
        this::getWorkFromLeader);
  }

  private void postLoadToLeader() {
    String leaderIPAddress;
    if((leaderIPAddress = leaderReadContext.getLeaderIPAddress()) != null) {

      //TODO: load = 0.5 hard-coded right now. Replace this with dynamic load computation in future
      float load = 0.5f;

      try {
        httpClient.requestAsync(
            HttpMethod.POST,
            leaderIPAddress,
            leaderHttpPort,
            ApiPathConstants.LEADER_POST_LOAD,
            ProtoUtil.buildBufferFromProto(
                BackendDTO.LoadReportRequest.newBuilder()
                    .setIp(ipAddress)
                    .setPort(backendHttpPort)
                    .setLoad(load)
                    .setCurrTick(loadTickCounter)
                    .build()))
            .setHandler(ar -> {
              if(ar.succeeded()) {
                if(ar.result().getStatusCode() == 200) {
                  try {
                    loadTickCounter++;
                    Recorder.ProcessGroups assignedProcessGroups = ProtoUtil.buildProtoFromBuffer(Recorder.ProcessGroups.parser(), ar.result().getResponse());
                    associatedProcessGroups.updateProcessGroupAssociations(assignedProcessGroups, (processGroupDetail, processGroupAssociationResult) -> {
                      switch (processGroupAssociationResult) {
                        case ADDED:
                          this.aggregationWindowPlannerStore.associateAggregationWindowPlannerIfAbsent(processGroupDetail);
                          break;
                        case REMOVED:
                          this.aggregationWindowPlannerStore.deAssociateAggregationWindowPlanner(processGroupDetail.getProcessGroup());
                          break;
                      }
                    });
                  } catch (Exception ex) {
                    logger.error("Error parsing response returned by leader when reporting load");
                  }
                } else {
                  logger.error("Non OK status returned by leader when reporting load, status=" + ar.result().getStatusCode());
                }
              } else {
                logger.error("Error when reporting load to leader", ar.cause());
              }
              setupTimerForReportingLoad();
            });
      } catch (IOException ex) {
        logger.error("Error building load request body", ex);
        setupTimerForReportingLoad();
      }
    } else {
      logger.debug("Not reporting load because leader is unknown");
      setupTimerForReportingLoad();
    }
  }

  private void setupTimerForReportingLoad() {
    vertx.setTimer(configManager.getLoadReportIntervalInSeconds() * 1000, timerId -> postLoadToLeader());
  }

  private Future<BackendDTO.RecordingPolicy> getWorkFromLeader(Recorder.ProcessGroup processGroup) {
    Future<BackendDTO.RecordingPolicy> result = Future.future();
    String leaderIPAddress;
    if((leaderIPAddress = leaderReadContext.getLeaderIPAddress()) != null) {
      try {
        String requestPath = new StringBuilder(ApiPathConstants.LEADER_GET_WORK)
            .append('/').append(URLEncoder.encode(processGroup.getAppId(), ENCODING))
            .append('/').append(URLEncoder.encode(processGroup.getCluster(), ENCODING))
            .append('/').append(URLEncoder.encode(processGroup.getProcName(), ENCODING))
            .toString();

        //TODO: Support configuring max retries at request level because this request should definitely be retried on failure while other requests like posting load to backend need not be
        httpClient.requestAsync(
            HttpMethod.GET,
            leaderIPAddress,
            leaderHttpPort,
            requestPath,
            null).setHandler(ar -> {
              if (ar.failed()) {
                result.fail("Error when requesting work from leader for process group="
                    + RecorderProtoUtil.processGroupCompactRepr(processGroup)
                    + ", message=" + ar.cause());
                return;
              }
              if (ar.result().getStatusCode() != 200) {
                result.fail("Non-OK status code when requesting work from leader for process group="
                    + RecorderProtoUtil.processGroupCompactRepr(processGroup)
                    + ", status=" + ar.result().getStatusCode());
                return;
              }
              try {
                BackendDTO.RecordingPolicy recordingPolicy = ProtoUtil.buildProtoFromBuffer(BackendDTO.RecordingPolicy.parser(), ar.result().getResponse());
                result.complete(recordingPolicy);
              } catch (Exception ex) {
                result.fail("Error parsing work response returned by leader for process group=" + RecorderProtoUtil.processGroupCompactRepr(processGroup));
              }
            });
      } catch (UnsupportedEncodingException ex) {
        result.fail("Error building url for process_group=" + RecorderProtoUtil.processGroupCompactRepr(processGroup));
      }
    } else {
      result.fail("Not reporting load because leader is unknown");
    }

    return result;
  }

}
