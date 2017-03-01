package fk.prof.backend.aggregator;

import fk.prof.aggregation.FinalizableBuilder;
import fk.prof.aggregation.model.FinalizedAggregationWindow;
import fk.prof.aggregation.model.FinalizedProfileWorkInfo;
import fk.prof.aggregation.state.AggregationState;
import fk.prof.backend.exception.AggregationFailure;
import fk.prof.backend.model.profile.RecordedProfileIndexes;
import fk.prof.backend.service.AggregationWindowWriteContext;
import recording.Recorder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AggregationWindow extends FinalizableBuilder<FinalizedAggregationWindow> {
  private final String appId;
  private final String clusterId;
  private final String procId;
  private LocalDateTime start = null, endWithTolerance = null;

  private final ConcurrentHashMap<Long, ProfileWorkInfo> workInfoLookup = new ConcurrentHashMap<>();
  private final CpuSamplingAggregationBucket cpuSamplingAggregationBucket = new CpuSamplingAggregationBucket();

  public AggregationWindow(String appId, String clusterId, String procId,
                           LocalDateTime start, long[] workIds) {
    this.appId = appId;
    this.clusterId = clusterId;
    this.procId = procId;
    this.start = start;
    for (int i = 0; i < workIds.length; i++) {
      this.workInfoLookup.put(workIds[i], new ProfileWorkInfo());
    }
  }

  public AggregationState startProfile(long workId, int recorderVersion, LocalDateTime startedAt) throws AggregationFailure {
    ensureEntityIsWriteable();

    try {
      ProfileWorkInfo workInfo = this.workInfoLookup.get(workId);
      return workInfo.startProfile(recorderVersion, startedAt);
    } catch (IllegalStateException ex) {
      throw new AggregationFailure(String.format("Error starting profile for work_id=%d, recorder_version=%d, startedAt=%s",
          workId, recorderVersion, startedAt.toString()), ex);
    }
  }

  public AggregationState completeProfile(long workId) throws AggregationFailure {
    ensureEntityIsWriteable();

    try {
      ProfileWorkInfo workInfo = this.workInfoLookup.get(workId);
      return workInfo.completeProfile();
    } catch (IllegalStateException ex) {
      throw new AggregationFailure(String.format("Error completing profile for work_id=%d", workId), ex);
    }
  }

  public AggregationState abandonProfile(long workId) throws AggregationFailure {
    ensureEntityIsWriteable();

    try {
      ProfileWorkInfo workInfo = this.workInfoLookup.get(workId);
      return workInfo.abandonProfile();
    } catch (IllegalStateException ex) {
      throw new AggregationFailure(String.format("Error abandoning profile for work_id=%d", workId), ex);
    }
  }

  /**
   * Does following tasks required to expire aggregation window:
   * > Marks status of ongoing profiles as aborted
   * @param aggregationWindowWriteContext
   * @return
   */
  public FinalizedAggregationWindow expireWindow(AggregationWindowWriteContext aggregationWindowWriteContext) {
    abortOngoingProfiles();
    long[] workIds = this.workInfoLookup.keySet().stream().mapToLong(Long::longValue).toArray();
    aggregationWindowWriteContext.deAssociateAggregationWindow(workIds);
    this.endWithTolerance = LocalDateTime.now(Clock.systemUTC());
    return finalizeEntity();
  }

  /**
   * Aborts all in-flight profiles. Should be called when aggregation window expires
   */
  private void abortOngoingProfiles() throws AggregationFailure {
    ensureEntityIsWriteable();

    for (Map.Entry<Long, ProfileWorkInfo> entry : workInfoLookup.entrySet()) {
      try {
        entry.getValue().abortProfile();
      } catch (IllegalStateException ex) {
        throw new AggregationFailure(String.format("Error aborting profile for work_id=%d", entry.getKey()), ex);
      }
    }
  }

  public boolean hasProfileBeenStarted(long workId) {
    ProfileWorkInfo workInfo = this.workInfoLookup.get(workId);
    if (workInfo == null) {
      throw new IllegalArgumentException(String.format("No profile for work_id=%d exists in the aggregation window",
          workId));
    }
    return workInfo.hasProfileBeenStarted();
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

    ProfileWorkInfo workInfo = workInfoLookup.get(workId);
    if (workInfo == null) {
      throw new AggregationFailure(String.format("Cannot find work id=%d association in the aggregation window", workId), true);
    }
    workInfo.updateWSESpecificDetails(wse);
  }

  @Override
  protected FinalizedAggregationWindow buildFinalizedEntity() {
    Map<Long, FinalizedProfileWorkInfo> finalizedWorkInfoLookup = workInfoLookup.entrySet()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey,
            entry -> entry.getValue().finalizeEntity()));

    return new FinalizedAggregationWindow(
        appId, clusterId, procId, start, endWithTolerance, finalizedWorkInfoLookup,
        cpuSamplingAggregationBucket.finalizeEntity()
    );
  }
}
