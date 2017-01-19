package fk.prof.backend.aggregator;

import fk.prof.aggregation.state.AggregationState;
import fk.prof.aggregation.FinalizableAggregationEntity;
import fk.prof.aggregation.finalized.FinalizedAggregationWindow;
import fk.prof.backend.exception.AggregationFailure;
import fk.prof.backend.model.request.RecordedProfileIndexes;
import recording.Recorder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AggregationWindow extends FinalizableAggregationEntity<FinalizedAggregationWindow> {
  private final String appId;
  private final String clusterId;
  private final String procId;
  private LocalDateTime start = null, endWithTolerance = null;

  private final ConcurrentHashMap<Long, ProfileWorkInfo> workDetailLookup = new ConcurrentHashMap<>();
  private final CpuSamplingAggregationBucket cpuSamplingAggregationBucket = new CpuSamplingAggregationBucket();

  public AggregationWindow(String appId, String clusterId, String procId,
                           LocalDateTime start, int windowDurationInMinutes, int toleranceInSeconds, long[] workIds) {
    this.appId = appId;
    this.clusterId = clusterId;
    this.procId = procId;
    this.start = start;
    this.endWithTolerance = this.start.plusMinutes(windowDurationInMinutes).plusSeconds(toleranceInSeconds);
    for (int i = 0; i < workIds.length; i++) {
      this.workDetailLookup.put(workIds[i], new ProfileWorkInfo());
    }
  }

  public AggregationState startProfile(long workId, int recorderVersion, LocalDateTime startedAt) throws AggregationFailure {
    ensureEntityIsWriteable();

    try {
      ProfileWorkInfo workInfo = this.workDetailLookup.get(workId);
      return workInfo.startProfile(recorderVersion, startedAt);
    } catch (AggregationFailure ex) {
      throw new AggregationFailure(String.format("Error starting profile for work_id=%d", workId), ex);
    }
  }

  public AggregationState completeProfile(long workId) throws AggregationFailure {
    ensureEntityIsWriteable();

    try {
      ProfileWorkInfo workInfo = this.workDetailLookup.get(workId);
      return workInfo.completeProfile();
    } catch (AggregationFailure ex) {
      throw new AggregationFailure(String.format("Error completing profile for work_id=%d", workId), ex);
    }
  }

  public AggregationState abandonProfile(long workId) throws AggregationFailure {
    ensureEntityIsWriteable();

    try {
      ProfileWorkInfo workInfo = this.workDetailLookup.get(workId);
      return workInfo.abandonProfile();
    } catch (AggregationFailure ex) {
      throw new AggregationFailure(String.format("Error abandoning profile for work_id=%d", workId), ex);
    }
  }

  /**
   * Aborts all in-flight profiles. Should be called when aggregation window expires
   */
  public void abortOngoingProfiles() throws AggregationFailure {
    ensureEntityIsWriteable();

    for (Map.Entry<Long, ProfileWorkInfo> entry: workDetailLookup.entrySet()) {
      try {
        entry.getValue().abortProfile();
      } catch (AggregationFailure ex) {
        throw new AggregationFailure(String.format("Error aborting profile for work_id=%d", entry.getKey()), ex);
      }
    }
  }

  public void aggregate(Recorder.Wse wse, RecordedProfileIndexes indexes) throws AggregationFailure {
    ensureEntityIsWriteable();

    switch (wse.getWType()) {
      case cpu_sample_work:
        Recorder.StackSampleWse stackSampleWse = wse.getCpuSampleEntry();
        if (stackSampleWse == null) {
          throw new AggregationFailure(String.format("work type=%s did not have associated samples", wse.getWType()));
        }
        cpuSamplingAggregationBucket.aggregate(stackSampleWse, indexes);
        break;
      default:
        throw new AggregationFailure(String.format("Aggregation not supported for work type=%s", wse.getWType()));
    }
  }

  public void updateWorkInfo(long workId, Recorder.Wse wse) {
    ensureEntityIsWriteable();

    ProfileWorkInfo workInfo = workDetailLookup.get(workId);
    if (workInfo == null) {
      throw new AggregationFailure(String.format("Cannot find work id=%d association in the aggregation window", workId), true);
    }
    workInfo.updateWSESpecificDetails(wse);
  }

  @Override
  protected FinalizedAggregationWindow buildFinalizedEntity() {
    //TODO: Implement translation layer to finalized entity
    return null;
  }
}
