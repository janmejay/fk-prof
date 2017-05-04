package fk.prof.userapi;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import io.vertx.core.json.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Map configuration to POJO and encapsulate default settings inside it
 * Use this POJO to access config and remove jsonobject getters from rest of the code base
 */
public class UserapiConfigManager {

  private static final String VERTX_OPTIONS_KEY = "vertxOptions";
  private static final String USERAPI_HTTP_DEPLOYMENT_OPTIONS_KEY = "userapiHttpOptions";
  private static final String LOGFACTORY_SYSTEM_PROPERTY_KEY = "vertx.logger-delegate-factory-class-name";
  private static final String LOGFACTORY_SYSTEM_PROPERTY_DEFAULT_VALUE = "io.vertx.core.logging.SLF4JLogDelegateFactory";
  private static final String STORAGE = "storage";
  private static final String S3 = "s3";
  private static final String THREAD_POOL = "thread.pool";
  private static final String BUFFER_POOL_OPTIONS_KEY = "bufferPoolOptions";

  static final String METRIC_REGISTRY = "backend-metric-registry";
  private static final String PROFILE_RETENTION_KEY = "profile.retention.duration.min";
  private static final String USERPAI_HTTP_PORT_KEY = "port";
  private static final String REQ_TIMEOUT_KEY = "req.timeout";
  private static final String CONFIG = "config";

  private final JsonObject config;


  public UserapiConfigManager(String configFilePath) throws IOException {
    Preconditions.checkNotNull(configFilePath);
    this.config = new JsonObject(Files.toString(
        new File(configFilePath), StandardCharsets.UTF_8));
  }

  public UserapiConfigManager(JsonObject config) {
    Preconditions.checkNotNull(config);
    this.config = config;
  }

  JsonObject getVertxConfig() {
    return config.getJsonObject(VERTX_OPTIONS_KEY, new JsonObject());
  }

  private JsonObject enrichDeploymentConfig(JsonObject deploymentConfig) {
    if (deploymentConfig.getJsonObject("config") == null) {
      deploymentConfig.put("config", new JsonObject());
    }
    return deploymentConfig;
  }

  public static void setDefaultSystemProperties() {
    Properties properties = System.getProperties();
    properties.computeIfAbsent(UserapiConfigManager.LOGFACTORY_SYSTEM_PROPERTY_KEY, k -> UserapiConfigManager.LOGFACTORY_SYSTEM_PROPERTY_DEFAULT_VALUE);
    properties.computeIfAbsent("vertx.metrics.options.enabled", k -> true);
  }

  JsonObject getS3Config() {
    JsonObject s3Config = getStorageConfig().getJsonObject(S3);
    if(!s3Config.containsKey("list.objects.timeout.ms")) {
      // put default value for listObject timeout
      s3Config.put("list.objects.timeout.ms", 5000L);
    }

    // check for consistent config
    Long requestTimeout = getUserapiHttpDeploymentConfig().getJsonObject("config").getLong("req.timeout");
    Long ListObjectTimeout = s3Config.getLong("list.objects.timeout.ms");
    if(requestTimeout <= ListObjectTimeout) {
      throw new RuntimeException("request timeout must be greater than listObject timeout");
    }
    return s3Config;
  }


  JsonObject getStorageThreadPoolConfig() {
    JsonObject tpConfig = getStorageConfig().getJsonObject(THREAD_POOL);
    checkNotEmpty(tpConfig, "thread pool");
    return tpConfig;
  }

  JsonObject getStorageConfig() {
    JsonObject storageConfig = config.getJsonObject(STORAGE);
    checkNotEmpty(storageConfig, "storage");
    return storageConfig;
  }

  JsonObject getBufferPoolConfig() {
    JsonObject poolConfig = config.getJsonObject(BUFFER_POOL_OPTIONS_KEY, new JsonObject());
    if (poolConfig.getInteger("max.total") <= 0 || poolConfig.getInteger("max.idle") < 0 || poolConfig.getInteger("buffer.size") <= 0) {
      throw new RuntimeException("buffer pool config is not proper");
    }
    return poolConfig;
  }

  private void checkNotEmpty(JsonObject json, String tag) {
    if (json == null || json.isEmpty()) {
      // TODO: convert these to configException
      throw new RuntimeException(tag + " config is not present");
    }
  }

  public JsonObject getUserapiHttpDeploymentConfig() {
    JsonObject deploymentConfig = enrichDeploymentConfig(config.getJsonObject(USERAPI_HTTP_DEPLOYMENT_OPTIONS_KEY, new JsonObject()));
    JsonObject httpConfig = deploymentConfig.getJsonObject("config");
    if(!httpConfig.containsKey("req.timeout")) {
      // put default value for request timeout
      httpConfig.put("req.timeout", 10000);
    }
    return deploymentConfig;
  }

  int getProfileRetentionDuration() {
    return config.getInteger(PROFILE_RETENTION_KEY, 30);
  }

  public int getAggregationWindowDurationInSecs() {
    return config.getInteger("aggregation_window.duration.secs", 30);
  }

  public String getBaseDir() {
    return config.getString("base.dir", "profiles");
  }

  public int getUserapiHttpPort() {
    return getUserapiHttpDeploymentConfig().getJsonObject(CONFIG).getInteger(USERPAI_HTTP_PORT_KEY, 8082);
  }
}
