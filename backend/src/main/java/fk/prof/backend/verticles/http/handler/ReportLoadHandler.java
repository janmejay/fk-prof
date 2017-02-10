package fk.prof.backend.verticles.http.handler;

import fk.prof.backend.exception.HttpFailure;
import fk.prof.backend.model.association.BackendAssociationStore;
import fk.prof.backend.model.association.ReportLoadPayload;
import fk.prof.backend.request.CompositeByteBufInputStream;
import fk.prof.backend.request.profile.RecordedProfileProcessor;
import fk.prof.backend.verticles.http.HttpHelper;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

import java.io.IOException;

public class ReportLoadHandler implements Handler<Buffer> {
  private static Logger logger = LoggerFactory.getLogger(ReportLoadHandler.class);

  private final RoutingContext context;
  private final BackendAssociationStore backendAssociationStore;

  public ReportLoadHandler(RoutingContext context, BackendAssociationStore backendAssociationStore) {
    this.context = context;
    this.backendAssociationStore = backendAssociationStore;
  }

  @Override
  public void handle(Buffer requestBuffer) {
    if (!context.response().ended()) {
      try {
        ReportLoadPayload payload = Json.decodeValue(context.getBodyAsString(), ReportLoadPayload.class);
        context.response().end(Json.encode(payload));
      } catch (Exception ex) {
        HttpFailure httpFailure = HttpFailure.failure(ex);
        HttpHelper.handleFailure(context, httpFailure);
      }
    }
  }

}
