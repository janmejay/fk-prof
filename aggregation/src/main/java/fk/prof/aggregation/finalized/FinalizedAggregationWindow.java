package fk.prof.aggregation.finalized;

import fk.prof.aggregation.SerializableAggregationEntity;

import java.time.LocalDateTime;
import java.util.Map;

public class FinalizedAggregationWindow implements SerializableAggregationEntity {
  private final String appId;
  private final String clusterId;
  private final String procId;
  private final LocalDateTime start;
  private final LocalDateTime endWithTolerance;
  private final Map<Long, FinalizedProfileWorkInfo> workInfoLookup;
  private final FinalizedCpuSamplingAggregationBucket cpuSamplingAggregationBucket;

  public FinalizedAggregationWindow(String appId,
                                    String clusterId,
                                    String procId,
                                    LocalDateTime start,
                                    LocalDateTime endWithTolerance,
                                    Map<Long, FinalizedProfileWorkInfo> workInfoLookup,
                                    FinalizedCpuSamplingAggregationBucket cpuSamplingAggregationBucket) {
    this.appId = appId;
    this.clusterId = clusterId;
    this.procId = procId;
    this.start = start;
    this.endWithTolerance = endWithTolerance;
    this.workInfoLookup = workInfoLookup;
    this.cpuSamplingAggregationBucket = cpuSamplingAggregationBucket;
  }

  public FinalizedProfileWorkInfo getDetailsForWorkId(long workId) {
    return this.workInfoLookup.get(workId);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof FinalizedAggregationWindow)) {
      return false;
    }

    FinalizedAggregationWindow other = (FinalizedAggregationWindow) o;
    return this.appId.equals(other.appId)
        && this.clusterId.equals(other.clusterId)
        && this.procId.equals(other.procId)
        && this.start.equals(other.start)
        && this.endWithTolerance.equals(other.endWithTolerance)
        && this.workInfoLookup.equals(other.workInfoLookup)
        && this.cpuSamplingAggregationBucket.equals(other.cpuSamplingAggregationBucket);
  }
}
