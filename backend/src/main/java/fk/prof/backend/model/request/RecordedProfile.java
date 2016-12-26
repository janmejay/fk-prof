package fk.prof.backend.model.request;

import recording.Recorder;

import java.util.ArrayList;
import java.util.List;

public class RecordedProfile {

  private RecordedProfileHeader header = null;
  private List<Recorder.Wse> wseEntries = new ArrayList<>();

  public RecordedProfile(RecordedProfileHeader header, List<Recorder.Wse> wseEntries) {
    this.header = header;
    this.wseEntries = wseEntries;
  }

  public RecordedProfileHeader getHeader() {
    return header;
  }

  public List<Recorder.Wse> getWseEntries() {
    return wseEntries;
  }

  //TODO: remove
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("profile header\n" + header).append('\n');
    for (Recorder.Wse wse : wseEntries) {
      builder.append("wse\n" + header).append('\n');
    }
    return builder.toString();
  }
}
