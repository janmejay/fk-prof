package fk.prof.backend;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import fk.prof.backend.leader.election.KillBehavior;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * Created by gaurav.ashok on 21/05/17.
 */
public class Configuration {
    @JsonCreator
    public Configuration(@JsonProperty("ip.address") String ipAddress,
                         @JsonProperty("backend.version") Integer backendVersion,
                         @JsonProperty("backend.id") Integer backendId,
                         @JsonProperty("load.report.interval.secs") Integer loadReportItvlSecs,
                         @JsonProperty("recorder.defunct.threshold.secs") Integer recorderDefunctThresholdSecs,
                         @JsonProperty("slot.pool.capacity") Integer scheduleSlotPoolCapacity,
                         @JsonProperty("backend.http.server") Map<String, Object> backendHttpServerCfgMap,
                         @JsonProperty("leader.http.server") Map<String, Object> leaderHttpServerCfgMap,
                         @JsonProperty("http.client") HttpClientConfig httpClientCfg,
                         @JsonProperty("vertxOptions") Map<String, Object> vertxOptionsMap,
                         @JsonProperty("backendHttpOptions") Map<String, Object> backendDeploymentOptsMap,
                         @JsonProperty("leaderElectionOptions") Map<String, Object> leaderElectionDeploymentOptsMap,
                         @JsonProperty("curatorOptions") CuratorConfig curatorCfg,
                         @JsonProperty("backendAssociations") BackendAssociationsConfig associationsCfg,
                         @JsonProperty("daemonOptions") Map<String, Object> daemonDeploymentOptsMap,
                         @JsonProperty("serializationWorkerPool") SerializationWorkerPoolConfig serializationWorkerPoolCfg,
                         @JsonProperty("storage") StorageConfig storageCfg,
                         @JsonProperty("bufferPoolOptions") BufferPoolConfig bufferPoolCfg) {
        this.ipAddress = ipAddress;
        this.backendVersion = backendVersion;
        this.backendId = backendId;
        this.loadReportItvlSecs = loadReportItvlSecs;
        this.recorderDefunctThresholdSecs = recorderDefunctThresholdSecs;
        this.scheduleSlotPoolCapacity = scheduleSlotPoolCapacity;
        this.backendHttpServerOpts = toHttpServerOptions(backendHttpServerCfgMap);
        this.leaderHttpServerOpts = toHttpServerOptions(leaderHttpServerCfgMap);
        this.httpClientCfg = httpClientCfg;

        this.vertxOptions = vertxOptionsMap != null ? new VertxOptions(new JsonObject(vertxOptionsMap)) : null;

        this.backendDeploymentOpts = toDeploymentOptions(backendDeploymentOptsMap);
        this.backendHttpVerticleCfg = toVerticleConfig(this.backendDeploymentOpts, BackendHttpVerticleConfig.class);

        this.leaderElectionDeploymentOpts = toDeploymentOptions(leaderElectionDeploymentOptsMap);
        this.leaderElectionVerticleCfg = toVerticleConfig(this.leaderElectionDeploymentOpts, LeaderElectionVerticleConfig.class);

        this.curatorCfg = curatorCfg;
        this.associationsCfg = associationsCfg;

        this.daemonDeploymentOpts = toDeploymentOptions(daemonDeploymentOptsMap);
        //Backend daemon should never be deployed more than once, so hardcoding verticle count to 1, to protect from illegal configuration
        this.daemonDeploymentOpts.getConfig().put("verticle.count", 1);
        this.daemonVerticleCfg = toVerticleConfig(this.daemonDeploymentOpts, DaemonVerticleConfig.class);

        this.serializationWorkerPoolCfg = serializationWorkerPoolCfg;
        this.storageCfg = storageCfg;
        this.bufferPoolCfg = bufferPoolCfg;

        //default config for now
        this.leaderDeploymentOpts = new DeploymentOptions().setConfig(new JsonObject());
    }

