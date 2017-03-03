package fk.prof.backend;

import fk.prof.backend.deployer.VerticleDeployer;
import fk.prof.backend.deployer.impl.BackendHttpVerticleDeployer;
import fk.prof.backend.deployer.impl.LeaderElectionParticipatorVerticleDeployer;
import fk.prof.backend.deployer.impl.LeaderElectionWatcherVerticleDeployer;
import fk.prof.backend.deployer.impl.LeaderHttpVerticleDeployer;
import fk.prof.backend.leader.election.LeaderElectedTask;
import fk.prof.backend.mock.MockLeaderStores;
import fk.prof.backend.model.aggregation.AggregationWindowLookupStore;
import fk.prof.backend.model.assignment.ProcessGroupAssociationStore;
import fk.prof.backend.model.assignment.impl.ProcessGroupAssociationStoreImpl;
import fk.prof.backend.model.election.LeaderWriteContext;
import fk.prof.backend.model.election.impl.InMemoryLeaderStore;
import fk.prof.backend.model.aggregation.impl.AggregationWindowLookupStoreImpl;
import io.vertx.core.*;
import io.vertx.core.impl.CompositeFutureImpl;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(VertxUnitRunner.class)
public class LeaderElectionTest {

  private Vertx vertx;
  private ConfigManager configManager;

  private TestingServer testingServer;
  private CuratorFramework curatorClient;

