package fk.prof.backend.util;

import com.google.protobuf.*;
import fk.prof.aggregation.proto.AggregatedProfileModel;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.buffer.Buffer;
import recording.Recorder;

import java.io.IOException;

public class ProtoUtil {
  public static AggregatedProfileModel.WorkType mapRecorderToAggregatorWorkType(Recorder.WorkType recorderWorkType) {
    return AggregatedProfileModel.WorkType.forNumber(recorderWorkType.getNumber());
  }

  //Avoids double byte copy to create a vertx buffer
  public static Buffer buildBufferFromProto(AbstractMessage message) throws IOException {
    int serializedSize = message.getSerializedSize();
    ByteBuf byteBuf = Unpooled.buffer(serializedSize, Integer.MAX_VALUE);
    CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(byteBuf.array());
    message.writeTo(codedOutputStream);
    byteBuf.writerIndex(serializedSize);
    return Buffer.buffer(byteBuf);
  }

  //Proto parser operates directly on underlying byte array, avoids byte copy
  public static <T extends AbstractMessage> T buildProtoFromBuffer(Parser<T> parser, Buffer buffer)
      throws InvalidProtocolBufferException {
    return parser.parseFrom(CodedInputStream.newInstance(buffer.getByteBuf().nioBuffer()));
  }
}
