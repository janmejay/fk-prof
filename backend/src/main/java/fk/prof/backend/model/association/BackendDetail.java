package fk.prof.backend.model.association;

import recording.Recorder;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class BackendDetail {
  private static final double NANOSECONDS_IN_SECOND = Math.pow(10, 9);
  private final String backendIPAddress;
  private final long thresholdForDefunctInNanoSeconds;
  private final Set<Recorder.ProcessGroup> associatedProcessGroups;

  private long lastReportedTick;
  //lastReportedTime is null unless backend reports stable load at least once (i.e. currTick reported by backend > 0)
  private volatile Long lastReportedTime;
  private float lastReportedLoad;

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
    this.thresholdForDefunctInNanoSeconds = (long)(loadReportIntervalInSeconds * (loadMissTolerance + 1) * NANOSECONDS_IN_SECOND);
    this.associatedProcessGroups = associatedProcessGroups == null ? new HashSet<>() : associatedProcessGroups;
    this.lastReportedTick = 0;
  }

  /**
   * Updates the load for this backend
   * NOTE: If backend dies and comes back up, it will send currTick=0 in the first request so as to override previous reported state, other than last reported time
   * @param load
   * @param currTick
   * @return true if successfully updated load report time, false otherwise
   */
  public synchronized boolean reportLoad(float load, long currTick) {
    boolean timeUpdated = false;
    if(currTick == 0 || this.lastReportedTick <= currTick) {
      this.lastReportedTick = currTick;
      if(currTick > 0) {
        this.lastReportedTime = System.nanoTime();
        timeUpdated = true;
      }
      this.lastReportedLoad = load;
    }
    return timeUpdated;
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
    return lastReportedTime == null ||
        ((System.nanoTime() - lastReportedTime) > thresholdForDefunctInNanoSeconds);
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
