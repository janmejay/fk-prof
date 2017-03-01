package fk.prof.backend.worker;

import com.google.common.primitives.Ints;
import com.google.protobuf.InvalidProtocolBufferException;
import fk.prof.backend.ConfigManager;
import fk.prof.backend.http.ApiPathConstants;
import fk.prof.backend.http.ProfHttpClient;
import fk.prof.backend.model.assignment.WorkAssignmentManager;
import fk.prof.backend.model.assignment.WorkAssignmentManagerImpl;
import fk.prof.backend.model.election.LeaderReadContext;
import fk.prof.backend.proto.BackendDTO;
import fk.prof.backend.util.ProtoUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URIUtils;
import recording.Recorder;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class WorkAssignmentScheduler extends AbstractVerticle {
  private static Logger logger = LoggerFactory.getLogger(WorkAssignmentScheduler.class);
  private static String ENCODING = "UTF-8";

  private final ConfigManager configManager;
  private final LeaderReadContext leaderReadContext;
  private final WorkAssignmentManager workAssignmentManager;
  private final String ipAddress;
  private final int leaderPort;

  private ProfHttpClient httpClient;
  private Function<Recorder.ProcessGroup, Future<BackendDTO.WorkProfile>> requestWorkFromLeader;
  private int loadTickCounter = 0;

  public WorkAssignmentScheduler(ConfigManager configManager, LeaderReadContext leaderReadContext, WorkAssignmentManager workAssignmentManager) {
    this.configManager = configManager;
    this.ipAddress = configManager.getIPAddress();
    this.leaderPort = configManager.getLeaderHttpPort();

    this.leaderReadContext = leaderReadContext;
    this.workAssignmentManager = workAssignmentManager;
  }

  @Override
  public void start() {
    httpClient = buildHttpClient();
    workAssignmentManager.initialize(processGroup -> getWorkFromLeader(processGroup));
    registerPollRequestConsumer();
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
                    //TODO: Do something with the returned assigned process groups
                  } catch (Exception ex) {
                    logger.error("Error parsing response returned by leader when reporting load");
                  }
                } else {
                  logger.error("Non OK status returned by leader when reporting load, status=" + ar.result().getStatusCode());
                }
              } else {
                logger.error("Error when reporting load to leader", ar.cause());
              }

              vertx.setTimer(configManager.getLoadReportIntervalInSeconds(), timerId -> postLoadToLeader());
            });
      } catch (IOException ex) {
        logger.error("Error building load request body", ex);
      }
    } else {
      logger.debug("Not reporting load because leader is unknown");
    }
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

  private void registerPollRequestConsumer() {
    vertx.eventBus().consumer("recorder.poll", (Message<Buffer> message) -> {
      try {
        Recorder.PollReq pollReq = ProtoUtil.buildProtoFromBuffer(Recorder.PollReq.parser(), message.body());
        Recorder.WorkAssignment nextWorkAssignment = this.workAssignmentManager.receivePoll(
            pollReq.getRecorderInfo(), pollReq.getWorkLastIssued());
        Recorder.PollRes pollRes = Recorder.PollRes.newBuilder()
            .setAssignment(nextWorkAssignment)
            .setControllerVersion(config().getInteger("backend.version"))
            .setControllerId(Ints.fromByteArray(ipAddress.getBytes("UTF-8")))
            .setLocalTime(nextWorkAssignment == null
                ? LocalDateTime.now(Clock.systemUTC()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : nextWorkAssignment.getIssueTime())
            .build();
        message.reply(ProtoUtil.buildBufferFromProto(pollRes));
      } catch (InvalidProtocolBufferException ex) {
        message.fail(400, "Error parsing poll request body");
      } catch (IllegalArgumentException ex) {
        message.fail(400, ex.getMessage());
      } catch (UnsupportedEncodingException ex) {
        message.fail(500, ex.getMessage());
      } catch (IOException ex) {
        message.fail(500, ex.getMessage());
      }
    });
  }
}
