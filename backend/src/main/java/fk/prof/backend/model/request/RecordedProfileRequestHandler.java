package fk.prof.backend.model.request;

import com.google.protobuf.CodedInputStream;
import fk.prof.backend.http.HttpHelper;
import fk.prof.backend.exception.HttpFailure;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;

import java.io.IOException;

public class RecordedProfileRequestHandler implements Handler<Buffer> {

  private Buffer runningBuffer = Buffer.buffer();
  private RoutingContext context = null;
  private RecordedProfileParser profileParser = null;

  public RecordedProfileRequestHandler(RoutingContext context, RecordedProfileParser profileParser) {
    this.context = context;
    this.profileParser = profileParser;
  }

  @Override
  public void handle(Buffer requestBuffer) {
    if (!context.response().ended()) {
      runningBuffer.appendBuffer(requestBuffer);
      CodedInputStream codedInputStream = CodedInputStream.newInstance(runningBuffer.getByteBuf().nioBuffer());
      try {
        profileParser.parse(codedInputStream);
        runningBuffer = resetRunningBuffer(runningBuffer, codedInputStream.getTotalBytesRead());
      } catch (IOException ex) {
        //NOTE: Ignore this exception. Can come because incomplete request has been received. Chunks can be received later
        runningBuffer = resetRunningBuffer(runningBuffer, codedInputStream.getTotalBytesRead());
      } catch (HttpFailure ex) {
        HttpHelper.handleFailure(context, ex);
      }
    }
  }

  private static Buffer resetRunningBuffer(Buffer buffer, int startPos) {
    return buffer.getBuffer(startPos, buffer.length());
  }

}
