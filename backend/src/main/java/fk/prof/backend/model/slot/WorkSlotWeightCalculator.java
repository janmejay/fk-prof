package fk.prof.backend.model.slot;

import fk.prof.backend.proto.BackendDTO;

import java.util.HashMap;
import java.util.Map;

public class WorkSlotWeightCalculator {
  private final static Map<BackendDTO.WorkType, Integer> weights = new HashMap<>();

  //TODO: Determine if weights for work type need to be picked from configuration?
  // In that case slotweightcalculator becomes a dependency to be injected everywhere
  static {
    weights.put(BackendDTO.WorkType.cpu_sample_work, 1);
    weights.put(BackendDTO.WorkType.thread_sample_work, 1);
    weights.put(BackendDTO.WorkType.monitor_contention_work, 1);
    weights.put(BackendDTO.WorkType.monitor_wait_work, 1);
  }

  public static int weight(BackendDTO.RecordingPolicy recordingPolicy) {
    int baseSlots = 0;
    if (recordingPolicy.getWorkCount() > 0) {
      for(BackendDTO.Work work: recordingPolicy.getWorkList()) {
        baseSlots += weights.get(work.getWType());
      }
    }
    return baseSlots;
  }
}
