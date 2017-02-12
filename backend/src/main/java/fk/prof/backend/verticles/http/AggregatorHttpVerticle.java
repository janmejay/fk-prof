package fk.prof.backend.verticles.http;

import fk.prof.backend.exception.HttpFailure;
import fk.prof.backend.request.CompositeByteBufInputStream;
import fk.prof.backend.request.profile.RecordedProfileProcessor;
import fk.prof.backend.request.profile.impl.SharedMapBasedSingleProcessingOfProfileGate;
import fk.prof.backend.service.IProfileWorkService;
import fk.prof.backend.verticles.http.handler.RecordedProfileRequestHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.LoggerHandler;

public class AggregatorHttpVerticle extends AbstractVerticle {
  private final IProfileWorkService profileWorkService;
  private LocalMap<Long, Boolean> workIdsInPipeline;
  private JsonObject httpServerConfig;

  public AggregatorHttpVerticle(JsonObject httpServerConfig, IProfileWorkService profileWorkService) {
    this.httpServerConfig = httpServerConfig;
    this.profileWorkService = profileWorkService;
  }

  @Override
  public void start(Future<Void> fut) {
    Router router = setupRouting();
    workIdsInPipeline = vertx.sharedData().getLocalMap("WORK_ID_PIPELINE");
    vertx.createHttpServer(HttpHelper.getHttpServerOptions(httpServerConfig))
        .requestHandler(router::accept)
        .listen(httpServerConfig.getInteger("port"), http -> completeStartup(http, fut));
  }

  private Router setupRouting() {
    Router router = Router.router(vertx);
    router.route().handler(LoggerHandler.create());
    router.post(ApiPathConstants.AGGREGATOR_POST_PROFILE).handler(this::handlePostProfile);
    return router;
  }

  private void completeStartup(AsyncResult<HttpServer> http, Future<Void> fut) {
    if (http.succeeded()) {
      fut.complete();
    } else {
      fut.fail(http.cause());
    }
  }

  private void handlePostProfile(RoutingContext context) {
    CompositeByteBufInputStream inputStream = new CompositeByteBufInputStream();
    RecordedProfileProcessor profileProcessor = new RecordedProfileProcessor(
        profileWorkService,
        new SharedMapBasedSingleProcessingOfProfileGate(workIdsInPipeline),
        config().getJsonObject("parser").getInteger("recordingheader.max.bytes", 1024),
        config().getJsonObject("parser").getInteger("parser.wse.max.bytes", 1024 * 1024));

    RecordedProfileRequestHandler requestHandler = new RecordedProfileRequestHandler(context, inputStream, profileProcessor);
    context.request()
        .handler(requestHandler)
        .endHandler(v -> {
          try {
            if (!context.response().ended()) {
              //Can safely attempt to close the profile processor here because endHandler is called once the entire body has been read
              //and example in vertx docs also indicates that this handler will execute once all chunk handlers have completed execution
              //http://vertx.io/docs/vertx-core/java/#_handling_requests
              inputStream.close();
              profileProcessor.close();
              context.response().end();
            }
          } catch (Exception ex) {
            HttpFailure httpFailure = HttpFailure.failure(ex);
            HttpHelper.handleFailure(context, httpFailure);
          }
        });
  }
}
