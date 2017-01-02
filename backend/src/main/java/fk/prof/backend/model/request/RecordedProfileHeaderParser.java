package fk.prof.backend.model.request;

import com.google.protobuf.CodedInputStream;
import fk.prof.backend.exception.HttpFailure;
import io.vertx.core.buffer.Buffer;
import recording.Recorder;

import java.io.IOException;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

public class RecordedProfileHeaderParser {
  private Integer encodedVersion = null;
  private Integer headerLength = null;
  private Recorder.RecordingHeader recordingHeader = null;
  private Integer checksumValue = null;

  private Checksum headerChecksum = new Adler32();
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
   * Reads buffer and updates internal state with parsed fields. Returns the starting unread position in outputstream
   *
   * @param codedInputStream
   * @return starting unread position in buffer
   */
  public int parse(CodedInputStream codedInputStream, Buffer underlyingBuffer, int currentPos) throws HttpFailure {
    try {
      if (!headerParsed) {
        if (encodedVersion == null) {
          encodedVersion = codedInputStream.readUInt32();
          currentPos = updateChecksumAndGetNewPos(underlyingBuffer, codedInputStream, currentPos);
        }

        if (headerLength == null) {
          headerLength = codedInputStream.readUInt32();
          currentPos = updateChecksumAndGetNewPos(underlyingBuffer, codedInputStream, currentPos);
        }

        if (recordingHeader == null) {
          if (headerLength > (underlyingBuffer.length() - currentPos)) {
            return currentPos;
          }

          int oldLimit = codedInputStream.pushLimit(headerLength);
          recordingHeader = Recorder.RecordingHeader.parseFrom(codedInputStream);
          codedInputStream.popLimit(oldLimit);
          currentPos = updateChecksumAndGetNewPos(underlyingBuffer, codedInputStream, currentPos);
        }

        if (checksumValue == null) {
          checksumValue = codedInputStream.readUInt32();
          currentPos = codedInputStream.getTotalBytesRead();
          if ((int) headerChecksum.getValue() != checksumValue) {
            throw new HttpFailure("Checksum of header does not match", 400);
          }
          headerParsed = true;
        }
      }
    } catch (IOException ex) {
      //NOTE: Ignore this exception. Can come because incomplete request has been received. Chunks can be received later
    }
    return currentPos;
  }

  private int updateChecksumAndGetNewPos(Buffer underlyingBuffer, CodedInputStream codedInputStream, int oldPos) {
    int newPos = codedInputStream.getTotalBytesRead();
    headerChecksum.update(underlyingBuffer.getByteBuf().array(), oldPos, (newPos - oldPos));
    return newPos;
  }

}
