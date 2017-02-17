package fk.prof.backend.worker;

import fk.prof.backend.http.ApiPathConstants;
import fk.prof.backend.http.ConfigurableHttpClient;
import fk.prof.backend.model.election.LeaderDiscoveryStore;
import fk.prof.backend.proto.BackendDTO;
import fk.prof.backend.util.IPAddressUtil;
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

  private final JsonObject httpClientConfig;
  private final int leaderPort;
  private final int loadReportIntervalInSeconds;
  private final LeaderDiscoveryStore leaderDiscoveryStore;
  private ConfigurableHttpClient httpClient;

  public LoadReportingWorker(JsonObject httpClientConfig, int leaderPort, int loadReportIntervalInSeconds, LeaderDiscoveryStore leaderDiscoveryStore) {
    this.httpClientConfig = httpClientConfig;
    this.leaderPort = leaderPort;
    this.loadReportIntervalInSeconds = loadReportIntervalInSeconds;
    this.leaderDiscoveryStore = leaderDiscoveryStore;
  }

  @Override
  public void start(Future<Void> fut) {
    httpClient = ConfigurableHttpClient.newBuilder()
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
      if((leaderIPAddress = leaderDiscoveryStore.getLeaderIPAddress()) != null) {
        makeRequestPostLoad(leaderIPAddress,
            //TODO: Hardcoded load = 0.5 right now. Refactor this to have dynamic load
            BackendDTO.LoadReportRequest.newBuilder().setIp(IPAddressUtil.getIPAddressAsString()).setLoad(0.5f).build())
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

  private Future<ConfigurableHttpClient.ResponseWithStatusTuple> makeRequestPostLoad(String leaderIPAddress, BackendDTO.LoadReportRequest payload) {
    return httpClient.requestAsync(
        HttpMethod.POST,
        leaderIPAddress, leaderPort, ApiPathConstants.LEADER_POST_LOAD,
        Buffer.buffer(payload.toByteArray()));
  }
}
