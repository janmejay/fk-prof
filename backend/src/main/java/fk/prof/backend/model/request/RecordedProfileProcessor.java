package fk.prof.backend.model.request;

import com.google.protobuf.CodedInputStream;
import fk.prof.backend.aggregator.AggregationWindow;
import fk.prof.backend.exception.AggregationFailure;
import fk.prof.backend.service.IProfileWorkService;
import io.vertx.core.buffer.Buffer;
import recording.Recorder;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;

public class RecordedProfileProcessor {

  private IProfileWorkService profileWorkService;
  private ISingleProcessingOfProfileGate singleProcessingOfProfileGate;

  private RecordedProfileHeader header = null;
  private AggregationWindow aggregationWindow = null;
  private long workId = 0;
  private LocalDateTime startedAt = null;

  private RecordedProfileHeaderParser headerParser;
  private WseParser wseParser;
  private RecordedProfileIndexes indexes = new RecordedProfileIndexes();

  private boolean intermediateWseEntry = false;

  public RecordedProfileProcessor(IProfileWorkService profileWorkService, ISingleProcessingOfProfileGate singleProcessingOfProfileGate,
                                  int maxAllowedBytesForRecordingHeader, int maxAllowedBytesForWse) {
    this.profileWorkService = profileWorkService;
    this.singleProcessingOfProfileGate = singleProcessingOfProfileGate;
    this.headerParser = new RecordedProfileHeaderParser(maxAllowedBytesForRecordingHeader);
    this.wseParser = new WseParser(maxAllowedBytesForWse);
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
   * Reads buffer and updates internal state with parsed fields.
   * Aggregates the parsed entries in appropriate aggregation window
   * Returns the starting unread position in outputstream
   *
   * @param codedInputStream
   * @return starting unread position in buffer
   */
  public int process(CodedInputStream codedInputStream, Buffer underlyingBuffer, int currentPos) throws AggregationFailure {
    if (startedAt == null) {
      startedAt = LocalDateTime.now(Clock.systemUTC());
    }

    try {
      if (!headerParser.isParsed()) {
        currentPos = headerParser.parse(codedInputStream, underlyingBuffer, currentPos);

        if (headerParser.isParsed()) {
          header = headerParser.get();
          workId = header.getRecordingHeader().getWorkAssignment().getWorkId();
          singleProcessingOfProfileGate.accept(workId);

          aggregationWindow = profileWorkService.getAssociatedAggregationWindow(workId);
          if (aggregationWindow == null) {
            throw new AggregationFailure(String.format("workId=%d not found, cannot continue receiving associated profile",
                workId));
          }
          aggregationWindow.startProfile(workId, header.getRecordingHeader().getRecorderVersion(), startedAt);
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
   * @throws AggregationFailure
   */
  public void close() throws AggregationFailure {
    try {
      if (isParsed()) {
        aggregationWindow.completeProfile(workId);
      } else {
        aggregationWindow.abandonProfile(workId);
        throw new AggregationFailure(String.format("Invalid or incomplete payload received, aggregation failed for work_id=%d", workId));
      }
    } finally {
      singleProcessingOfProfileGate.finish(workId);
    }
  }

  private void processWse(Recorder.Wse wse) throws AggregationFailure {
    indexes.update(wse.getIndexedData());
    aggregationWindow.updateWorkInfo(workId, wse);
    aggregationWindow.aggregate(wse, indexes);
  }

}
