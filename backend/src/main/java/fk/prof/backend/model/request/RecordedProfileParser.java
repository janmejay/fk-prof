package fk.prof.backend.model.request;

import com.google.protobuf.CodedInputStream;
import fk.prof.backend.aggregator.AggregationWindow;
import fk.prof.backend.exception.AggregationFailure;
import fk.prof.backend.exception.HttpFailure;
import fk.prof.backend.service.IProfileWorkService;
import io.vertx.core.buffer.Buffer;
import recording.Recorder;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;

public class RecordedProfileParser {

  private IProfileWorkService profileWorkService;
  private RecordedProfileHeader header = null;
  private AggregationWindow aggregationWindow = null;
  private long workId = 0;
  private LocalDateTime startedAt = null;

  private RecordedProfileHeaderParser headerParser = new RecordedProfileHeaderParser();
  private WseParser wseParser = new WseParser();
  private RecordedProfileIndexes indexes = new RecordedProfileIndexes();

  private boolean intermediateWseEntry = false;

  public RecordedProfileParser(IProfileWorkService profileWorkService) {
    this.profileWorkService = profileWorkService;
  }

  /**
   * Returns true if header has been successfully parsed and no wse entry log is parsed partially
   *
   * @return parsed a valid recorded profile object or not
   */
  public boolean isParsed() {
    return headerParser.isParsed() && !intermediateWseEntry;
  }

  /**
   * Returns {@link RecordedProfileHeader} if {@link #isParsed()} is true, null otherwise
   *
   * @return
   */
  public RecordedProfileHeader getProfileHeader() {
    return headerParser.get();
  }

  /**
   * Reads buffer and updates internal state with parsed fields. Returns the starting unread position in outputstream
   *
   * @param codedInputStream
   * @return starting unread position in buffer
   */
  public int parse(CodedInputStream codedInputStream, Buffer underlyingBuffer, int currentPos) throws HttpFailure, AggregationFailure {
    if (startedAt == null) {
      startedAt = LocalDateTime.now(Clock.systemUTC());
    }

    try {
      if (!headerParser.isParsed()) {
        currentPos = headerParser.parse(codedInputStream, underlyingBuffer, currentPos);

        if (headerParser.isParsed()) {
          header = headerParser.get();

          workId = header.getRecordingHeader().getWorkAssignment().getWorkId();
          aggregationWindow = profileWorkService.getAssociatedAggregationWindow(workId);
          if (aggregationWindow == null) {
            throw new HttpFailure(String.format("workId=%d not found, cannot continue receiving associated profile",
                workId), 400);
          }

          boolean started = aggregationWindow.startProfile(workId, startedAt);
          if (!started) {
            throw new HttpFailure(String.format("Unable to start receiving profile for workId=%d, status=%s",
                workId, aggregationWindow.getWorkInfo(workId).getStatus()), 400);
          }
        }
      }

      if (headerParser.isParsed()) {
        while (!codedInputStream.isAtEnd()) {
          currentPos = wseParser.parse(codedInputStream, underlyingBuffer, currentPos);
          if (wseParser.isParsed()) {
            Recorder.Wse wse = wseParser.get();
            processWse(wse);
            wseParser.reset();
          } else {
            throw new IOException("Incomplete bytes received for wse entry log");
          }
        }
      }
    } catch (IOException ex) {
      //NOTE: Ignore this exception. Can happen because incomplete request has been received. Chunks can be received later
    }

    return currentPos;
  }

  /**
   * If parsing was successful, marks the profile as completed/retried, partial otherwise
   *
   * @throws HttpFailure
   */
  public void close() throws HttpFailure {
    if (isParsed()) {
      if (!aggregationWindow.completeProfile(workId)) {
        throw new HttpFailure(String.format("Unable to complete receiving profile for workId=%d, status=%s",
            workId, aggregationWindow.getWorkInfo(workId).getStatus()), 400);
      }
    } else {
      if (!aggregationWindow.abandonProfile(workId)) {
        throw new HttpFailure(String.format("Unable to abandon receiving profile for workId=%d, status=%s",
            workId, aggregationWindow.getWorkInfo(workId).getStatus()), 400);
      } else {
        //Successfully abandoned profile, now raise an exception so that failure http response is returned
        throw new HttpFailure("Invalid or incomplete payload received", 400);
      }
    }
  }

  private void processWse(Recorder.Wse wse) throws AggregationFailure {
    indexes.update(wse.getIndexedData());
    aggregationWindow.updateWorkInfo(workId, wse);
    aggregationWindow.aggregate(wse, indexes);
  }

}
