package fk.prof.backend.model.request;

import fk.prof.backend.exception.HttpFailure;
import fk.prof.backend.verticles.http.HttpHelper;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

import java.io.IOException;

public class RecordedProfileRequestHandler implements Handler<Buffer> {
  private static Logger logger = LoggerFactory.getLogger(RecordedProfileRequestHandler.class);

  private final RoutingContext context;
  private final RecordedProfileProcessor profileParser;
  private final CompositeByteBufInputStream inputStream;

  public RecordedProfileRequestHandler(RoutingContext context, CompositeByteBufInputStream inputStream, RecordedProfileProcessor profileParser) {
    this.context = context;
    this.profileParser = profileParser;
    this.inputStream = inputStream;
  }

  @Override
  public void handle(Buffer requestBuffer) {
//    System.err.println(String.format("buffer=%d, chunk=%d", runningBuffer.length(), requestBuffer.length()));
    if (!context.response().ended()) {
      inputStream.accept(requestBuffer.getByteBuf());
      try {
        profileParser.process(inputStream);
        //NOTE: Do not rely on CodedInputStream::getTotalBytesRead() to determine remaining bytes to be parsed
        //Example: CodedInputStream::readUInt32 can throw InvalidProtocolBufferEx if incomplete bytes have been received but it will update the totalBytesRead count
        //This will result in un-parsed bytes getting discarded and not accounted for when next chunk is received
        //Maintaining own counter "currentPos" which serves as a checkpoint = last successfully read byte in the buffer + 1
      } catch (Exception ex) {
        try {
          inputStream.close();
        } catch (IOException ex1) {
          logger.error("Error closing inputstream", ex1);
        }
        HttpFailure httpFailure = HttpFailure.failure(ex);
        HttpHelper.handleFailure(context, httpFailure);
      }
    }
  }

}