    @NotNull
    public final String ipAddress;
    @NotNull
    public final Integer backendVersion;
    @NotNull
    public final Integer backendId;
    @NotNull
    public final Integer loadReportItvlSecs;
    @NotNull
    public final Integer recorderDefunctThresholdSecs;
    @NotNull
    public final Integer scheduleSlotPoolCapacity;
    @NotNull
    @Valid
    public final HttpServerOptions backendHttpServerOpts;
    @NotNull
    @Valid
    public final HttpServerOptions leaderHttpServerOpts;
    @NotNull
    @Valid
    public final HttpClientConfig httpClientCfg;
    @NotNull
    public final VertxOptions vertxOptions;

    @NotNull
    public final DeploymentOptions backendDeploymentOpts;
    @NotNull
    @Valid
    public final BackendHttpVerticleConfig backendHttpVerticleCfg;

    @NotNull
    public final DeploymentOptions leaderDeploymentOpts;

    @NotNull
    public final DeploymentOptions leaderElectionDeploymentOpts;
    @NotNull
    @Valid
    public final LeaderElectionVerticleConfig leaderElectionVerticleCfg;

    @NotNull
    @Valid
    public final CuratorConfig curatorCfg;
    @NotNull
    @Valid
    public final BackendAssociationsConfig associationsCfg;

    @NotNull
    public final DeploymentOptions daemonDeploymentOpts;
    @NotNull
    @Valid
    public final DaemonVerticleConfig daemonVerticleCfg;

    @NotNull
    @Valid
    public final SerializationWorkerPoolConfig serializationWorkerPoolCfg;
    @NotNull
    @Valid
    public final StorageConfig storageCfg;
    @NotNull
    @Valid
    public final BufferPoolConfig bufferPoolCfg;

    public static class HttpClientConfig {
        @JsonCreator
        public HttpClientConfig(@JsonProperty("connect.timeout.ms") Integer connectTimeoutMs,
                                @JsonProperty("idle.timeout.secs") Integer idleTimeoutSecs,
                                @JsonProperty("max.attempts") Integer maxAttempts,
                                @JsonProperty("keepalive") Boolean keepAlive,
                                @JsonProperty("compression") Boolean supportCompression) {
            this.connectTimeoutMs = getOrDefault(connectTimeoutMs, 5000);
            this.idleTimeoutSecs = getOrDefault(idleTimeoutSecs, 120);
            this.maxAttempts = getOrDefault(maxAttempts, 3);
            this.keepAlive = getOrDefault(keepAlive, true);
            this.supportCompression = getOrDefault(supportCompression, true);
        }

        public final Integer connectTimeoutMs;
        public final Integer idleTimeoutSecs;
        public final Integer maxAttempts;
        public final Boolean keepAlive;
        public final Boolean supportCompression;
    }

    public static class BackendHttpVerticleConfig {
        @JsonCreator
        public BackendHttpVerticleConfig(@JsonProperty("verticle.count") Integer verticleCount,
                                         @JsonProperty("report.load") Boolean reportLoad,
                                         @JsonProperty("parser") ParserConfig parserCfg) {
            this.verticleCount = verticleCount;
            this.reportLoad = getOrDefault(reportLoad, true);
            this.parserCfg = parserCfg;
        }

        @NotNull
        public final Integer verticleCount;
        @NotNull
        public final Boolean reportLoad;
        @NotNull
        @Valid
        public final ParserConfig parserCfg;

        public static class ParserConfig {
            @JsonCreator
            public ParserConfig(@JsonProperty("recordingheader.max.bytes") Integer recordingHeaderMaxSizeBytes,
                                @JsonProperty("wse.max.bytes") Integer wseMaxSizeBytes) {
                this.recordingHeaderMaxSizeBytes = recordingHeaderMaxSizeBytes;
                this.wseMaxSizeBytes = wseMaxSizeBytes;
            }

            @NotNull
            public final Integer recordingHeaderMaxSizeBytes;
            @NotNull
            public final Integer wseMaxSizeBytes;
        }
    }

