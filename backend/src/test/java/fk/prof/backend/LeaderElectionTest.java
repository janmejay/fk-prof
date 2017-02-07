package fk.prof.backend;

import fk.prof.backend.service.ProfileWorkService;
import fk.prof.backend.verticles.leader.election.IPAddressUtil;
import fk.prof.backend.verticles.leader.election.LeaderDiscoveryStore;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.apache.curator.test.Timing;
import org.junit.*;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(VertxUnitRunner.class)
public class LeaderElectionTest {

  private Vertx vertx;
  private Integer port;
  private JsonObject vertxConfig;
  private DeploymentOptions leaderDeploymentOptions;
  private DeploymentOptions aggregatorDeploymentOptions;

  private TestingServer testingServer;
  private CuratorFramework curatorClient;

  @Before
  public void setUp(TestContext context) throws Exception {
    ConfigManager.setDefaultSystemProperties();
    JsonObject config = ConfigManager.loadFileAsJson(ProfileApiTest.class.getClassLoader().getResource("config.json").getFile());
    vertxConfig = ConfigManager.getVertxConfig(config);

    JsonObject leaderDeploymentConfig = ConfigManager.getLeaderDeploymentConfig(config);
    assert leaderDeploymentConfig != null;

    JsonObject aggregatorDeploymentConfig = ConfigManager.getAggregatorDeploymentConfig(config);
    assert aggregatorDeploymentConfig != null;

    JsonObject curatorConfig = ConfigManager.getCuratorConfig(config);
    assert curatorConfig != null;

    port = leaderDeploymentConfig.getJsonObject("config").getInteger("http.port");
    leaderDeploymentOptions = new DeploymentOptions(leaderDeploymentConfig);
    aggregatorDeploymentOptions = new DeploymentOptions(aggregatorDeploymentConfig);

    testingServer = new TestingServer();
    curatorClient = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), 500, 500, new RetryOneTime(1));
    curatorClient.start();
