package fk.prof.backend.worker;

import fk.prof.backend.ConfigManager;
import fk.prof.backend.http.ApiPathConstants;
import fk.prof.backend.http.ProfHttpClient;
import fk.prof.backend.model.election.LeaderReadContext;
import fk.prof.backend.proto.BackendDTO;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import recording.Recorder;

public class LoadReportingWorker extends AbstractVerticle {
  private static Logger logger = LoggerFactory.getLogger(LoadReportingWorker.class);

  private final ConfigManager configManager;
  private final int leaderPort;
  private final int loadReportIntervalInSeconds;
  private final String ipAddress;
  private final LeaderReadContext leaderReadContext;
  private ProfHttpClient httpClient;

  public LoadReportingWorker(ConfigManager configManager, LeaderReadContext leaderReadContext) {
    this.configManager = configManager;
    this.leaderReadContext = leaderReadContext;

    this.leaderPort = configManager.getLeaderHttpPort();
    this.loadReportIntervalInSeconds = configManager.getLoadReportIntervalInSeconds();
    this.ipAddress = configManager.getIPAddress();
  }

  @Override
  public void start(Future<Void> fut) {
    JsonObject httpClientConfig = configManager.getHttpClientConfig();
    httpClient = ProfHttpClient.newBuilder()
        .keepAlive(httpClientConfig.getBoolean("keepalive", false))
        .useCompression(httpClientConfig.getBoolean("compression", true))
        .setConnectTimeoutInMs(httpClientConfig.getInteger("connect.timeout.ms", 2000))
        .setIdleTimeoutInSeconds(httpClientConfig.getInteger("idle.timeout.secs", 3))
        .setMaxAttempts(httpClientConfig.getInteger("max.attempts", 1))
        .build(vertx);

    scheduleLoadReportTimer();
    fut.complete();
  }

  private void scheduleLoadReportTimer() {
    vertx.setPeriodic(loadReportIntervalInSeconds, timerId -> {
      String leaderIPAddress;
      if((leaderIPAddress = leaderReadContext.getLeaderIPAddress()) != null) {
        makeRequestPostLoad(leaderIPAddress,
            //TODO: Hardcoded load = 0.5 right now. Refactor this to have dynamic load
            BackendDTO.LoadReportRequest.newBuilder().setIp(ipAddress).setLoad(0.5f).build())
            .setHandler(ar -> {
              if(ar.succeeded()) {
                if(ar.result().getStatusCode() == 200) {
                  try {
                    Recorder.ProcessGroups assignedProcessGroups = Recorder.ProcessGroups.parseFrom(ar.result().getResponse().getBytes());
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
            });
      } else {
        logger.debug("Not reporting load because leader is unknown");
      }
    });
  }

  private Future<ProfHttpClient.ResponseWithStatusTuple> makeRequestPostLoad(String leaderIPAddress, BackendDTO.LoadReportRequest payload) {
    return httpClient.requestAsync(
        HttpMethod.POST,
        leaderIPAddress, leaderPort, ApiPathConstants.LEADER_POST_LOAD,
        Buffer.buffer(payload.toByteArray()));
  }
}
