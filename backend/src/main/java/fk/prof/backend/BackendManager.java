package fk.prof.backend;

import com.codahale.metrics.Counter;
import com.codahale.metrics.InstrumentedExecutorService;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.google.common.base.Preconditions;
import fk.prof.aggregation.model.AggregationWindowStorage;
import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.deployer.impl.*;
import fk.prof.backend.http.ApiPathConstants;
import fk.prof.backend.leader.election.LeaderElectedTask;
import fk.prof.backend.model.aggregation.ActiveAggregationWindows;
import fk.prof.backend.model.assignment.AssociatedProcessGroups;
import fk.prof.backend.model.assignment.impl.AssociatedProcessGroupsImpl;
import fk.prof.backend.model.slot.WorkSlotPool;
import fk.prof.backend.model.association.BackendAssociationStore;
import fk.prof.backend.model.association.ProcessGroupCountBasedBackendComparator;
import fk.prof.backend.model.association.impl.ZookeeperBasedBackendAssociationStore;
import fk.prof.backend.model.election.impl.InMemoryLeaderStore;
import fk.prof.backend.model.aggregation.impl.ActiveAggregationWindowsImpl;
import fk.prof.backend.model.policy.PolicyStore;
import fk.prof.backend.proto.BackendDTO;
import fk.prof.storage.AsyncStorage;
import fk.prof.storage.S3AsyncStorage;
import fk.prof.storage.buffer.ByteBufferPoolFactory;
import io.vertx.core.*;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.dropwizard.Match;
import io.vertx.ext.dropwizard.MatchType;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import recording.Recorder;
import org.apache.zookeeper.KeeperException;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class BackendManager {
  private static Logger logger = LoggerFactory.getLogger(BackendManager.class);

  private final Vertx vertx;
  private final ConfigManager configManager;
  private final CuratorFramework curatorClient;
  private AsyncStorage storage;
  private GenericObjectPool<ByteBuffer> bufferPool;

  public BackendManager(String configFilePath) throws Exception {
    this(new ConfigManager(configFilePath));
  }

  public BackendManager(ConfigManager configManager) throws Exception {
    ConfigManager.setDefaultSystemProperties();
    this.configManager = Preconditions.checkNotNull(configManager);

    VertxOptions vertxOptions = new VertxOptions(configManager.getVertxConfig());
    vertxOptions.setMetricsOptions(buildMetricsOptions());
    this.vertx = Vertx.vertx(vertxOptions);

    this.curatorClient = createCuratorClient();
    curatorClient.start();
    curatorClient.blockUntilConnected(configManager.getCuratorConfig().getInteger("connection.timeout.ms", 10000), TimeUnit.MILLISECONDS);
    ensureRequiredZkNodesPresent();

    initStorage();
  }

  public Future<Void> close() {
    Future future = Future.future();
    vertx.close(closeResult -> {
      if (closeResult.succeeded()) {
        logger.info("Shutdown successful for vertx instance");
        curatorClient.close();
        future.complete();
      } else {
        logger.error("Error shutting down vertx instance");
        future.fail(closeResult.cause());
      }
    });

    return future;
  }

  public Future<Void> launch() {
    Future result = Future.future();
    InMemoryLeaderStore leaderStore = new InMemoryLeaderStore(configManager.getIPAddress(), configManager.getLeaderHttpPort());
    ActiveAggregationWindows activeAggregationWindows = new ActiveAggregationWindowsImpl();
    AssociatedProcessGroups associatedProcessGroups = new AssociatedProcessGroupsImpl(configManager.getRecorderDefunctThresholdInSeconds());
    WorkSlotPool workSlotPool = new WorkSlotPool(configManager.getSlotPoolCapacity());
    AggregationWindowStorage aggregationWindowStorage = new AggregationWindowStorage(configManager.getStorageConfig().getString("base.dir", "profiles"), storage, bufferPool);

    VerticleDeployer backendHttpVerticleDeployer = new BackendHttpVerticleDeployer(vertx, configManager, leaderStore, activeAggregationWindows, associatedProcessGroups);
    VerticleDeployer backendDaemonVerticleDeployer = new BackendDaemonVerticleDeployer(vertx, configManager, leaderStore, associatedProcessGroups, activeAggregationWindows, workSlotPool, aggregationWindowStorage);
    CompositeFuture backendDeploymentFuture = CompositeFuture.all(backendHttpVerticleDeployer.deploy(), backendDaemonVerticleDeployer.deploy());
    backendDeploymentFuture.setHandler(backendDeployResult -> {
      if (backendDeployResult.succeeded()) {
        try {
          List<String> backendDeployments = backendDeployResult.result().list().stream()
              .flatMap(fut -> ((CompositeFuture)fut).list().stream())
              .map(deployment -> (String)deployment)
              .collect(Collectors.toList());

          BackendAssociationStore backendAssociationStore = createBackendAssociationStore(vertx, curatorClient);
          PolicyStore policyStore = new PolicyStore();

          //TODO: Remove. Temporary for e2e testing
          Recorder.ProcessGroup testProcessGroup = Recorder.ProcessGroup.newBuilder().setAppId("bar-app").setCluster("quux-cluster").setProcName("grault-proc").build();
          BackendDTO.RecordingPolicy recordingPolicy = buildRecordingPolicy(15);
          policyStore.put(testProcessGroup, recordingPolicy);

          VerticleDeployer leaderHttpVerticleDeployer = new LeaderHttpVerticleDeployer(vertx, configManager, backendAssociationStore, policyStore);
          Runnable leaderElectedTask = createLeaderElectedTask(vertx, leaderHttpVerticleDeployer, backendDeployments);

          VerticleDeployer leaderElectionParticipatorVerticleDeployer = new LeaderElectionParticipatorVerticleDeployer(
              vertx, configManager, curatorClient, leaderElectedTask);
          VerticleDeployer leaderElectionWatcherVerticleDeployer = new LeaderElectionWatcherVerticleDeployer(vertx, configManager, curatorClient, leaderStore);

          CompositeFuture leaderDeployFuture = CompositeFuture.all(
              leaderElectionParticipatorVerticleDeployer.deploy(), leaderElectionWatcherVerticleDeployer.deploy());
          leaderDeployFuture.setHandler(leaderDeployResult -> {
            if(leaderDeployResult.succeeded()) {
              result.complete();
            } else {
              result.fail(leaderDeployResult.cause());
            }
          });
        } catch (Exception ex) {
          result.fail(ex);
        }
      } else {
        result.fail(backendDeployResult.cause());
      }
    });

    return result;
  }

  private void initStorage() {
    JsonObject s3Config = configManager.getS3Config();
    JsonObject threadPoolConfig = configManager.getStorageThreadPoolConfig();
    MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(ConfigManager.METRIC_REGISTRY);
    Counter threadPoolRejectionsCounter = metricRegistry.counter(MetricRegistry.name(S3AsyncStorage.class, "threadpool.rejections"));

    // thread pool with bounded queue for s3 io.
    BlockingQueue ioTaskQueue = new LinkedBlockingQueue(threadPoolConfig.getInteger("queue.maxsize"));
    ExecutorService storageExecSvc = new InstrumentedExecutorService(
        new ThreadPoolExecutor(threadPoolConfig.getInteger("coresize"), threadPoolConfig.getInteger("maxsize"), threadPoolConfig.getInteger("idletime.secs"), TimeUnit.SECONDS, ioTaskQueue,
                new AbortPolicy("s3ExectorSvc", threadPoolRejectionsCounter)),
        metricRegistry, "executors.fixed_thread_pool.storage");

    this.storage = new S3AsyncStorage(s3Config.getString("endpoint"), s3Config.getString("access.key"), s3Config.getString("secret.key"),
        storageExecSvc);

    // buffer pool to temporarily store serialized bytes
    JsonObject bufferPoolConfig = configManager.getBufferPoolConfig();
    GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
    poolConfig.setMaxTotal(bufferPoolConfig.getInteger("max.total"));
    poolConfig.setMaxIdle(bufferPoolConfig.getInteger("max.idle"));

    this.bufferPool = new GenericObjectPool<>(new ByteBufferPoolFactory(bufferPoolConfig.getInteger("buffer.size"), false), poolConfig);
  }

  private CuratorFramework createCuratorClient() {
    JsonObject curatorConfig = configManager.getCuratorConfig();
    return CuratorFrameworkFactory.builder()
        .connectString(curatorConfig.getString("connection.url"))
        .retryPolicy(new ExponentialBackoffRetry(1000, curatorConfig.getInteger("max.retries", 3)))
        .connectionTimeoutMs(curatorConfig.getInteger("connection.timeout.ms", 10000))
        .sessionTimeoutMs(curatorConfig.getInteger("session.timeout.ms", 60000))
        .namespace(curatorConfig.getString("namespace", "fkprof"))
        .build();
  }

  private void ensureRequiredZkNodesPresent() throws  Exception {
    try {
      curatorClient.create().forPath(configManager.getLeaderHttpDeploymentConfig().getString("backend.association.path", "/association"));
    } catch (KeeperException.NodeExistsException ex) {
      logger.warn(ex);
    }
  }

  private BackendAssociationStore createBackendAssociationStore(
      Vertx vertx, CuratorFramework curatorClient)
      throws Exception {
    int loadReportIntervalInSeconds = configManager.getLoadReportIntervalInSeconds();
    JsonObject leaderHttpDeploymentConfig = configManager.getLeaderHttpDeploymentConfig();
    String backendAssociationPath = leaderHttpDeploymentConfig.getString("backend.association.path", "/association");
    int loadMissTolerance = leaderHttpDeploymentConfig.getInteger("load.miss.tolerance", 2);
    return new ZookeeperBasedBackendAssociationStore(vertx, curatorClient, backendAssociationPath,
        loadReportIntervalInSeconds, loadMissTolerance, new ProcessGroupCountBasedBackendComparator());
  }

  private Runnable createLeaderElectedTask(Vertx vertx, VerticleDeployer leaderHttpVerticleDeployer, List<String> backendDeployments) {
    LeaderElectedTask.Builder builder = LeaderElectedTask.newBuilder();
    builder.disableBackend(backendDeployments);
    return builder.build(vertx, leaderHttpVerticleDeployer);
  }

  private MetricsOptions buildMetricsOptions() {
    MetricsOptions metricsOptions = new DropwizardMetricsOptions()
        .setEnabled(true)
        .setJmxEnabled(true)
        .setRegistryName(ConfigManager.METRIC_REGISTRY)
        .addMonitoredHttpServerUri(new Match().setValue(ApiPathConstants.AGGREGATOR_POST_PROFILE).setType(MatchType.EQUALS))
        .addMonitoredHttpServerUri(new Match().setValue(ApiPathConstants.BACKEND_POST_POLL).setType(MatchType.EQUALS))
        .addMonitoredHttpServerUri(new Match().setValue(ApiPathConstants.BACKEND_POST_ASSOCIATION).setType(MatchType.EQUALS))
        .addMonitoredHttpServerUri(new Match().setValue(ApiPathConstants.LEADER_POST_ASSOCIATION).setType(MatchType.EQUALS))
        .addMonitoredHttpServerUri(new Match().setValue(ApiPathConstants.LEADER_POST_LOAD).setType(MatchType.EQUALS))
        .addMonitoredHttpServerUri(new Match().setValue(ApiPathConstants.LEADER_GET_WORK + ".*").setAlias(ApiPathConstants.LEADER_GET_WORK).setType(MatchType.REGEX));
    return metricsOptions;
  }

  //TODO: Ugly hack for e2e testing, remove!
  private BackendDTO.RecordingPolicy buildRecordingPolicy(int profileDuration) {
    return BackendDTO.RecordingPolicy.newBuilder()
        .setDuration(profileDuration)
        .setCoveragePct(100)
        .setDescription("Test work profile")
        .addWork(BackendDTO.Work.newBuilder()
            .setWType(BackendDTO.WorkType.cpu_sample_work)
            .setCpuSample(BackendDTO.CpuSampleWork.newBuilder().setFrequency(50).setMaxFrames(64))
            .build())
        .build();
  }

  public static class AbortPolicy implements RejectedExecutionHandler {

    private String forExecutorSvc;
    private Counter counter;

    public AbortPolicy(String forExecutorSvc, Counter counter) {
      this.forExecutorSvc = forExecutorSvc;
      this.counter = counter;
    }

    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
      counter.inc();
      throw new RejectedExecutionException("Task rejected from " + forExecutorSvc);
    }
  }
}
