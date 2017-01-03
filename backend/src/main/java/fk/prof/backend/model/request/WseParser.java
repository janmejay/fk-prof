package fk.prof.backend.model.request;

import com.google.protobuf.CodedInputStream;
import fk.prof.backend.exception.HttpFailure;
import io.vertx.core.buffer.Buffer;
import recording.Recorder;

import java.io.IOException;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

public class WseParser {
  private Integer wseLength = null;
  private Recorder.Wse wse = null;
  private Integer checksumValue = null;

  private Checksum wseChecksum = new Adler32();
  private boolean wseParsed = false;
  private int maxAllowedBytesForWse = 1024 * 1024;

  public WseParser() {}

  public WseParser(int maxAllowedBytesForWse) {
    this.maxAllowedBytesForWse = maxAllowedBytesForWse;
  }

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
    this.wseChecksum.reset();
  }

  /**
   * Reads buffer and updates internal state with parsed fields. Returns the starting unread position in outputstream
   *
   * @param codedInputStream
   * @return starting unread position in buffer
   */
  public int parse(CodedInputStream codedInputStream, Buffer underlyingBuffer, int currentPos) throws HttpFailure {
    try {
      if (!wseParsed) {
        if (wseLength == null) {
          wseLength = codedInputStream.readUInt32();
          if(wseLength < 1 || wseLength > maxAllowedBytesForWse) {
            throw new HttpFailure("Allowed range for work-specific entry log length is 1B to " + maxAllowedBytesForWse + "B", 400);
          }
          currentPos = updateChecksumAndGetNewPos(underlyingBuffer, codedInputStream, currentPos);
        }

        if (wse == null) {
          if (wseLength > (underlyingBuffer.length() - currentPos)) {
            return currentPos;
          }

          int oldLimit = codedInputStream.pushLimit(wseLength);
          try {
            wse = Recorder.Wse.parseFrom(codedInputStream);
          } catch (IOException ex) {
            //Running buffer has sufficient bytes present for reading wse. If exception is thrown while parsing, send error response
            throw new HttpFailure("Error while parsing work-specific entry log", 400);
          }
          codedInputStream.popLimit(oldLimit);
          currentPos = updateChecksumAndGetNewPos(underlyingBuffer, codedInputStream, currentPos);
        }

        if (checksumValue == null) {
          checksumValue = codedInputStream.readUInt32();
          currentPos = codedInputStream.getTotalBytesRead();
          if ((int) wseChecksum.getValue() != checksumValue) {
            throw new HttpFailure("Checksum of work-specific entry log does not match", 400);
          }
          wseParsed = true;
        }
      }
    } catch (IOException ex) {
      //NOTE: Ignore this exception. Can come because incomplete request has been received. Chunks can be received later
    }
    return currentPos;
  }

  private int updateChecksumAndGetNewPos(Buffer underlyingBuffer, CodedInputStream codedInputStream, int oldPos) {
    int newPos = codedInputStream.getTotalBytesRead();
    wseChecksum.update(underlyingBuffer.getByteBuf().array(), oldPos, (newPos - oldPos));
    return newPos;
  }
}