    public static class LeaderElectionVerticleConfig {
        @JsonCreator
        public LeaderElectionVerticleConfig(@JsonProperty("aggregation.enabled") Boolean aggregationEnabled,
                                            @JsonProperty("leader.watching.path") String leaderWatchPath,
                                            @JsonProperty("leader.mutex.path") String leaderMutexPath,
                                            @JsonProperty("kill.behavior") KillBehavior killBehaviour) {
            this.aggregationEnabled = getOrDefault(aggregationEnabled, false);
            this.leaderWatchPath = leaderWatchPath;
            this.leaderMutexPath = leaderMutexPath;
            this.killBehaviour = getOrDefault(killBehaviour, KillBehavior.DO_NOTHING);
        }

        @NotNull
        public final Boolean aggregationEnabled;
        @NotNull
        public final String leaderWatchPath;
        @NotNull
        public final String leaderMutexPath;
        @NotNull
        public final KillBehavior killBehaviour;
    }

    public static class CuratorConfig {
        @JsonCreator
        public CuratorConfig(@JsonProperty("connection.url") String connectionUrl,
                             @JsonProperty("namespace") String namespace,
                             @JsonProperty("connection.timeout.ms") Integer connectionTimeoutMs,
                             @JsonProperty("session.timeout.ms") Integer sessionTineoutMs,
                             @JsonProperty("max.retries") Integer maxRetries) {
            this.connectionUrl = connectionUrl;
            this.namespace = namespace;
            this.connectionTimeoutMs = connectionTimeoutMs;
            this.sessionTineoutMs = sessionTineoutMs;
            this.maxRetries = maxRetries;
        }

        @NotNull
        public final String connectionUrl;
        @NotNull
        public final String namespace;
        @NotNull
        public final Integer connectionTimeoutMs;
        @NotNull
        public final Integer sessionTineoutMs;
        @NotNull
        public final Integer maxRetries;
    }

    public static class BackendAssociationsConfig {
        @JsonCreator
        public BackendAssociationsConfig(@JsonProperty("backend.association.path") String associationPath,
                                         @JsonProperty("load.miss.tolerance") Integer loadMissTolerance) {
            this.associationPath = associationPath;
            this.loadMissTolerance = loadMissTolerance;
        }

        @NotNull
        public final String associationPath;
        @NotNull
        public final Integer loadMissTolerance;
    }

    public static class DaemonVerticleConfig {
        @JsonCreator
        public DaemonVerticleConfig(@JsonProperty("aggregation.window.duration.secs") Integer aggrWindowDurationSecs,
                                    @JsonProperty("aggregation.window.end.tolerance.secs") Integer aggrWindowEndToleranceSecs,
                                    @JsonProperty("policy.refresh.offset.secs") Integer policyRefreshOffsetSecs,
                                    @JsonProperty("scheduling.buffer.secs") Integer schedulingBufferSecs,
                                    @JsonProperty("work.assignment.max.delay.secs") Integer workAssignmentMaxDelaySecs,
                                    @JsonProperty("verticle.count") Integer verticleCount) {
            this.aggrWindowDurationSecs = aggrWindowDurationSecs;
            this.aggrWindowEndToleranceSecs = aggrWindowEndToleranceSecs;
            this.policyRefreshOffsetSecs = policyRefreshOffsetSecs;
            this.schedulingBufferSecs = schedulingBufferSecs;
            this.workAssignmentMaxDelaySecs = workAssignmentMaxDelaySecs;
            this.verticleCount = verticleCount;
        }

        @NotNull
        public final Integer aggrWindowDurationSecs;
        @NotNull
        public final Integer aggrWindowEndToleranceSecs;
        @NotNull
        public final Integer policyRefreshOffsetSecs;
        @NotNull
        public final Integer schedulingBufferSecs;
        @NotNull
        public final Integer workAssignmentMaxDelaySecs;
        @NotNull
        public final Integer verticleCount;
    }

