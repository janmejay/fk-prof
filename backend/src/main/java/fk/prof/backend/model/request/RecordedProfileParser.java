package fk.prof.backend.model.request;

import fk.prof.backend.aggregator.AggregationWindow;
import fk.prof.backend.exception.AggregationFailure;
import fk.prof.backend.exception.HttpFailure;
import fk.prof.backend.service.IProfileWorkService;
import io.vertx.core.buffer.Buffer;
import recording.Recorder;

public class RecordedProfileParser {

  private IProfileWorkService profileWorkService;
  private RecordedProfileHeader header = null;
  private AggregationWindow aggregationWindow = null;
  private long workId = 0;

  private RecordedProfileHeaderParser headerParser = new RecordedProfileHeaderParser();
  private WseParser wseParser = new WseParser();
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
   * Reads buffer and updates internal state with parsed fields
   *
   * @param buffer
   * @param currentPos
   * @return starting unread position in buffer
   */
  public int parse(Buffer buffer, int currentPos) throws HttpFailure, AggregationFailure {
    if (!headerParser.isParsed()) {
      int newPos = headerParser.parse(buffer, currentPos);
      if (newPos == currentPos) {
        return currentPos;
      } else {
        currentPos = newPos;

        if (headerParser.isParsed()) {
          header = headerParser.get();
          workId = header.getRecordingHeader().getWorkAssignment().getWorkId();
          aggregationWindow = profileWorkService.getAssociatedAggregationWindow(workId);
          if(aggregationWindow == null) {
            throw new HttpFailure(String.format("workId=%d not found, cannot continue receiving associated profile",
                workId), 400);
          }
          boolean started = aggregationWindow.startReceivingProfile(workId);
          if(!started) {
            throw new HttpFailure(String.format("Unable to start receiving profile for workId=%d, status=%s",
                workId, aggregationWindow.getStatus(workId)), 400);
          }
        }
      }
    }

    if (headerParser.isParsed()) {
      while (currentPos < buffer.length()) {
        int newPos = wseParser.parse(buffer, currentPos);
        if (newPos == currentPos) {
          return currentPos;
        } else {
          currentPos = newPos;
          intermediateWseEntry = true;
        }

        if (wseParser.isParsed()) {
          aggregationWindow.aggregate(wseParser.get());
          wseParser.reset();
          intermediateWseEntry = false;
        }
      }
    }

    return currentPos;
  }

}
