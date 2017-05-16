package fk.prof.userapi;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Map configuration to {@link Configuration} and encapsulate default settings inside it.
 */
public class UserapiConfigManager {

  private static Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  private static final String LOGFACTORY_SYSTEM_PROPERTY_KEY = "vertx.logger-delegate-factory-class-name";
  private static final String LOGFACTORY_SYSTEM_PROPERTY_DEFAULT_VALUE = "io.vertx.core.logging.SLF4JLogDelegateFactory";

  public static final String METRIC_REGISTRY = "backend-metric-registry";

  private final Configuration config;

  public UserapiConfigManager(String configFilePath) throws IOException {
    Preconditions.checkNotNull(configFilePath);
    JsonObject json = new JsonObject(Files.toString(new File(configFilePath), StandardCharsets.UTF_8));
    this.config = json.mapTo(Configuration.class);

    validateConfig(this.config);
  }

  public static void setDefaultSystemProperties() {
    Properties properties = System.getProperties();
    properties.computeIfAbsent(UserapiConfigManager.LOGFACTORY_SYSTEM_PROPERTY_KEY, k -> UserapiConfigManager.LOGFACTORY_SYSTEM_PROPERTY_DEFAULT_VALUE);
    properties.computeIfAbsent("vertx.metrics.options.enabled", k -> true);
  }

  Configuration.S3Config getS3Config() {
    return getStorageConfig().s3Config;
  }

  Configuration.FixedSizeThreadPoolConfig getStorageThreadPoolConfig() {
    return getStorageConfig().tpConfig;
  }

  Configuration.StorageConfig getStorageConfig() {
    Configuration.StorageConfig storageConfig = config.storageConfig;

    // check for consistent config
    Long requestTimeout = getUserapiHttpConfig().requestTimeout;
    Long ListObjectTimeout = storageConfig.s3Config.listObjectsTimeoutMs;
    if(requestTimeout <= ListObjectTimeout) {
      throw new RuntimeException("request timeout must be greater than listObject timeout");
    }

    return storageConfig;
  }

  public DeploymentOptions getUserapiHttpDeploymentConfig() {
    return config.httpVerticleConfig;
  }

  int getProfileRetentionDuration() {
    return config.profileRetentionDurationMin;
  }

  public int getAggregationWindowDurationInSecs() {
    return config.aggregationWindowDurationSec;
  }

  public String getBaseDir() {
    return config.baseDir;
  }

  public Configuration.HttpConfig getUserapiHttpConfig() {
    return getUserapiHttpDeploymentConfig().getConfig().mapTo(Configuration.HttpConfig.class);
  }

  public static <T> void validateConfig(T config) {
    Set<ConstraintViolation<T>> violations = validator.validate(config);
    if(violations.size() > 0) {
      String message = "Configuration is invalid:\n" +
          String.join("\n",
              violations.stream().map(v -> v.getPropertyPath().toString() + " " + v.getMessage()).collect(Collectors.toList()));
      throw new RuntimeException(message);
    }
  }
}