    public static class SerializationWorkerPoolConfig {
        @JsonCreator
        public SerializationWorkerPoolConfig(@JsonProperty("size") Integer size,
                                             @JsonProperty("timeout.secs") Integer timeoutSecs) {
            this.size = size;
            this.timeoutSecs = timeoutSecs;
        }

        @NotNull
        public final Integer size;
        @NotNull
        public final Integer timeoutSecs;
    }

    public static class StorageConfig {
        @JsonCreator
        public StorageConfig(@JsonProperty("s3") S3Config s3Config,
                             @JsonProperty("thread.pool") FixedSizeThreadPoolConfig tpConfig,
                             @JsonProperty("aggregatedProfiles.baseDir") String profilesBaseDir) {
            this.s3Config = s3Config;
            this.tpConfig = tpConfig;
            this.profilesBaseDir = profilesBaseDir;
        }

        @NotNull
        @Valid
        public final S3Config s3Config;
        @NotNull
        @Valid
        public final FixedSizeThreadPoolConfig tpConfig;
        @NotNull
        public final String profilesBaseDir;

        public static class S3Config {
            @JsonCreator
            public S3Config(@JsonProperty("endpoint") String endpoint,
                            @JsonProperty("access.key") String accessKey,
                            @JsonProperty("secret.key") String secretKey,
                            @JsonProperty("list.objects.timeout.ms") Long listObjectsTimeoutMs) {
                this.endpoint = endpoint;
                this.accessKey = accessKey;
                this.secretKey = secretKey;
                this.listObjectsTimeoutMs = listObjectsTimeoutMs;
            }

            @NotNull
            public final String endpoint;
            @NotNull
            public final String accessKey;
            @NotNull
            public final String secretKey;
            @NotNull
            public final Long listObjectsTimeoutMs;
        }

        public static class FixedSizeThreadPoolConfig {
            @JsonCreator
            public FixedSizeThreadPoolConfig(@JsonProperty("coresize") Integer coreSize,
                                             @JsonProperty("maxsize") Integer maxSize,
                                             @JsonProperty("idletime.secs") Integer idleTimeSec,
                                             @JsonProperty("queue.maxsize") Integer queueMaxSize) {
                this.coreSize = coreSize;
                this.maxSize = maxSize;
                this.idleTimeSec = idleTimeSec;
                this.queueMaxSize = queueMaxSize;
            }

            @NotNull
            public final Integer coreSize;
            @NotNull
            public final Integer maxSize;
            @NotNull
            public final Integer idleTimeSec;
            @NotNull
            public final Integer queueMaxSize;
        }
    }

    public static class BufferPoolConfig {
        @JsonCreator
        public BufferPoolConfig(@JsonProperty("max.total") Integer maxTotal,
                                @JsonProperty("max.idle") Integer maxIdle,
                                @JsonProperty("buffer.size") Integer bufferSize) {
            this.maxTotal = maxTotal;
            this.maxIdle = maxIdle;
            this.bufferSize = bufferSize;
        }

        @NotNull
        public final Integer maxTotal;
        @NotNull
        public final Integer maxIdle;
        @NotNull
        public final Integer bufferSize;
    }

    private static <T> T getOrDefault(T value, T defaultValue) {
        return (value == null ? defaultValue : value);
    }

    private static DeploymentOptions toDeploymentOptions(Map<String, Object> map) {
        if (map != null) {
            return new DeploymentOptions(new JsonObject(map));
        }
        return null;
    }

    private static <T> T toVerticleConfig(DeploymentOptions deploymentOpts, Class<T> clazz) {
        if (deploymentOpts != null) {
            return deploymentOpts.getConfig().mapTo(clazz);
        }
        return null;
    }

    private static HttpServerOptions toHttpServerOptions(Map<String, Object> map) {
        if(map != null) {
            return new HttpServerOptions(new JsonObject(map));
        }
        return null;
    }
}
