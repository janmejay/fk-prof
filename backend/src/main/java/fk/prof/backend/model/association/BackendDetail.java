package fk.prof.backend.model.association;

import recording.Recorder;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class BackendDetail {
  private final String backendIPAddress;
  private final int loadReportIntervalInSeconds;
  private final int loadMissTolerance;
  private final Set<Recorder.ProcessGroup> associatedProcessGroups;

  private long lastReportedTick;
  private volatile long lastReportedTime;
  private volatile Float lastReportedLoad = null;

  public BackendDetail(String backendIPAddress, int loadReportIntervalInSeconds, int loadMissTolerance)
      throws IOException {
    this(backendIPAddress, loadReportIntervalInSeconds, loadMissTolerance, new HashSet<>());
  }

  public BackendDetail(String backendIPAddress, int loadReportIntervalInSeconds, int loadMissTolerance, Set<Recorder.ProcessGroup> associatedProcessGroups)
      throws IOException {
    if(backendIPAddress == null) {
      throw new IllegalArgumentException("Backend ip address cannot be null");
    }
    this.backendIPAddress = backendIPAddress;
    this.loadReportIntervalInSeconds = loadReportIntervalInSeconds;
    this.loadMissTolerance = loadMissTolerance;
    this.associatedProcessGroups = associatedProcessGroups == null ? new HashSet<>() : associatedProcessGroups;
    this.lastReportedTick = 0;
  }

  /**
   * Updates the load for this backend
   * NOTE: If backend dies and comes back up, it will send prevTick=Long.MAX_VALUE and currTick=1 in the first request so as to override previous reported load
   * @param load
   * @param prevTick
   * @param currTick
   */
  public synchronized void reportLoad(float load, long prevTick, long currTick) {
    if(this.lastReportedTick <= prevTick) {
      this.lastReportedTick = currTick;
      this.lastReportedLoad = load;
      this.lastReportedTime = System.nanoTime();
    }
  }

  public void associateProcessGroup(Recorder.ProcessGroup processGroup) {
    this.associatedProcessGroups.add(processGroup);
  }

  public void deAssociateProcessGroup(Recorder.ProcessGroup processGroup) {
    this.associatedProcessGroups.remove(processGroup);
  }

  /**
   * Indicates backend is defunct if no load has been reported ever or the last load report time exceeds threshold
   * @return
   */
  public boolean isDefunct() {
    return lastReportedLoad == null
        || ((System.nanoTime() - lastReportedTime) > (loadReportIntervalInSeconds * (loadMissTolerance + 1) * Math.pow(10, 9)));
  }

  public String getBackendIPAddress() {
    return this.backendIPAddress;
  }

  public Set<Recorder.ProcessGroup> getAssociatedProcessGroups() {
    return this.associatedProcessGroups;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof BackendDetail)) {
      return false;
    }

    BackendDetail other = (BackendDetail) o;
    return this.backendIPAddress.equals(other.backendIPAddress);
  }

  @Override
  public int hashCode() {
    final int PRIME = 31;
    int result = 1;
    result = result * PRIME + this.backendIPAddress.hashCode();
    return result;
  }

  public Recorder.ProcessGroups buildProcessGroupsProto() {
    return Recorder.ProcessGroups.newBuilder().addAllProcessGroup(associatedProcessGroups).build();
  }

}
