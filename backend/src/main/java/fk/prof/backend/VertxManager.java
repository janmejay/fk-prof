package fk.prof.backend;

import com.codahale.metrics.SharedMetricRegistries;
import com.google.common.io.Files;
import fk.prof.backend.service.IProfileWorkService;
import fk.prof.backend.verticles.http.HttpVerticle;
import fk.prof.backend.service.ProfileWorkService;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.dropwizard.Match;
import io.vertx.ext.dropwizard.MatchType;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * TODO: Deployment process is liable to changes later
 * Right now http verticles are injected with profileworkservice instance and started directly
 */
public class VertxManager {
  private static Logger logger = LoggerFactory.getLogger(VertxManager.class);

  public static Future<Void> launch(Vertx vertx, JsonObject confJson) {
    Future future = Future.future();

    DeploymentOptions deploymentOptions = new DeploymentOptions(confJson);
    ProfileWorkService profileWorkService = new ProfileWorkService();

    int httpVerticleCount = confJson.getInteger("http.instances", 1);
    if(httpVerticleCount < 1) {
      httpVerticleCount = 1;
    }
    CompositeFuture deploymentFuture = deployHttpVerticles(vertx, deploymentOptions, httpVerticleCount, profileWorkService);
    deploymentFuture.setHandler(future.completer());

    return future;
  }

  public static Future<Void> close(Vertx vertx) {
    Future future = Future.future();
    vertx.close(closeResult -> {
      if(closeResult.succeeded()) {
        logger.info("Shutdown successful for vertx instance");
        future.complete();
      } else {
        logger.error("Error shutting down vertx instance");
        future.fail(closeResult.cause());
      }
    });

    return future;
  }

  public static CompositeFuture deployHttpVerticles(Vertx vertx, DeploymentOptions deploymentOptions, int instancesCount, IProfileWorkService profileWorkService) {
    List<Future> deployFutures = new ArrayList<>();
    for(int i = 1;i <= instancesCount;i++) {
      Future<Void> deployFuture = Future.future();
      deployFutures.add(deployFuture);

      Verticle httpVerticle = new HttpVerticle(profileWorkService);
      vertx.deployVerticle(httpVerticle, deploymentOptions, deployResult -> {
        if(deployResult.succeeded()) {
          logger.info("Deployment of HttpVerticle succeeded with deploymentId = " + deployResult.result());
          deployFuture.complete();
        } else {
          logger.error("Deployment of HttpVerticle failed", deployResult.cause());
          deployFuture.fail(deployResult.cause());
        }
      });
    }

    return CompositeFuture.all(deployFutures);
  }

  public static Vertx setup(JsonObject vertxConfig) throws IOException {
    VertxOptions vertxOptions = vertxConfig != null
        ? new VertxOptions(vertxConfig)
        : new VertxOptions();

    vertxOptions.setMetricsOptions(new DropwizardMetricsOptions()
        .setEnabled(true)
        .setJmxEnabled(true)
        .setRegistryName(ConfigManager.METRIC_REGISTRY)
        .addMonitoredHttpServerUri(new Match().setValue("/.*").setType(MatchType.REGEX)));
    return Vertx.vertx(vertxOptions);
  }

}
