package fk.prof.backend.request.profile;

import fk.prof.backend.aggregator.AggregationWindow;
import fk.prof.backend.exception.AggregationFailure;
import fk.prof.backend.model.profile.RecordedProfileHeader;
import fk.prof.backend.model.profile.RecordedProfileIndexes;
import fk.prof.backend.request.CompositeByteBufInputStream;
import fk.prof.backend.request.profile.parser.RecordedProfileHeaderParser;
import fk.prof.backend.request.profile.parser.WseParser;
import fk.prof.backend.service.AggregationWindowReadContext;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import recording.Recorder;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;

public class RecordedProfileProcessor {
  private static Logger logger = LoggerFactory.getLogger(RecordedProfileProcessor.class);

  private AggregationWindowReadContext aggregationWindowReadContext;
  private ISingleProcessingOfProfileGate singleProcessingOfProfileGate;

  private RecordedProfileHeader header = null;
  private AggregationWindow aggregationWindow = null;
  private long workId = 0;
  private LocalDateTime startedAt = null;

  private RecordedProfileHeaderParser headerParser;
  private WseParser wseParser;
  private RecordedProfileIndexes indexes = new RecordedProfileIndexes();

  private boolean intermediateWseEntry = false;

  public RecordedProfileProcessor(AggregationWindowReadContext aggregationWindowReadContext, ISingleProcessingOfProfileGate singleProcessingOfProfileGate,
                                  int maxAllowedBytesForRecordingHeader, int maxAllowedBytesForWse) {
    this.aggregationWindowReadContext = aggregationWindowReadContext;
    this.singleProcessingOfProfileGate = singleProcessingOfProfileGate;
    this.headerParser = new RecordedProfileHeaderParser(maxAllowedBytesForRecordingHeader);
    this.wseParser = new WseParser(maxAllowedBytesForWse);
  }

  /**
   * Returns true if header has been successfully parsed to retrieve aggregation window and no wse entry log is processed partially
   *
   * @return processed a valid recorded profile object or not
   */
  public boolean isProcessed() {
    return aggregationWindow != null && !intermediateWseEntry;
  }

  /**
   * Reads buffer and updates internal state with parsed fields.
   * Aggregates the parsed entries in appropriate aggregation window
   * Returns the starting unread position in outputstream
   *
   * @param inputStream
   */
  public void process(CompositeByteBufInputStream inputStream) throws AggregationFailure {
    if (startedAt == null) {
      startedAt = LocalDateTime.now(Clock.systemUTC());
    }

    try {
      if (aggregationWindow == null) {
        headerParser.parse(inputStream);

        if (headerParser.isParsed()) {
          header = headerParser.get();
          workId = header.getRecordingHeader().getWorkAssignment().getWorkId();

          singleProcessingOfProfileGate.accept(workId);
          aggregationWindow = aggregationWindowReadContext.getAssociatedAggregationWindow(workId);
          if (aggregationWindow == null) {
            throw new AggregationFailure(String.format("workId=%d not found, cannot continue receiving associated profile",
                workId));
          }

          aggregationWindow.startProfile(workId, header.getRecordingHeader().getRecorderVersion(), startedAt);
          logger.info(String.format("Profile aggregation started for work_id=%d started_at=%s",
              workId, startedAt.toString()));
        }
      }

      if (aggregationWindow != null) {
        while (inputStream.available() > 0) {
          wseParser.parse(inputStream);
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
    } catch (Exception ex) {
      if(workId != 0) {
        singleProcessingOfProfileGate.finish(workId);
      }
      throw ex;
    }
  }

  /**
   * If parsing was successful, marks the profile as completed/retried, partial otherwise
   *
   * @throws AggregationFailure
   */
  public void close() throws AggregationFailure {
    try {
      if (isProcessed()) {
        aggregationWindow.completeProfile(workId);
      } else {
        if (aggregationWindow != null) {
          aggregationWindow.abandonProfile(workId);
        }
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
