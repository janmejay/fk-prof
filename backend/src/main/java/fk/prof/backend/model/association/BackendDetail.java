package fk.prof.backend.model.association;

import fk.prof.backend.proto.BackendDTO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

public class BackendDetail {
  private final String backendIPAddress;
  private final int reportingFrequencyInSeconds;
  private final int maxAllowedSkips;
  private final Set<BackendDTO.ProcessGroup> associatedProcessGroups;

  private Double lastReportedLoad = null;
  //Last reported time is initialized with epochSecond=0 to ensure isDefunct returns true until backend reports its load to the leader
  private LocalDateTime lastReportedTime = LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC);

  public BackendDetail(String backendIPAddress, int reportingFrequencyInSeconds, int maxAllowedSkips)
      throws IOException {
    this(backendIPAddress, reportingFrequencyInSeconds, maxAllowedSkips, null);
  }

  public BackendDetail(String backendIPAddress, int reportingFrequencyInSeconds, int maxAllowedSkips, byte[] processGroupsBytes)
      throws IOException {
    this.backendIPAddress = backendIPAddress;
    this.reportingFrequencyInSeconds = reportingFrequencyInSeconds;
    this.maxAllowedSkips = maxAllowedSkips;
    this.associatedProcessGroups = deserializeProcessGroups(processGroupsBytes);
  }

  public void reportLoad(double loadFactor) {
    this.lastReportedLoad = loadFactor;
    this.lastReportedTime = LocalDateTime.now(Clock.systemUTC());
  }

  public void associateProcessGroup(BackendDTO.ProcessGroup processGroup) {
    this.associatedProcessGroups.add(processGroup);
  }

  public void deAssociateProcessGroup(BackendDTO.ProcessGroup processGroup) {
    this.associatedProcessGroups.remove(processGroup);
  }

  public boolean isDefunct() {
    return timeElapsedSinceLastReport(ChronoUnit.SECONDS) > (reportingFrequencyInSeconds * (maxAllowedSkips + 1));
  }

  public String getBackendIPAddress() {
    return this.backendIPAddress;
  }

  public Set<BackendDTO.ProcessGroup> getAssociatedProcessGroups() {
    return this.associatedProcessGroups;
  }

  public byte[] getAssociatedProcessGroupsAsBytes()
      throws IOException {
    return serializeProcessGroups(this.associatedProcessGroups);
  }

  private long timeElapsedSinceLastReport(ChronoUnit chronoUnit) {
    return chronoUnit.between(lastReportedTime, LocalDateTime.now(Clock.systemUTC()));
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
    return this.backendIPAddress == null ? other.backendIPAddress == null : this.backendIPAddress.equals(other.backendIPAddress);
  }

  @Override
  public int hashCode() {
    final int PRIME = 31;
    int result = 1;
    result = result * PRIME + (this.backendIPAddress == null ? 0 : this.backendIPAddress.hashCode());
    return result;
  }

  public static Set<BackendDTO.ProcessGroup> deserializeProcessGroups(byte[] rawBytes)
      throws IOException {
    Set<BackendDTO.ProcessGroup> processGroups = new HashSet<>();
    if(rawBytes == null) {
      return processGroups;
    }

    ByteArrayInputStream inputStream = new ByteArrayInputStream(rawBytes);
    BackendDTO.ProcessGroup processGroup;

    while ((processGroup = BackendDTO.ProcessGroup.parseDelimitedFrom(inputStream)) != null) {
      processGroups.add(processGroup);
    }
    return processGroups;
  }

  public static byte[] serializeProcessGroups(Set<BackendDTO.ProcessGroup> processGroups)
      throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    if(processGroups != null) {
      for (BackendDTO.ProcessGroup processGroup : processGroups) {
        processGroup.writeDelimitedTo(outputStream);
      }
    }
    return outputStream.toByteArray();
  }

}