  @Before
  public void setUp(TestContext context) throws Exception {
    ConfigManager.setDefaultSystemProperties();
    configManager = new ConfigManager(LeaderElectionTest.class.getClassLoader().getResource("config.json").getFile());

    testingServer = new TestingServer();
    curatorClient = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), 500, 500, new RetryOneTime(1));
    curatorClient.start();
    curatorClient.blockUntilConnected(10, TimeUnit.SECONDS);
  }

  @After
  public void tearDown(TestContext context) throws IOException {
    System.out.println("Tearing down");
    vertx.close(result -> {
      System.out.println("Vertx shutdown");
      curatorClient.close();
      try {
        testingServer.close();
      } catch (IOException ex) {
      }
      if (result.failed()) {
        context.fail(result.cause());
      }
    });
  }

  @Test(timeout = 20000)
  public void leaderTaskTriggerOnLeaderElection(TestContext testContext) throws InterruptedException {
    vertx = Vertx.vertx(new VertxOptions(configManager.getVertxConfig()));
    CountDownLatch latch = new CountDownLatch(1);
    Runnable leaderElectedTask = () -> {
      latch.countDown();
    };
    LeaderWriteContext leaderWriteContext = new InMemoryLeaderStore(configManager.getIPAddress());

    VerticleDeployer leaderParticipatorDeployer = new LeaderElectionParticipatorVerticleDeployer(vertx, configManager, curatorClient, leaderElectedTask);
    VerticleDeployer leaderWatcherDeployer = new LeaderElectionWatcherVerticleDeployer(vertx, configManager, curatorClient, leaderWriteContext);

    leaderParticipatorDeployer.deploy();
    leaderWatcherDeployer.deploy();

    boolean released = latch.await(10, TimeUnit.SECONDS);
    if (!released) {
      testContext.fail("Latch timed out but leader election task was not run");
    }
  }

  @Test(timeout = 20000)
  public void leaderUpdateOnLeaderElection(TestContext testContext) throws InterruptedException {
    vertx = Vertx.vertx(new VertxOptions(configManager.getVertxConfig()));
    CountDownLatch latch = new CountDownLatch(1);
    Runnable leaderElectedTask = () -> {};
    MockLeaderStores.TestLeaderStore leaderStore = new MockLeaderStores.TestLeaderStore(configManager.getIPAddress(), latch);

    VerticleDeployer leaderParticipatorDeployer = new LeaderElectionParticipatorVerticleDeployer(vertx, configManager, curatorClient, leaderElectedTask);
    VerticleDeployer leaderWatcherDeployer = new LeaderElectionWatcherVerticleDeployer(vertx, configManager, curatorClient, leaderStore);

    leaderParticipatorDeployer.deploy();
    leaderWatcherDeployer.deploy();

    boolean released = latch.await(10, TimeUnit.SECONDS);
    if (!released) {
      testContext.fail("Latch timed out but leader store was not updated with leader address");
    } else {
      testContext.assertEquals(configManager.getIPAddress(), leaderStore.getLeaderIPAddress());
      testContext.assertTrue(leaderStore.isLeader());
    }
  }

  @Test(timeout = 20000)
  public void leaderElectionAssertionsWithDisablingOfBackendDuties(TestContext testContext) throws InterruptedException {
    vertx = Vertx.vertx(new VertxOptions(configManager.getVertxConfig()));
    AggregationWindowLookupStore aggregationWindowLookupStore = new AggregationWindowLookupStoreImpl();
    ProcessGroupAssociationStore processGroupAssociationStore = new ProcessGroupAssociationStoreImpl(configManager.getRecorderDefunctThresholdInSeconds());
    InMemoryLeaderStore leaderStore = new InMemoryLeaderStore(configManager.getIPAddress());
    List<String> backendDeployments = new ArrayList<>();
    CountDownLatch aggDepLatch = new CountDownLatch(1);

    VerticleDeployer backendVerticleDeployer = new BackendHttpVerticleDeployer(vertx, configManager, leaderStore, aggregationWindowLookupStore, processGroupAssociationStore);
    backendVerticleDeployer.deploy().setHandler(asyncResult -> {
      if (asyncResult.succeeded()) {
        backendDeployments.addAll(asyncResult.result().list());
        aggDepLatch.countDown();
      } else {
        testContext.fail(asyncResult.cause());
      }
    });

    boolean aggDepLatchReleased = aggDepLatch.await(10, TimeUnit.SECONDS);
    if (!aggDepLatchReleased) {
      testContext.fail("Latch timed out but aggregation verticles were not deployed");
    } else {
      CountDownLatch leaderElectionLatch = new CountDownLatch(1);
      CountDownLatch leaderWatchedLatch = new CountDownLatch(1);

      VerticleDeployer leaderHttpDeployer = mock(LeaderHttpVerticleDeployer.class);
      when(leaderHttpDeployer.deploy()).thenReturn(CompositeFutureImpl.all(Future.succeededFuture()));
      Runnable leaderElectedTask = LeaderElectedTask.newBuilder().disableBackend(backendDeployments).build(vertx, leaderHttpDeployer);
      Runnable wrappedLeaderElectedTask = () -> {
        leaderElectedTask.run();
        leaderElectionLatch.countDown();
      };
      LeaderWriteContext leaderWriteContext = new MockLeaderStores.WrappedLeaderStore(leaderStore, leaderWatchedLatch);

      VerticleDeployer leaderParticipatorDeployer = new LeaderElectionParticipatorVerticleDeployer(vertx, configManager, curatorClient, wrappedLeaderElectedTask);
      VerticleDeployer leaderWatcherDeployer = new LeaderElectionWatcherVerticleDeployer(vertx, configManager, curatorClient, leaderWriteContext);
      leaderParticipatorDeployer.deploy();
      leaderWatcherDeployer.deploy();

      boolean leaderElectionLatchReleased = leaderElectionLatch.await(10, TimeUnit.SECONDS);
      Thread.sleep(2000); //wait for some time for aggregator verticles to be undeployed
      if (!leaderElectionLatchReleased) {
        testContext.fail("Latch timed out but leader election task was not run");
      } else {
        //Ensure aggregator verticles have been undeployed
        for (String aggDep : backendDeployments) {
          testContext.assertFalse(vertx.deploymentIDs().contains(aggDep));
        }
      }

      boolean leaderWatchedLatchReleased = leaderWatchedLatch.await(10, TimeUnit.SECONDS);
      if (!leaderWatchedLatchReleased) {
        testContext.fail("Latch timed out but leader store was not updated with leader address");
      } else {
        testContext.assertNotNull(leaderStore.getLeaderIPAddress());
        testContext.assertTrue(leaderStore.isLeader());
      }

    }
  }

}
