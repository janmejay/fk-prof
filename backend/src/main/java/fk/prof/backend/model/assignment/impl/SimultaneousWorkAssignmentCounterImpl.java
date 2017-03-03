package fk.prof.backend.model.assignment.impl;

import fk.prof.backend.model.assignment.SimultaneousWorkAssignmentCounter;
import fk.prof.backend.proto.BackendDTO;
import recording.Recorder;

public class SimultaneousWorkAssignmentCounterImpl implements SimultaneousWorkAssignmentCounter {
  private final int maxAllowedConcurrentWorkAssignmentSlots;
  private int currentlyAcquiredWorkAssignmentSlots = 0;

  public SimultaneousWorkAssignmentCounterImpl(int maxAllowedConcurrentWorkAssignmentSlots) {
    this.maxAllowedConcurrentWorkAssignmentSlots = maxAllowedConcurrentWorkAssignmentSlots;
  }

  /**
   * TODO: Should be refactored to take process group and work prototype as input and accordingly determine slots
   * @return
   */
  public int acquireSlots(Recorder.ProcessGroup processGroup, BackendDTO.WorkProfile workProfile) {
    int availableSlots = maxAllowedConcurrentWorkAssignmentSlots - currentlyAcquiredWorkAssignmentSlots;
    int slotsAcquired = availableSlots >= 3 ? 3 : availableSlots;
    currentlyAcquiredWorkAssignmentSlots += slotsAcquired;
    return slotsAcquired;
  }

  public void releaseSlots(int slotsToRelease) {
    if (slotsToRelease < 0) {
      throw new IllegalArgumentException("Slots to be released cannot be a negative number");
    }
    currentlyAcquiredWorkAssignmentSlots -= slotsToRelease;
  }
}