//    curatorClient = curatorClient.usingNamespace("fkprof");
    curatorClient.blockUntilConnected(10, TimeUnit.SECONDS);
  }

  @After
  public void tearDown(TestContext context) throws IOException {
    System.out.println("Tearing down");
    VertxManager.close(vertx).setHandler(result -> {
      System.out.println("Vertx shutdown");
      curatorClient.close();
      try { testingServer.close(); } catch (IOException ex) {}
      if(result.failed()) {
        context.fail(result.cause());
      }
    });
  }

  @Test(timeout = 20000)
  public void leaderTaskTriggerOnLeaderElection(TestContext testContext) throws InterruptedException {
    vertx = vertxConfig != null ? Vertx.vertx(new VertxOptions(vertxConfig)) : Vertx.vertx();
    CountDownLatch latch = new CountDownLatch(1);
    Runnable leaderElectedTask = () -> {
      latch.countDown();
    };
    LeaderDiscoveryStore leaderDiscoveryStore = VertxManager.getDefaultLeaderDiscoveryStore(vertx);

    Thread.sleep(1000);
    VertxManager.deployLeaderElectionWorkerVerticles(
        vertx,
        leaderDeploymentOptions,
        curatorClient,
        leaderElectedTask,
        leaderDiscoveryStore
    );

    boolean released = latch.await(10, TimeUnit.SECONDS);
    if(!released) {
      testContext.fail("Latch timed out but leader election task was not run");
    }
  }

  @Test(timeout = 20000)
  public void leaderDiscoveryUpdateOnLeaderElection(TestContext testContext) throws InterruptedException {
    vertx = vertxConfig != null ? Vertx.vertx(new VertxOptions(vertxConfig)) : Vertx.vertx();
    CountDownLatch latch = new CountDownLatch(1);
    Runnable leaderElectedTask = VertxManager.getDefaultLeaderElectedTask(vertx, true, null);
    LeaderDiscoveryStore leaderDiscoveryStore = new LeaderDiscoveryStore() {
      private String address = null;
      private boolean self = false;

      @Override
      public void setLeaderAddress(String ipAddress) {
        address = ipAddress;
        self = ipAddress != null && ipAddress.equals(IPAddressUtil.getIPAddressAsString());
        if(address != null) {
          latch.countDown();
        }
      }

      @Override
      public String getLeaderAddress() {
        return address;
      }

      @Override
      public boolean isLeader() {
        return self;
      }
    };

    Thread.sleep(1000);
    VertxManager.deployLeaderElectionWorkerVerticles(
        vertx,
        leaderDeploymentOptions,
        curatorClient,
        leaderElectedTask,
        leaderDiscoveryStore
    );

    boolean released = latch.await(10, TimeUnit.SECONDS);
    if(!released) {
      testContext.fail("Latch timed out but leader discovery store was not updated with leader address");
    } else {
      testContext.assertEquals(IPAddressUtil.getIPAddressAsString(), leaderDiscoveryStore.getLeaderAddress());
      testContext.assertTrue(leaderDiscoveryStore.isLeader());
    }
  }

  @Test(timeout = 20000)
  public void leaderElectionAssertionsWithDefaults(TestContext testContext) throws InterruptedException {
    vertx = vertxConfig != null ? Vertx.vertx(new VertxOptions(vertxConfig)) : Vertx.vertx();
    ProfileWorkService profileWorkService = new ProfileWorkService();
    List<String> aggregatorDeployments = new ArrayList<>();

    CompositeFuture aggDepFut = VertxManager.deployAggregatorHttpVerticles(vertx, aggregatorDeploymentOptions, profileWorkService);
    CountDownLatch aggDepLatch = new CountDownLatch(1);
    aggDepFut.setHandler(asyncResult -> {
      if(asyncResult.succeeded()) {
        aggregatorDeployments.addAll(asyncResult.result().list());
        aggDepLatch.countDown();
      } else {
        testContext.fail(asyncResult.cause());
      }
    });

    boolean aggDepLatchReleased = aggDepLatch.await(10, TimeUnit.SECONDS);
    if(!aggDepLatchReleased) {
      testContext.fail("Latch timed out but aggregation verticles were not deployed");
    } else {

      CountDownLatch leaderElectionLatch = new CountDownLatch(1);
      CountDownLatch leaderWatchedLatch = new CountDownLatch(1);

      Runnable defaultLeaderElectedTask = VertxManager.getDefaultLeaderElectedTask(vertx, false, aggregatorDeployments);
      Runnable wrappedLeaderElectedTask = () -> {
        defaultLeaderElectedTask.run();
        leaderElectionLatch.countDown();
      };

      LeaderDiscoveryStore defaultLeaderDiscoveryStore = VertxManager.getDefaultLeaderDiscoveryStore(vertx);
      LeaderDiscoveryStore wrappedLeaderDiscoveryStore = new LeaderDiscoveryStore() {
        private LeaderDiscoveryStore toWrap;
        @Override
        public void setLeaderAddress(String ipAddress) {
          toWrap.setLeaderAddress(ipAddress);
          if(ipAddress != null) {
            leaderWatchedLatch.countDown();
          }
        }

        @Override
        public String getLeaderAddress() {
          return toWrap.getLeaderAddress();
        }

        @Override
        public boolean isLeader() {
          return toWrap.isLeader();
        }

        public LeaderDiscoveryStore initialize(LeaderDiscoveryStore toWrap) {
          this.toWrap = toWrap;
          return this;
        }
      }.initialize(defaultLeaderDiscoveryStore);

      Thread.sleep(1000);
      VertxManager.deployLeaderElectionWorkerVerticles(
          vertx,
          leaderDeploymentOptions,
          curatorClient,
          wrappedLeaderElectedTask,
          wrappedLeaderDiscoveryStore
      );

      boolean leaderElectionLatchReleased = leaderElectionLatch.await(10, TimeUnit.SECONDS);
      Thread.sleep(2000); //wait for some time for aggregator verticles to be undeployed
      if(!leaderElectionLatchReleased) {
        testContext.fail("Latch timed out but leader election task was not run");
      } else {
        //Ensure aggregator verticles have been undeployed
        for(String aggDep: aggregatorDeployments) {
          testContext.assertFalse(vertx.deploymentIDs().contains(aggDep));
        }
      }

      boolean leaderWatchedLatchReleased = leaderWatchedLatch.await(10, TimeUnit.SECONDS);
      if(!leaderWatchedLatchReleased) {
        testContext.fail("Latch timed out but leader discovery store was not updated with leader address");
      } else {
        testContext.assertNotNull(defaultLeaderDiscoveryStore.getLeaderAddress());
        testContext.assertTrue(defaultLeaderDiscoveryStore.isLeader());
      }

    }
  }

}
