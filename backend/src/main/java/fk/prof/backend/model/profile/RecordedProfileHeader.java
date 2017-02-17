package fk.prof.backend.model.profile;

import recording.Recorder;

public class RecordedProfileHeader {

  private final int encodingVersion;
  private final Recorder.RecordingHeader recordingHeader;

  public RecordedProfileHeader(int encodingVersion, Recorder.RecordingHeader recordingHeader) {
    this.encodingVersion = encodingVersion;
    this.recordingHeader = recordingHeader;
  }

  public int getEncodingVersion() {
    return encodingVersion;
  }

  public Recorder.RecordingHeader getRecordingHeader() {
    return recordingHeader;
  }

}
