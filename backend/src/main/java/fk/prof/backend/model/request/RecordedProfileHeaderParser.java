package fk.prof.backend.model.request;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import fk.prof.backend.exception.AggregationFailure;
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
  private int maxAllowedBytesForRecordingHeader;

  public RecordedProfileHeaderParser(int maxAllowedBytesForRecordingHeader) {
    this.maxAllowedBytesForRecordingHeader = maxAllowedBytesForRecordingHeader;
  }

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
   * @param inputStream
   * @return starting unread position in buffer
   */
  public void parse(CompositeByteBufInputStream inputStream) throws AggregationFailure {
    try {
      if (!headerParsed) {
        if (encodedVersion == null) {
          inputStream.mark(0);
          int firstByte = inputStream.read();
          if(firstByte == -1) {
            //NOTE: Wrapping this as invalid protocol buffer exception to indicate this is a case of incomplete protobuf-serialized bytes received
            throw new InvalidProtocolBufferException("EOF when reading RecordingHeader:encodedVersion from inputstream");
          }
          encodedVersion = CodedInputStream.readRawVarint32(firstByte, inputStream);
          byte[] encodedVersionBytes = inputStream.getBytesReadSinceMark();
          headerChecksum.update(encodedVersionBytes, 0, encodedVersionBytes.length);
        }

        if (headerLength == null) {
          inputStream.mark(0);
          int firstByte = inputStream.read();
          if(firstByte == -1) {
            throw new InvalidProtocolBufferException("EOF when reading RecordingHeader:headerLength from inputstream");
          }
          headerLength = CodedInputStream.readRawVarint32(firstByte, inputStream);
          if (headerLength < 1 || headerLength > maxAllowedBytesForRecordingHeader) {
            throw new AggregationFailure("Allowed range for recording header length is 1B to " + maxAllowedBytesForRecordingHeader + "B");
          }
          byte[] headerLengthBytes = inputStream.getBytesReadSinceMark();
          headerChecksum.update(headerLengthBytes, 0, headerLengthBytes.length);
        }

        if (recordingHeader == null) {
          if(inputStream.available() < headerLength) {
            return;
          }
          try {
            inputStream.mark(0);
            byte[] headerBytes = new byte[headerLength];
            inputStream.read(headerBytes, 0, headerLength);
            recordingHeader = Recorder.RecordingHeader.parseFrom(headerBytes);
            headerChecksum.update(headerBytes, 0, headerBytes.length);
          } catch (InvalidProtocolBufferException ex) {
            //Running buffer has sufficient bytes present for reading recording header. If exception is thrown while parsing, send error response
            throw new AggregationFailure("Error while parsing recording header");
          }
        }

        if (checksumValue == null) {
          inputStream.mark(0);
          int firstByte = inputStream.read();
          if(firstByte == -1) {
            throw new InvalidProtocolBufferException("EOF when reading RecordingHeader:checksum from inputstream");
          }
          checksumValue = CodedInputStream.readRawVarint32(firstByte, inputStream);
          if ((int) headerChecksum.getValue() != checksumValue) {
            throw new AggregationFailure("Checksum of header does not match");
          }
          headerParsed = true;
        }
      }
    } catch (IOException ex) {
      if(!(ex instanceof InvalidProtocolBufferException)) {
        throw new AggregationFailure(ex);
      } else {
        try {
          inputStream.reset();
        } catch (IOException ex1) {}
        //NOTE: Ignore this exception. Can come because incomplete request has been received. Chunks can be received later
      }
    }
  }

}
