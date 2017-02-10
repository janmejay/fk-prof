package fk.prof.backend;

import com.google.common.io.Files;
import io.vertx.core.json.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class ConfigManager {
  public static final String HTTP_PORT_KEY = "http.port";
  public static final String LOAD_REPORT_INTERVAL_KEY = "load.report.interval.secs";
  public static final String VERTX_OPTIONS_KEY = "vertxOptions";
  public static final String AGGREGATOR_DEPLOYMENT_OPTIONS_KEY = "aggregatorOptions";
  public static final String CURATOR_OPTIONS_KEY = "curatorOptions";
  public static final String LEADER_ELECTION_DEPLOYMENT_OPTIONS_KEY = "leaderElectionOptions";
  public static final String LEADER_HTTP_DEPLOYMENT_OPTIONS_KEY = "leaderHttpOptions";
  public static final String LOGFACTORY_SYSTEM_PROPERTY_KEY = "vertx.logger-delegate-factory-class-name";
  public static final String LOGFACTORY_SYSTEM_PROPERTY_DEFAULT_VALUE = "io.vertx.core.logging.SLF4JLogDelegateFactory";
  public static final String METRIC_REGISTRY = "vertx-registry";


  public static JsonObject loadFileAsJson(String confPath) throws IOException {
    return new JsonObject(Files.toString(
        new File(confPath), StandardCharsets.UTF_8));
  }

  public static int getHttpPort(JsonObject config) {
    return config.getInteger(HTTP_PORT_KEY);
  }

  public static int getLoadReportIntervalInSeconds(JsonObject config) {
    return config.getInteger(LOAD_REPORT_INTERVAL_KEY);
  }

  public static JsonObject getVertxConfig(JsonObject config) {
    return config.getJsonObject(VERTX_OPTIONS_KEY);
  }

  public static JsonObject getAggregatorDeploymentConfig(JsonObject config) {
    return config.getJsonObject(AGGREGATOR_DEPLOYMENT_OPTIONS_KEY);
  }

  public static JsonObject getCuratorConfig(JsonObject config) {
    return config.getJsonObject(CURATOR_OPTIONS_KEY);
  }

  public static JsonObject getLeaderElectionDeploymentConfig(JsonObject config) {
    return config.getJsonObject(LEADER_ELECTION_DEPLOYMENT_OPTIONS_KEY);
  }

  public static JsonObject getLeaderHttpDeploymentConfig(JsonObject config) {
    return config.getJsonObject(LEADER_HTTP_DEPLOYMENT_OPTIONS_KEY);
  }

  public static void setDefaultSystemProperties() {
    Properties properties = System.getProperties();
    properties.computeIfAbsent(ConfigManager.LOGFACTORY_SYSTEM_PROPERTY_KEY, k -> ConfigManager.LOGFACTORY_SYSTEM_PROPERTY_DEFAULT_VALUE);
    properties.computeIfAbsent("vertx.metrics.options.enabled", k -> true);
  }
}
