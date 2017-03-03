package fk.prof.backend.worker;

import fk.prof.backend.ConfigManager;
import fk.prof.backend.http.ApiPathConstants;
import fk.prof.backend.http.ProfHttpClient;
import fk.prof.backend.model.aggregation.AggregationWindowLookupStore;
import fk.prof.backend.model.assignment.AggregationWindowPlannerStore;
import fk.prof.backend.model.assignment.ProcessGroupAssociationStore;
import fk.prof.backend.model.assignment.SimultaneousWorkAssignmentCounter;
import fk.prof.backend.model.election.LeaderReadContext;
import fk.prof.backend.proto.BackendDTO;
import fk.prof.backend.util.ProtoUtil;
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
  private final AggregationWindowPlannerStore aggregationWindowPlannerStore;
  private final ProcessGroupAssociationStore processGroupAssociationStore;
  private final String ipAddress;
  private final int leaderPort;

  private ProfHttpClient httpClient;
  private int loadTickCounter = 0;

  public BackendDaemon(ConfigManager configManager,
                       LeaderReadContext leaderReadContext,
                       ProcessGroupAssociationStore processGroupAssociationStore,
                       AggregationWindowLookupStore aggregationWindowLookupStore,
                       SimultaneousWorkAssignmentCounter simultaneousWorkAssignmentCounter) {
    this.configManager = configManager;
    this.ipAddress = configManager.getIPAddress();
    this.leaderPort = configManager.getLeaderHttpPort();

    this.leaderReadContext = leaderReadContext;
    this.processGroupAssociationStore = processGroupAssociationStore;
    this.aggregationWindowPlannerStore = new AggregationWindowPlannerStore(
        vertx,
        config().getInteger("aggregation.window.duration.mins", 30),
        config().getInteger("aggregation.window.end.tolerance.secs", 120),
        config().getInteger("workprofile.refresh.offset.secs", 300),
        config().getInteger("scheduling.buffer.secs", 30),
        config().getInteger("work.assignment.max.delay.secs", 120),
        simultaneousWorkAssignmentCounter,
        aggregationWindowLookupStore,
        this::getWorkFromLeader);
  }

  @Override
  public void start() {
    httpClient = buildHttpClient();
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

  private void postLoadToLeader() {
    String leaderIPAddress;
    if((leaderIPAddress = leaderReadContext.getLeaderIPAddress()) != null) {

      //TODO: load = 0.5 hard-coded right now. Replace this with dynamic load computation in future
      float load = 0.5f;

      try {
        httpClient.requestAsync(
            HttpMethod.POST,
            leaderIPAddress,
            leaderPort,
            ApiPathConstants.LEADER_POST_LOAD,
            ProtoUtil.buildBufferFromProto(
                BackendDTO.LoadReportRequest.newBuilder()
                    .setIp(ipAddress)
                    .setLoad(load).setCurrTick(loadTickCounter++).build()))
            .setHandler(ar -> {
              if(ar.succeeded()) {
                if(ar.result().getStatusCode() == 200) {
                  try {
                    Recorder.ProcessGroups assignedProcessGroups = ProtoUtil.buildProtoFromBuffer(Recorder.ProcessGroups.parser(), ar.result().getResponse());
                    processGroupAssociationStore.updateProcessGroupAssociations(assignedProcessGroups, (processGroupDetail, processGroupAssociationResult) -> {
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
    vertx.setTimer(configManager.getLoadReportIntervalInSeconds(), timerId -> postLoadToLeader());
  }

  private Future<BackendDTO.WorkProfile> getWorkFromLeader(Recorder.ProcessGroup processGroup) {
    Future<BackendDTO.WorkProfile> result = Future.future();
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
            leaderPort,
            requestPath,
            null).setHandler(ar -> {
              if (ar.failed()) {
                result.fail("Error when requesting work from leader for process group="
                    + ProtoUtil.processGroupCompactRepr(processGroup)
                    + ", message=" + ar.cause());
                return;
              }
              if (ar.result().getStatusCode() != 200) {
                result.fail("Non-OK status code when requesting work from leader for process group="
                    + ProtoUtil.processGroupCompactRepr(processGroup)
                    + ", status=" + ar.result().getStatusCode());
                return;
              }
              try {
                BackendDTO.WorkProfile workProfile = ProtoUtil.buildProtoFromBuffer(BackendDTO.WorkProfile.parser(), ar.result().getResponse());
                result.complete(workProfile);
              } catch (Exception ex) {
                result.fail("Error parsing work response returned by leader for process group=" + ProtoUtil.processGroupCompactRepr(processGroup));
              }
            });
      } catch (UnsupportedEncodingException ex) {
        result.fail("Error building url for process_group=" + ProtoUtil.processGroupCompactRepr(processGroup));
      }
    } else {
      result.fail("Not reporting load because leader is unknown");
    }

    return result;
  }

}
