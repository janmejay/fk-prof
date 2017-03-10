package fk.prof.backend.util;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.CodedOutputStream;
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

  public static String processGroupCompactRepr(Recorder.ProcessGroup processGroup) {
    return String.format("%s,%s,%s", processGroup.getAppId(), processGroup.getCluster(), processGroup.getProcName());
  }

  public static Buffer buildBufferFromProto(AbstractMessage message) throws IOException {
    int serializedSize = message.getSerializedSize();
    ByteBuf byteBuf = Unpooled.buffer(serializedSize, Integer.MAX_VALUE);
    CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(byteBuf.array());
    message.writeTo(codedOutputStream);
    byteBuf.writerIndex(serializedSize);
    return Buffer.buffer(byteBuf);
  }
}
