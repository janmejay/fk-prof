package fk.prof.backend.http;

import fk.prof.backend.model.request.RecordedProfileParser;
import fk.prof.backend.model.request.RecordedProfileRequestHandler;
import fk.prof.backend.exception.HttpFailure;
import fk.prof.backend.service.IProfileWorkService;
import fk.prof.backend.service.ProfileWorkService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;

public class MainVerticle extends AbstractVerticle {

  private IProfileWorkService profileWorkService;

  public MainVerticle(IProfileWorkService profileWorkService) {
    this.profileWorkService = profileWorkService;
  }

  @Override
  public void start(Future<Void> fut) {
    Router router = setupRouting();
    vertx.createHttpServer()
        .requestHandler(router::accept)
        .listen(config().getInteger("http.port", 9300),
            http -> completeStartup(http, fut));
  }

  private Router setupRouting() {
    Router router = Router.router(vertx);
    setupFailureHandler(router);
//        router.route().handler(BodyHandler.create());
    router.route("/assets/*").handler(StaticHandler.create("assets"));
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
    RecordedProfileParser parser = new RecordedProfileParser(profileWorkService);
    RecordedProfileRequestHandler requestHandler = new RecordedProfileRequestHandler(context, parser);
    context.request()
        .handler(requestHandler)
        .endHandler((v) -> {
          if (!context.response().ended()) {
            if(!parser.isParsed()) {
              HttpHelper.handleFailure(context, new HttpFailure("Invalid or incomplete payload received", 400));
            } else {
              context.response().end();
            }
          }
        });
  }
}
