package fk.prof.backend.model.request;

import com.google.protobuf.CodedInputStream;
import fk.prof.backend.aggregator.IAggregationWindow;
import fk.prof.backend.exception.AggregationFailure;
import fk.prof.backend.exception.HttpFailure;
import fk.prof.backend.service.IProfileWorkService;
import io.vertx.core.buffer.Buffer;

import java.io.IOException;

public class RecordedProfileParser {

  private IProfileWorkService profileWorkService;
  private RecordedProfileHeader header = null;
  private IAggregationWindow aggregationWindow = null;
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
   * @param codedInputStream
   * @return starting unread position in buffer
   */
  public void parse(CodedInputStream codedInputStream) throws HttpFailure, AggregationFailure, IOException {

    if (!headerParser.isParsed()) {
      headerParser.parse(codedInputStream);

      if (headerParser.isParsed()) {
        header = headerParser.get();
        System.out.println(header);
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

    if (headerParser.isParsed()) {
      while (!codedInputStream.isAtEnd()) {
        wseParser.parse(codedInputStream);
        intermediateWseEntry = true;

        if (wseParser.isParsed()) {
          System.out.println(wseParser.get());
          aggregationWindow.aggregate(wseParser.get());
          wseParser.reset();
          intermediateWseEntry = false;
        }
      }
    }
  }

}
