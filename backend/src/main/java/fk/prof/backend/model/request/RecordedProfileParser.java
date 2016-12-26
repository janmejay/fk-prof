package fk.prof.backend.model.request;

import fk.prof.backend.model.response.HttpFailure;
import io.vertx.core.buffer.Buffer;
import recording.Recorder;

import java.util.ArrayList;
import java.util.List;

public class RecordedProfileParser {

  private RecordedProfileHeader header = null;
  private List<Recorder.Wse> wseEntries = new ArrayList<>();

  private RecordedProfileHeaderParser headerParser = new RecordedProfileHeaderParser();
  private WseParser wseParser = new WseParser();
  private boolean intermediateWseEntry = false;

  /**
   * Returns true if header has been successfully parsed and no wse entry log is parsed partially
   *
   * @return parsed a valid recorded profile object or not
   */
  public boolean isParsed() {
    return headerParser.isParsed() && !intermediateWseEntry;
  }

  /**
   * Returns {@link RecordedProfile} if {@link #isParsed()} is true, null otherwise
   *
   * @return
   */
  public RecordedProfile get() {
    if (!this.isParsed()) {
      return null;
    }
    return new RecordedProfile(header, wseEntries);
  }

  /**
   * Reads buffer and updates internal state with parsed fields
   *
   * @param buffer
   * @param currentPos
   * @return starting unread position in buffer
   */
  public int parse(Buffer buffer, int currentPos) throws HttpFailure {
    if (!headerParser.isParsed()) {
      int newPos = headerParser.parse(buffer, currentPos);
      if (newPos == currentPos) {
        return currentPos;
      } else {
        currentPos = newPos;

        if (headerParser.isParsed()) {
          header = headerParser.get();
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
          wseEntries.add(wseParser.get());
          wseParser.reset();
          intermediateWseEntry = false;
        }
      }
    }

    return currentPos;
  }

}
