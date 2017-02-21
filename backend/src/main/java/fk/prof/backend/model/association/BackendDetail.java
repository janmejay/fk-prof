package fk.prof.backend.model.association;

import recording.Recorder;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class BackendDetail {
  private final String backendIPAddress;
  private final int loadReportIntervalInSeconds;
  private final int loadMissTolerance;
  private final Set<Recorder.ProcessGroup> associatedProcessGroups;

  private final AtomicLong lastReportedTime;
  private Double lastReportedLoad = null;

  public BackendDetail(String backendIPAddress, int loadReportIntervalInSeconds, int loadMissTolerance, long initialReportTime)
      throws IOException {
    this(backendIPAddress, loadReportIntervalInSeconds, loadMissTolerance, new HashSet<>());
    this.lastReportedTime.set(initialReportTime);
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
    this.lastReportedTime = new AtomicLong(System.nanoTime());
  }

  public void reportLoad(double loadFactor, long reportTime) {
    long newLoadReportTime = this.lastReportedTime.updateAndGet(currentValue -> {
      if((reportTime - currentValue) > 0) {
        return reportTime;
      }
      return currentValue;
    });
    if(newLoadReportTime == reportTime) {
      this.lastReportedLoad = loadFactor;
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
        || ((System.nanoTime() - lastReportedTime.get()) > (loadReportIntervalInSeconds * (loadMissTolerance + 1) * Math.pow(10, 9)));
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
