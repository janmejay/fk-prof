package fk.prof.backend.request.profile.parser;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import fk.prof.backend.exception.AggregationFailure;
import fk.prof.backend.request.CompositeByteBufInputStream;
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
  private int maxAllowedBytesForWse;

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
   * Returns {@link Recorder.Wse} if {@link #isParsed()} is true, null otherwise
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
   * @param inputStream
   */
  public void parse(CompositeByteBufInputStream inputStream) throws AggregationFailure {
    try {
      if (!wseParsed) {
        if (wseLength == null) {
          inputStream.discardReadBytesAndMark();
          int firstByte = inputStream.read();
          if (firstByte == -1) {
            throw new InvalidProtocolBufferException("EOF when reading WSE:wseLength from inputstream");
          }
          wseLength = CodedInputStream.readRawVarint32(firstByte, inputStream);
          if (wseLength < 1 || wseLength > maxAllowedBytesForWse) {
            throw new AggregationFailure("Allowed range for work-specific entry log length is 1B to " + maxAllowedBytesForWse + "B");
          }
          byte[] wseLengthBytes = inputStream.getBytesReadSinceDiscardAndMark();
          wseChecksum.update(wseLengthBytes, 0, wseLengthBytes.length);
        }

        if (wse == null) {
          if (inputStream.available() < wseLength) {
            return;
          }

          try {
            inputStream.discardReadBytesAndMark();
            byte[] wseBytes = new byte[wseLength];
            inputStream.read(wseBytes, 0, wseLength);
            wse = Recorder.Wse.parseFrom(wseBytes);
            wseChecksum.update(wseBytes, 0, wseBytes.length);
          } catch (InvalidProtocolBufferException ex) {
            //Running buffer has sufficient bytes present for reading wse. If exception is thrown while parsing, send error response
            throw new AggregationFailure("Error while parsing work-specific entry log");
          }
        }

        if (checksumValue == null) {
          inputStream.discardReadBytesAndMark();
          int firstByte = inputStream.read();
          if (firstByte == -1) {
            throw new InvalidProtocolBufferException("EOF when reading WSE:checksum from inputstream");
          }
          checksumValue = CodedInputStream.readRawVarint32(firstByte, inputStream);
          if ((int) wseChecksum.getValue() != checksumValue) {
            throw new AggregationFailure("Checksum of work-specific entry log does not match");
          }
          wseParsed = true;
        }
      }
    } catch (IOException ex) {
      if (!(ex instanceof InvalidProtocolBufferException)) {
        throw new AggregationFailure(ex);
      } else {
        try {
          inputStream.reset();
        } catch (IOException ex1) {
        }
        //NOTE: Ignore this exception. Can come because incomplete request has been received. Chunks can be received later
      }
    }
  }
}
