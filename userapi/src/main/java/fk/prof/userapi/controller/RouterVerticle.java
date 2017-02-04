package fk.prof.userapi.controller;

import fk.prof.userapi.discovery.ProfileDiscoveryAPI;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.VoidHandler;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.TimeoutHandler;

import java.util.Set;

/**
 * Routes requests to their respective handlers
 * Created by rohit.patiyal on 18/01/17.
 */
public class RouterVerticle extends AbstractVerticle {

    private ProfileDiscoveryAPI profileDiscoveryAPI = null;

    RouterVerticle(ProfileDiscoveryAPI profileDiscoveryAPI) {
        this.profileDiscoveryAPI = profileDiscoveryAPI;
    }

    private Router configureRouter() {
        Router router = Router.router(vertx);
        router.route().handler(TimeoutHandler.create(config().getInteger("req.timeout")));
        router.route("/").handler(routingContext -> routingContext.response()
                .putHeader("context-type", "text/html")
                .end("<h1>Welcome to UserAPI for FKProfiler"));
        router.get("/apps").handler(this::getAppIds);
        router.get("/cluster/:appId").handler(this::getClusterIds);
        router.get("/proc/:appId/:clusterId").handler(this::getProcs);
        router.get("/profiles/:appId/:clusterId/:proc").handler(this::getProfiles);
        return router;
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        Router router = configureRouter();
        Future<Void> serverFuture = Future.future();
        Future<Void> dbFuture = Future.future();
        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(config().getInteger("http.port", 8080), event -> {
                    if (event.succeeded()) {
                        startFuture.complete();
                    } else {
                        startFuture.fail(event.cause());
                    }
                });

        vertx.executeBlocking(Future::complete, dbFuture.completer());

        CompositeFuture.all(serverFuture, dbFuture).setHandler(compositeFutureAsyncResult -> {
            if (compositeFutureAsyncResult.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(compositeFutureAsyncResult.cause());
            }
        });
    }

    private void returnResponse(Set<?> responseContent, Throwable throwable, RoutingContext routingContext) {
        routingContext.response().
                putHeader("content-type", "application/json; charset=utf-8").
                end(Json.encodePrettily(responseContent));
        if (throwable != null) {
            routingContext.response().setStatusCode(HttpResponseStatus.SERVICE_UNAVAILABLE.code()).end();
        }
    }

    private void getAppIds(RoutingContext routingContext) {
        String prefix = routingContext.request().getParam("prefix");
        if (prefix == null) {
            prefix = "";
        }
        profileDiscoveryAPI.getAppIdsWithPrefix(prefix).whenComplete((appIds, throwable) -> vertx.runOnContext(new VoidHandler() {
            @Override
            protected void handle() {
                returnResponse(appIds, throwable, routingContext);
            }
        }));
    }

    private void getClusterIds(RoutingContext routingContext) {
        final String appId = routingContext.request().getParam("appId");
        String prefix = routingContext.request().getParam("prefix");
        if (prefix == null) {
            prefix = "";
        }
        profileDiscoveryAPI.getClusterIdsWithPrefix(appId, prefix).whenComplete((clusterIds, throwable) -> vertx.runOnContext(new VoidHandler() {
            @Override
            protected void handle() {
                returnResponse(clusterIds, throwable, routingContext);
            }
        }));
    }

    private void getProcs(RoutingContext routingContext) {
        final String appId = routingContext.request().getParam("appId");
        final String clusterId = routingContext.request().getParam("clusterId");
        String prefix = routingContext.request().getParam("prefix");
        if (prefix == null) {
            prefix = "";
        }
        profileDiscoveryAPI.getProcsWithPrefix(appId, clusterId, prefix).whenComplete((procs, throwable) -> vertx.runOnContext(new VoidHandler() {
            @Override
            protected void handle() {
                returnResponse(procs, throwable, routingContext);
            }
        }));

    }

    private void getProfiles(RoutingContext routingContext) {
        final String appId = routingContext.request().getParam("appId");
        final String clusterId = routingContext.request().getParam("clusterId");
        final String proc = routingContext.request().getParam("proc");
        String start = routingContext.request().getParam("start");
        String duration = routingContext.request().getParam("duration");

        if (start == null) {
            start = "";
        }
        if (duration == null) {
            duration = "";
        }
        profileDiscoveryAPI.getProfilesInTimeWindow(appId, clusterId, proc, start, duration).whenComplete((profiles, throwable) -> vertx.runOnContext(new VoidHandler() {
            @Override
            protected void handle() {
                returnResponse(profiles, throwable, routingContext);
            }
        }));
    }

}
