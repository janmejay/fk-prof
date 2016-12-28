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

public class WseParser {
  private Integer wseLength = null;
  private Recorder.Wse wse = null;
  private Integer checksumValue = null;

  private boolean wseParsed = false;

  /**
   * Returns true if wse has been read and checksum validated, false otherwise
   *
   * @return returns if wse has been parsed or not
   */
  public boolean isParsed() {
    return this.wseParsed;
  }

  /**
   * Returns {@link recording.Recorder.Wse} if {@link #isParsed()} is true, null otherwise
   *
   * @return
   */
  public Recorder.Wse get() {
    return this.wse;
  }

  /**
   * Resets internal fields of the parser
   * Note: If {@link #get()} is not performed before reset, previous parsed entry will be lost
   */
  public void reset() {
    this.wseLength = null;
    this.wse = null;
    this.checksumValue = null;
    this.wseParsed = false;
  }

  /**
   * Reads buffer and updates internal state with parsed fields
   *
   * @param codedInputStream
   * @return starting unread position in buffer
   */
  public void parse(CodedInputStream codedInputStream) throws IOException, HttpFailure {
    if (!wseParsed) {
      if (wseLength == null) {
        wseLength = codedInputStream.readUInt32();
      }

      if (wse == null) {
        int oldLimit = codedInputStream.pushLimit(wseLength);
        wse = Recorder.Wse.parseFrom(codedInputStream);
        codedInputStream.popLimit(oldLimit);
      }

      if (checksumValue == null) {
        checksumValue = codedInputStream.readUInt32();
        if (!validate()) {
          throw new HttpFailure("Checksum of recorded data entry log does not match", 400);
        }
        wseParsed = true;
      }
    }

  }

  private boolean validate() {
    try {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(outputStream);
      codedOutputStream.writeUInt32NoTag(wseLength);
      wse.writeTo(codedOutputStream);
      codedOutputStream.flush();
      byte[] bytesWritten = outputStream.toByteArray();

      Checksum wseChecksum = new Adler32();
      wseChecksum.update(bytesWritten, 0, bytesWritten.length);
      return wseChecksum.getValue() == checksumValue;
    } catch (IOException ex) {
      return false;
    }
  }
}
