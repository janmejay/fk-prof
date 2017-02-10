package fk.prof.backend;

import fk.prof.backend.model.association.BackendAssociationStore;
import fk.prof.backend.model.association.ReportLoadPayload;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
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

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@RunWith(VertxUnitRunner.class)
public class LeaderAPILoadAndAssociationTest {
  private Vertx vertx;
  private Integer port;
  private DeploymentOptions leaderHttpDeploymentOptions;

  private TestingServer testingServer;
  private CuratorFramework curatorClient;


  @Before
  public void setBefore(TestContext context) throws Exception {
    ConfigManager.setDefaultSystemProperties();

    testingServer = new TestingServer();
    curatorClient = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), 500, 500, new RetryOneTime(1));
    curatorClient.start();
    curatorClient.blockUntilConnected(10, TimeUnit.SECONDS);

    JsonObject config = ConfigManager.loadFileAsJson(ProfileApiTest.class.getClassLoader().getResource("config.json").getFile());
    JsonObject vertxConfig = ConfigManager.getVertxConfig(config);

    vertx = vertxConfig != null ? Vertx.vertx(new VertxOptions(vertxConfig)) : Vertx.vertx();
    port = ConfigManager.getHttpPort(config);

    JsonObject leaderHttpConfig = ConfigManager.getLeaderHttpDeploymentConfig(config);
    assert leaderHttpConfig != null;
    String backendAssociationPath = leaderHttpConfig.getString("backend.association.path");
    curatorClient.create().forPath(backendAssociationPath);

    leaderHttpDeploymentOptions = new DeploymentOptions(leaderHttpConfig);
    BackendAssociationStore backendAssociationStore = VertxManager.getDefaultBackendAssociationStore(
        vertx,
        curatorClient,
        backendAssociationPath,
        ConfigManager.getLoadReportIntervalInSeconds(config),
        leaderHttpConfig.getInteger("allowed.report.skips")
    );

    VertxManager.deployLeaderHttpVerticles(vertx, port, leaderHttpDeploymentOptions, curatorClient, backendAssociationStore);
  }

  @After
  public void tearDown(TestContext context) throws IOException {
    System.out.println("Tearing down");
    VertxManager.close(vertx).setHandler(result -> {
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

  @Test(timeout = 5000)
  public void test(TestContext context) {
    final Async async = context.async();
    HttpClientRequest request = vertx.createHttpClient()
        .post(port, "localhost", "/leader/load")
        .putHeader("content-type", "application/json")
        .handler(response -> {
          response.bodyHandler(buffer -> {
            System.out.println(buffer.toString());
            async.complete();
          });
        });
    request.end(Json.encode(new ReportLoadPayload(0.5)));
  }

}
