package fk.prof.backend.model.request;

import recording.Recorder;

public class RecordedProfileHeader {

  private final long encodedVersion;
  private final Recorder.RecordingHeader recordingHeader;

  public RecordedProfileHeader(long encodedVersion, Recorder.RecordingHeader recordingHeader) {
    this.encodedVersion = encodedVersion;
    this.recordingHeader = recordingHeader;
  }

  public long getEncodedVersion() {
    return encodedVersion;
  }

  public Recorder.RecordingHeader getRecordingHeader() {
    return recordingHeader;
  }

  //TODO: remove
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("encoding version=" + encodedVersion).append('\n');
    builder.append("recording header=" + recordingHeader).append('\n');
    return builder.toString();
  }

}
