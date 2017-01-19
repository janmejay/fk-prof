package fk.prof.backend.verticles.http;

import fk.prof.backend.exception.HttpFailure;
import fk.prof.backend.model.request.RecordedProfileProcessor;
import fk.prof.backend.model.request.RecordedProfileRequestHandler;
import fk.prof.backend.model.request.SharedMapBasedSingleProcessingOfProfileGate;
import fk.prof.backend.service.IProfileWorkService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.LoggerHandler;

public class HttpVerticle extends AbstractVerticle {

  private final IProfileWorkService profileWorkService;
  private LocalMap<Long, Boolean> workIdsInPipeline;
  public HttpVerticle(IProfileWorkService profileWorkService) {
    this.profileWorkService = profileWorkService;
  }

  @Override
  public void start(Future<Void> fut) {
    Router router = setupRouting();
    workIdsInPipeline = vertx.sharedData().getLocalMap("WORK_ID_PIPELINE");
    vertx.createHttpServer()
        .requestHandler(router::accept)
        .listen(config().getInteger("http.port"),
            http -> completeStartup(http, fut));
  }

  private Router setupRouting() {
    Router router = Router.router(vertx);
    setupFailureHandler(router);
    router.route().handler(LoggerHandler.create());
    router.post(ApiPathConstants.API_POST_PROFILE).handler(this::handlePostProfile);
    return router;
  }

  private void setupFailureHandler(Router router) {
    router.route().failureHandler(context -> {
      HttpFailure exception;
      if (context.failure() == null) {
        exception = new HttpFailure(context.statusCode());
      } else {
        exception = HttpFailure.failure(context.failure());
      }
      HttpHelper.handleFailure(context, exception);
    });
  }

  private void completeStartup(AsyncResult<HttpServer> http, Future<Void> fut) {
    if (http.succeeded()) {
      fut.complete();
    } else {
      fut.fail(http.cause());
    }
  }

  private void handlePostProfile(RoutingContext context) {
    RecordedProfileProcessor parser = new RecordedProfileProcessor(
        profileWorkService,
        new SharedMapBasedSingleProcessingOfProfileGate(workIdsInPipeline),
        config().getJsonObject("parser").getInteger("recordingheader.max.bytes", 1024),
        config().getJsonObject("parser").getInteger("parser.wse.max.bytes", 1024*1024));

    RecordedProfileRequestHandler requestHandler = new RecordedProfileRequestHandler(context, parser);
    context.request()
        .handler(requestHandler)
        .endHandler(v -> {
          if (!context.response().ended()) {
            //Can safely attempt to close the parser here because endHandler is called once the entire body has been read
            //and example in vertx docs also indicates that this handler will execute once all chunk handlers have completed execution
            //http://vertx.io/docs/vertx-core/java/#_handling_requests
            parser.close();
            context.response().end();
          }
        });
  }
}
