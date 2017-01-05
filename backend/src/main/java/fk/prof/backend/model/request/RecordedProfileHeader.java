package fk.prof.backend.model.request;

import recording.Recorder;

public class RecordedProfileHeader {

  private final int encodedVersion;
  private final Recorder.RecordingHeader recordingHeader;

  public RecordedProfileHeader(int encodedVersion, Recorder.RecordingHeader recordingHeader) {
    this.encodedVersion = encodedVersion;
    this.recordingHeader = recordingHeader;
  }

  public int getEncodedVersion() {
    return encodedVersion;
  }

  public Recorder.RecordingHeader getRecordingHeader() {
    return recordingHeader;
  }

}
