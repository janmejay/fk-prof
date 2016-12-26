package fk.prof.backend.model.request;

import com.google.protobuf.InvalidProtocolBufferException;
import fk.prof.backend.http.HttpHelper;
import fk.prof.backend.model.response.HttpFailure;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import recording.Recorder;

import java.util.zip.Adler32;
import java.util.zip.Checksum;

public class ProfilePayload {
  private Long encodedVersion = null;
  private Long headerLength = null;
  private Recorder.RecordingHeader recordingHeader = null;
  private Long checksumValue = null;

  private Checksum internalChecksum = new Adler32();
  private boolean readWseEntries = false;
  private Buffer runningBuffer = Buffer.buffer();

  public boolean validate() {
    internalChecksum.reset();
    internalChecksum.update(encodedVersion.intValue());
    internalChecksum.update(headerLength.intValue());
    byte[] recordingHeaderByteArr = recordingHeader.toByteArray();
    internalChecksum.update(recordingHeaderByteArr, 0, recordingHeaderByteArr.length);
    return internalChecksum.getValue() == checksumValue;
  }

  public Handler<Buffer> handler(RoutingContext context) {
    return requestBuffer -> {
      if (!context.response().ended()) {
        try {
          runningBuffer.appendBuffer(requestBuffer);
          int readTillPos = 0;
          if (!readWseEntries) {
            if (encodedVersion == null) {
              if (runningBuffer.length() >= (4 + readTillPos)) {
                encodedVersion = runningBuffer.getUnsignedInt(readTillPos);
                readTillPos += 4;
              } else {
                resetRunningBuffer(readTillPos);
                return;
              }
            }

            if (headerLength == null) {
              if (runningBuffer.length() >= (4 + readTillPos)) {
                headerLength = runningBuffer.getUnsignedInt(readTillPos);
                readTillPos += 4;
              } else {
                resetRunningBuffer(readTillPos);
                return;
              }
            }

            if (recordingHeader == null) {
              if (runningBuffer.length() >= (headerLength + readTillPos)) {
                try {
                  recordingHeader = Recorder.RecordingHeader.parseFrom(
                      runningBuffer.getBytes(readTillPos, readTillPos + headerLength.intValue()));
                  readTillPos += headerLength.intValue();
                } catch (InvalidProtocolBufferException ex) {
                  throw new HttpFailure("Invalid recording header in request", 400);
                }
              } else {
                resetRunningBuffer(readTillPos);
                return;
              }
            }

            if (checksumValue == null) {
              if (runningBuffer.length() >= (4 + readTillPos)) {
                checksumValue = runningBuffer.getUnsignedInt(readTillPos);
                if (!validate()) {
                  throw new HttpFailure("Checksum of header does not match", 400);
                }
                readWseEntries = true;
                readTillPos += 4;
              } else {
                resetRunningBuffer(readTillPos);
                return;
              }
            }
          }
        } catch (HttpFailure ex) {
          HttpHelper.handleFailure(context, ex);
        }
      }
    };
  }

  private void resetRunningBuffer(int pos) {
    runningBuffer = runningBuffer.getBuffer(pos, runningBuffer.length());
  }

  public Long getEncodedVersion() {
    return encodedVersion;
  }

  public Long getHeaderLength() {
    return headerLength;
  }

  public Recorder.RecordingHeader getRecordingHeader() {
    return recordingHeader;
  }

  public Long getChecksumValue() {
    return checksumValue;
  }

  //TODO: remove
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("encoding version=" + encodedVersion).append('\n');
    builder.append("header length=" + headerLength).append('\n');
    builder.append("recording header=" + recordingHeader).append('\n');
    builder.append("checksum=" + checksumValue).append('\n');
    return builder.toString();
  }
}
