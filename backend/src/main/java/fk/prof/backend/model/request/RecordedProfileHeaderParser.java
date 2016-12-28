package fk.prof.backend.model.request;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import fk.prof.backend.Utils;
import fk.prof.backend.exception.HttpFailure;
import io.vertx.core.buffer.Buffer;
import recording.Recorder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

public class RecordedProfileHeaderParser {
  private Integer encodedVersion = null;
  private Integer headerLength = null;
  private Recorder.RecordingHeader recordingHeader = null;
  private Integer checksumValue = null;

  private boolean headerParsed = false;

  /**
   * Returns true if all fields of header have been read and checksum validated, false otherwise
   *
   * @return returns if header has been parsed or not
   */
  public boolean isParsed() {
    return this.headerParsed;
  }

  /**
   * Returns {@link RecordedProfileHeader} if {@link #isParsed()} is true, null otherwise
   *
   * @return
   */
  public RecordedProfileHeader get() {
    if (!this.headerParsed) {
      return null;
    }
    return new RecordedProfileHeader(this.encodedVersion, this.recordingHeader);
  }

  /**
   * Reads buffer and updates internal state with parsed fields
   *
   * @param codedInputStream
   * @return starting unread position in buffer
   */
  public void parse(CodedInputStream codedInputStream) throws IOException, HttpFailure {
    if (!headerParsed) {
      if (encodedVersion == null) {
        encodedVersion = codedInputStream.readUInt32();
      }

      if (headerLength == null) {
        headerLength = codedInputStream.readUInt32();
      }

      if (recordingHeader == null) {
        int oldLimit = codedInputStream.pushLimit(headerLength);
        recordingHeader = Recorder.RecordingHeader.parseFrom(codedInputStream);
        codedInputStream.popLimit(oldLimit);
      }

      if (checksumValue == null) {
        checksumValue = codedInputStream.readUInt32();
        if (!validate()) {
          throw new HttpFailure("Checksum of header does not match", 400);
        }
        headerParsed = true;
      }
    }
  }

  private boolean validate() {
    try {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(outputStream);
      codedOutputStream.writeUInt32NoTag(encodedVersion);
      codedOutputStream.writeUInt32NoTag(headerLength);
      recordingHeader.writeTo(codedOutputStream);
      codedOutputStream.flush();
      byte[] bytesWritten = outputStream.toByteArray();

      Checksum headerChecksum = new Adler32();
      headerChecksum.update(bytesWritten, 0, bytesWritten.length);
      return headerChecksum.getValue() == checksumValue;
    } catch (IOException ex) {
      return false;
    }
  }

}
