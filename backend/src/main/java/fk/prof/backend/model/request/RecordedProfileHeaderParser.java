package fk.prof.backend.model.request;

import com.google.protobuf.InvalidProtocolBufferException;
import fk.prof.backend.exception.HttpFailure;
import io.vertx.core.buffer.Buffer;
import recording.Recorder;

import java.util.zip.Adler32;
import java.util.zip.Checksum;

public class RecordedProfileHeaderParser {
  private Long encodedVersion = null;
  private Long headerLength = null;
  private Recorder.RecordingHeader recordingHeader = null;
  private Long checksumValue = null;

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
   * Reads buffer and updates internal state with parsed fields
   *
   * @param buffer
   * @param currentPos
   * @return starting unread position in buffer
   */
  public int parse(Buffer buffer, int currentPos) throws HttpFailure {
    if (!headerParsed) {
      if (encodedVersion == null) {
        int newPos = readEncodedVersion(buffer, currentPos);
        if (newPos == currentPos) {
          return currentPos;
        } else {
          currentPos = newPos;
        }
      }

      if (headerLength == null) {
        int newPos = readHeaderLength(buffer, currentPos);
        if (newPos == currentPos) {
          return currentPos;
        } else {
          currentPos = newPos;
        }
      }

      if (recordingHeader == null) {
        int newPos = readRecordingHeader(buffer, currentPos);
        if (newPos == currentPos) {
          return currentPos;
        } else {
          currentPos = newPos;
        }
      }

      if (checksumValue == null) {
        int newPos = readChecksumValue(buffer, currentPos);
        if (newPos == currentPos) {
          return currentPos;
        } else {
          if (!validate()) {
            throw new HttpFailure("Checksum of header does not match", 400);
          }
          this.headerParsed = true;
          currentPos = newPos;
        }
      }
    }

    return currentPos;
  }

  private boolean validate() {
    headerChecksum.reset();
    headerChecksum.update(encodedVersion.intValue());
    headerChecksum.update(headerLength.intValue());
    byte[] recordingHeaderByteArr = recordingHeader.toByteArray();
    headerChecksum.update(recordingHeaderByteArr, 0, recordingHeaderByteArr.length);
    return headerChecksum.getValue() == checksumValue;
  }

  private int readEncodedVersion(Buffer buffer, int currentPos) {
    if (buffer.length() >= (4 + currentPos)) {
      this.encodedVersion = buffer.getUnsignedInt(currentPos);
      currentPos += 4;
    }
    return currentPos;
  }

  private int readHeaderLength(Buffer buffer, int currentPos) {
    if (buffer.length() >= (4 + currentPos)) {
      this.headerLength = buffer.getUnsignedInt(currentPos);
      currentPos += 4;
    }
    return currentPos;
  }

  private int readRecordingHeader(Buffer buffer, int currentPos) throws HttpFailure {
    if (buffer.length() >= (this.headerLength + currentPos)) {
      try {
        this.recordingHeader = Recorder.RecordingHeader.parseFrom(
            buffer.getBytes(currentPos, currentPos + this.headerLength.intValue()));
        currentPos += this.headerLength.intValue();
      } catch (InvalidProtocolBufferException ex) {
        throw new HttpFailure("Invalid recording header in request", 400);
      }
    }
    return currentPos;
  }

  private int readChecksumValue(Buffer buffer, int currentPos) {
    if (buffer.length() >= (4 + currentPos)) {
      this.checksumValue = buffer.getUnsignedInt(currentPos);
      currentPos += 4;
    }
    return currentPos;
  }
}
