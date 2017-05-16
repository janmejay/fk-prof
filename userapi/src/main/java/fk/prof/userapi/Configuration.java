package fk.prof.userapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * Created by gaurav.ashok on 15/05/17.
 */
public class Configuration {

  @JsonProperty("profile.retention.duration.min")
  public Integer profileRetentionDurationMin = 30;

  @JsonProperty("aggregation_window.duration.secs")
  public Integer aggregationWindowDurationSec = 1800;

  @NotNull
  public DeploymentOptions httpVerticleConfig;

  @NotNull
  @Valid
  @JsonProperty("storage")
  public StorageConfig storageConfig;

  @NotNull
  @JsonProperty("base.dir")
  public String baseDir;

  @JsonProperty("userapiHttpOptions")
  public void setHttpVerticleConfig(Map<String, Object> jsonMap) {
    if (jsonMap != null) {
      httpVerticleConfig = new DeploymentOptions(new JsonObject(jsonMap));
    }
  }

  public static class HttpConfig {
    @NotNull
    @JsonProperty("verticle.count")
    public Integer verticleCount;

    @NotNull
    @JsonProperty("http.port")
    public Integer httpPort;

    @NotNull
    @JsonProperty("req.timeout")
    public Long requestTimeout;
  }

  public static class StorageConfig {
    @NotNull
    @Valid
    @JsonProperty("s3")
    public S3Config s3Config;

    @NotNull
    @Valid
    @JsonProperty("thread.pool")
    public FixedSizeThreadPoolConfig tpConfig;
  }

  public static class S3Config {
    @NotNull
    public String endpoint;

    @NotNull
    @JsonProperty("access.key")
    public String accessKey;

    @NotNull
    @JsonProperty("secret.key")
    public String secretKey;

    @NotNull
    @JsonProperty("list.objects.timeout.ms")
    public Long listObjectsTimeoutMs;
  }

  public static class FixedSizeThreadPoolConfig {
    @NotNull
    @JsonProperty("coresize")
    public Integer coreSize;

    @NotNull
    @JsonProperty("maxsize")
    public Integer maxSize;

    @NotNull
    @JsonProperty("idletime.secs")
    public Integer idleTimeSec;

    @NotNull
    @JsonProperty("queue.maxsize")
    public Integer queueMaxSize;
  }

  @NotNull
  @Valid
  public HttpConfig getHttpConfig() {
    return httpVerticleConfig.getConfig().mapTo(HttpConfig.class);
  }
}
