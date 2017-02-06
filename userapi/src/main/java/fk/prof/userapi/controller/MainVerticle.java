package fk.prof.userapi.controller;

import fk.prof.userapi.discovery.ProfileDiscoveryAPIImpl;
import fk.prof.userapi.model.StorageFactory;
import io.vertx.core.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Deploys instances of {@link RouterVerticle} based on the number provided in the config in the following structure:
 * <pre>
 * {@code
 * {
 * "http.port": 8082,
 * "http.instances": 1,
 * "req.timeout": 2000,
 * "storage":"S3",
 * }
 *  }
 * </pre>
 * Created by rohit.patiyal on 02/02/17.
 */
public class MainVerticle extends AbstractVerticle {
    @Override
    public void start(Future<Void> startFuture) throws Exception {
        int numInstances = config().getInteger("http.instances");
        List<Future> futureList = new ArrayList<>();
        for (int i = 0; i < numInstances; i++) {
            Future<String> future = Future.future();
            futureList.add(future);
            Verticle routerVerticle = new RouterVerticle(new ProfileDiscoveryAPIImpl(StorageFactory.getAsyncStorage(config())));
            vertx.deployVerticle(routerVerticle, new DeploymentOptions().setConfig(config()), future.completer());
        }
        CompositeFuture.all(futureList).setHandler(event -> {
            if (event.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(event.cause());
            }
        });
    }
}
