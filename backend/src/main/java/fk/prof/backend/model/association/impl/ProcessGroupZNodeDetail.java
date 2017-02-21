package fk.prof.backend.model.association.impl;

import recording.Recorder;

public class ProcessGroupZNodeDetail {
  private final String zNodePath;
  private final Recorder.ProcessGroup processGroup;

  public ProcessGroupZNodeDetail(String zNodePath, Recorder.ProcessGroup processGroup) {
    this.zNodePath = zNodePath;
    this.processGroup = processGroup;
  }

  public String getzNodePath() {
    return zNodePath;
  }

  public Recorder.ProcessGroup getProcessGroup() {
    return processGroup;
  }
}
