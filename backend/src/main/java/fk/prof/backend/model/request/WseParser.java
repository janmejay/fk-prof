package fk.prof.backend.model.request;

import com.google.protobuf.InvalidProtocolBufferException;
import fk.prof.backend.model.response.HttpFailure;
import io.vertx.core.buffer.Buffer;
import recording.Recorder;

import java.util.zip.Adler32;
import java.util.zip.Checksum;

public class WseParser {
  private Long wseLength = null;
  private Recorder.Wse wse = null;
  private Long checksumValue = null;

  private Checksum wseChecksum = new Adler32();
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
   * @param buffer
   * @param currentPos
   * @return starting unread position in buffer
   */
  public int parse(Buffer buffer, int currentPos) {
    if (!wseParsed) {
      if (wseLength == null) {
        int newPos = readWseLength(buffer, currentPos);
        if (newPos == currentPos) {
          return currentPos;
        } else {
          currentPos = newPos;
        }
      }

      if (wse == null) {
        int newPos = readWse(buffer, currentPos);
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
            throw new HttpFailure("Checksum of recorded data entry log does not match", 400);
          }
          this.wseParsed = true;
          currentPos = newPos;
        }
      }
    }

    return currentPos;
  }

  private boolean validate() {
    wseChecksum.reset();
    wseChecksum.update(wseLength.intValue());
    byte[] wseByteArr = wse.toByteArray();
    wseChecksum.update(wseByteArr, 0, wseByteArr.length);
    return wseChecksum.getValue() == checksumValue;
  }

  private int readWseLength(Buffer buffer, int currentPos) {
    if (buffer.length() >= (4 + currentPos)) {
      this.wseLength = buffer.getUnsignedInt(currentPos);
      currentPos += 4;
    }
    return currentPos;
  }

  private int readWse(Buffer buffer, int currentPos) throws HttpFailure {
    if (buffer.length() >= (this.wseLength + currentPos)) {
      try {
        this.wse = Recorder.Wse.parseFrom(
            buffer.getBytes(currentPos, currentPos + this.wseLength.intValue()));
        currentPos += this.wseLength.intValue();
      } catch (InvalidProtocolBufferException ex) {
        throw new HttpFailure("Invalid wse in request", 400);
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
