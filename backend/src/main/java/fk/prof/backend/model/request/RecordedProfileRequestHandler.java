package fk.prof.backend.model.request;

import fk.prof.backend.BufferUtil;
import fk.prof.backend.http.HttpHelper;
import fk.prof.backend.exception.HttpFailure;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;

public class RecordedProfileRequestHandler implements Handler<Buffer> {

  private Buffer runningBuffer = Buffer.buffer();
  private RoutingContext context = null;
  private RecordedProfileParser parser = null;

  public RecordedProfileRequestHandler(RoutingContext context, RecordedProfileParser parser) {
    this.context = context;
    this.parser = parser;
  }

  @Override
  public void handle(Buffer requestBuffer) {
    if (!context.response().ended()) {
      try {
        runningBuffer.appendBuffer(requestBuffer);
        int readTillPos = parser.parse(runningBuffer, 0);
        runningBuffer = BufferUtil.resetRunningBuffer(runningBuffer, readTillPos);
      } catch (HttpFailure ex) {
        HttpHelper.handleFailure(context, ex);
      }
    }
  }
}
