package fk.prof.userapi.verticles;

import fk.prof.userapi.api.ProfileStoreAPIImpl;
import fk.prof.userapi.model.StorageFactory;
import io.vertx.core.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Deploys instances of {@link HttpVerticle} based on the number provided in the config in the following structure:
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
        int profileRetentionDuration = config().getInteger("profile.retention.duration");

        List<Future> futureList = new ArrayList<>();
        for (int i = 0; i < numInstances; i++) {
            Future<String> future = Future.future();
            futureList.add(future);
            Verticle routerVerticle = new HttpVerticle(new ProfileStoreAPIImpl(vertx, StorageFactory.getAsyncStorage(config()), profileRetentionDuration));
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
