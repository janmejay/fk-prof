package fk.prof.backend.model.assignment;

import fk.prof.backend.util.ProtoUtil;
import recording.Recorder;

import java.util.*;
import java.util.stream.Collectors;

public class WorkAssignmentManagerImpl implements WorkAssignmentManager {

  private final Map<Recorder.ProcessGroup, PollView> pollViewLookup = new HashMap<>();
  private final int pollWindowCountForHealth;

  public WorkAssignmentManagerImpl(int pollWindowCountForHealth) {
    this.pollWindowCountForHealth = pollWindowCountForHealth;
  }

  @Override
  public Recorder.WorkAssignment receivePoll(Recorder.RecorderInfo recorderInfo, Recorder.WorkResponse lastIssuedWorkResponse) {
    Recorder.ProcessGroup processGroup = ProtoUtil.mapRecorderInfoToProcessGroup(recorderInfo);
    PollView pollView = this.pollViewLookup.get(processGroup);
    if(pollView == null) {
      throw new IllegalArgumentException("Process group " + ProtoUtil.processGroupCompactRepr(processGroup) + " not associated with the backend");
    }
    pollView.receivePoll(recorderInfo.getIp(), lastIssuedWorkResponse);
    //TODO: Generate work assignment
    return null;
  }

  @Override
  public void startPollWindow() {
    this.pollViewLookup.values().forEach(pollView -> pollView.startPollWindow());
  }

  public static class PollView {
    private final int pollWindowCountForHealth;
    private final Map<String, RecordingMachineDetail> recordingMachineLookup = new HashMap<>();
    private final List<RecordingMachineDetail> recordingMachineDetails = new ArrayList<>();

    public PollView(int pollWindowCountForHealth) {
      this.pollWindowCountForHealth = pollWindowCountForHealth;
    }

    public void startPollWindow() {
      this.recordingMachineDetails.forEach(recordingMachineDetail -> recordingMachineDetail.startPollWindow());
    }

    public void receivePoll(String ipAddress, Recorder.WorkResponse lastIssuedWorkResponse) {
      RecordingMachineDetail recordingMachineDetail = this.recordingMachineLookup.computeIfAbsent(ipAddress, key -> {
        RecordingMachineDetail value = new RecordingMachineDetail(key, pollWindowCountForHealth);
        this.recordingMachineDetails.add(value);
        return value;
      });
      recordingMachineDetail.receivePoll(lastIssuedWorkResponse);
    }

    public int healthyCount() {
      return recordingMachineDetails.stream().filter(m -> m.healthy()).collect(Collectors.toList()).size();
    }

  }

  public static class RecordingMachineDetail {
    private final String ipAddress;
    private final int pollWindowCountForHealth;
    private int healthState = 0;
    private long assignedWorkId = 0;
    private long currentWorkId = 0;
    private Recorder.WorkResponse.WorkState currentWorkState;

    public RecordingMachineDetail(String ipAddress, int pollWindowCountForHealth) {
      this.ipAddress = ipAddress;
      this.pollWindowCountForHealth = pollWindowCountForHealth;
    }

    public void startPollWindow() {
      this.healthState = this.healthState << 1;
    }

    public void receivePoll(Recorder.WorkResponse lastIssuedWorkResponse) {
      this.healthState = this.healthState & 1;
      this.currentWorkId = lastIssuedWorkResponse.getWorkId();
      this.currentWorkState = lastIssuedWorkResponse.getWorkState();
    }

    public boolean canAcceptWork() {
      if((this.assignedWorkId != this.currentWorkId) || !Recorder.WorkResponse.WorkState.complete.equals(currentWorkState)) {
        return false;
      }
      return true;
    }

    public void assignWork(long workId) {
      this.assignedWorkId = workId;
    }

    public boolean healthy() {
      int mask = ((int)Math.pow(2, pollWindowCountForHealth) - 1) << 1;
      int masked = this.healthState & mask;
      return Integer.bitCount(masked) == pollWindowCountForHealth ? true : false;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof RecordingMachineDetail)) {
        return false;
      }

      RecordingMachineDetail other = (RecordingMachineDetail) o;
      return this.ipAddress == null ? other.ipAddress == null : this.ipAddress.equals(other.ipAddress);
    }

    @Override
    public int hashCode() {
      final int PRIME = 31;
      int result = 1;
      result = result * PRIME + (this.ipAddress == null ? 0 : this.ipAddress.hashCode());
      return result;
    }
  }
}
