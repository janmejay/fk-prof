package fk.prof.backend.aggregator;

import fk.prof.backend.exception.AggregationFailure;
import fk.prof.backend.model.request.RecordedProfileIndexes;
import recording.Recorder;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

public class AggregationWindow implements IAggregationWindow {
  private String appId;
  private String clusterId;
  private String procId;

  private final ConcurrentHashMap<Long, ProfileWorkInfo> workStatusLookup = new ConcurrentHashMap<>();
  private LocalDateTime start = null, endWithTolerance = null;

  private final CpuSamplingAggregationBucket cpuSamplingAggregationBucket = new CpuSamplingAggregationBucket();

  public AggregationWindow(String appId, String clusterId, String procId,
                           LocalDateTime start, int windowDurationInMinutes, int toleranceInSeconds, long[] workIds) {
    this.appId = appId;
    this.clusterId = clusterId;
    this.procId = procId;
    this.start = start;
    this.endWithTolerance = this.start.plusMinutes(windowDurationInMinutes).plusSeconds(toleranceInSeconds);
    for (int i = 0; i < workIds.length; i++) {
      this.workStatusLookup.put(workIds[i], new ProfileWorkInfo());
    }
  }

  public String getAppId() {
    return appId;
  }

  public String getClusterId() {
    return clusterId;
  }

  public String getProcId() {
    return procId;
  }

  public ProfileWorkInfo getWorkInfo(long workId) {
    return workStatusLookup.get(workId);
  }

  public boolean startReceivingProfile(long workId) {
    boolean updated = true;
    ProfileWorkInfo workInfo = this.workStatusLookup.get(workId);
    if (workInfo.getStatus().equals(AggregationStatus.SCHEDULED)) {
      workInfo.setStatus(AggregationStatus.ONGOING);
    } else if (workInfo.getStatus().equals(AggregationStatus.PARTIAL)) {
      workInfo.setStatus(AggregationStatus.ONGOING_PARTIAL);
    } else {
      updated = false;
    }

    return updated;
  }

  public void abortOngoingProfiles() {
    this.workStatusLookup.replaceAll((workId, workInfo) -> {
      if (workInfo.getStatus().equals(AggregationStatus.ONGOING) || workInfo.getStatus().equals(AggregationStatus.ONGOING_PARTIAL)) {
        workInfo.setStatus(AggregationStatus.ABORTED);
      }
      return workInfo;
    });
  }

  public void aggregate(Recorder.Wse wse, RecordedProfileIndexes indexes) throws AggregationFailure {
    switch(wse.getWType()) {
      case cpu_sample_work:
        Recorder.StackSampleWse stackSampleWse = wse.getCpuSampleEntry();
        if(stackSampleWse == null) {
          throw new AggregationFailure(String.format("work type=%s did not have associated samples", wse.getWType()));
        }
        cpuSamplingAggregationBucket.aggregate(stackSampleWse, indexes);
        break;
      default:
        throw new AggregationFailure(String.format("Aggregation not supported for work type=%s", wse.getWType()));
    }
  }

  public void updateWorkInfo(long workId, Recorder.Wse wse) {
    ProfileWorkInfo workInfo = getWorkInfo(workId);
    for(Recorder.TraceContext trace: wse.getIndexedData().getTraceCtxList()) {
      workInfo.addTrace(trace.getTraceName(), trace.getCoveragePct());
    }
  }
}
