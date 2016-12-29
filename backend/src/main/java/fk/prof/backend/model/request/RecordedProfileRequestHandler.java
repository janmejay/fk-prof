package fk.prof.backend.model.request;

import com.google.protobuf.CodedInputStream;
import fk.prof.backend.http.HttpHelper;
import fk.prof.backend.exception.HttpFailure;
import io.netty.buffer.ByteBuf;
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
      int currentPos = 0;
      try {
        currentPos = profileParser.parse(codedInputStream, runningBuffer, currentPos);
        //NOTE: Do not rely on CodedInputStream::getTotalBytesRead() to determine remaining bytes to be parsed
        //Example: CodedInputStream::readUInt32 can throw InvalidProtocolBufferEx if incomplete bytes have been received but it will update the totalBytesRead count
        //This will result in un-parsed bytes getting discarded and not accounted for when next chunk is received
        runningBuffer = resetRunningBuffer(runningBuffer, currentPos);
      } catch (HttpFailure ex) {
        HttpHelper.handleFailure(context, ex);
      }
    }
  }

  private static Buffer resetRunningBuffer(Buffer buffer, int startPos) {
    return buffer.getBuffer(startPos, buffer.length());
  }

}
