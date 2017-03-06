package fk.prof.aggregation.model;

import fk.prof.aggregation.proto.AggregatedProfileModel.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class FinalizedAggregationWindow {
  private final String appId;
  private final String clusterId;
  private final String procId;
  private final LocalDateTime start;
  private final LocalDateTime endedAt;
  protected final Map<Long, FinalizedProfileWorkInfo> workInfoLookup;
  protected final FinalizedCpuSamplingAggregationBucket cpuSamplingAggregationBucket;

  public FinalizedAggregationWindow(String appId,
                                    String clusterId,
                                    String procId,
                                    LocalDateTime start,
                                    LocalDateTime endedAt,
                                    Map<Long, FinalizedProfileWorkInfo> workInfoLookup,
                                    FinalizedCpuSamplingAggregationBucket cpuSamplingAggregationBucket) {
    this.appId = appId;
    this.clusterId = clusterId;
    this.procId = procId;
    this.start = start;
    this.endedAt = endedAt;
    this.workInfoLookup = workInfoLookup;
    this.cpuSamplingAggregationBucket = cpuSamplingAggregationBucket;
  }

  public FinalizedProfileWorkInfo getDetailsForWorkId(long workId) {
    return this.workInfoLookup.get(workId);
  }

  //NOTE: This is computed on expiry of aggregation window, null otherwise. Having a getter here to make this testable
  public LocalDateTime getEndedAt() {
    return this.endedAt;
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
        && this.endedAt == null ? other.endedAt == null : this.endedAt.equals(other.endedAt)
        && this.workInfoLookup.equals(other.workInfoLookup)
        && this.cpuSamplingAggregationBucket.equals(other.cpuSamplingAggregationBucket);
  }

  protected Header buildHeaderProto(int version, WorkType workType) {
    return Header.newBuilder()
        .setFormatVersion(version)
        .setWorkType(workType)
        .setAggregationEndTime(endedAt == null ? null : endedAt.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_ZONED_DATE_TIME))
        .setAggregationStartTime(start.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_ZONED_DATE_TIME))
        .setAppId(appId)
        .setClusterId(clusterId)
        .setProcId(procId).build();
  }

  protected ProfilesSummary buildProfileSummaryProto(WorkType workType, TraceCtxList traces) {
    ProfilesSummary.Builder summaryBuilder = ProfilesSummary.newBuilder();
    // TODO group summaries by different visit sources.
    PerSourceProfileSummary.Builder defaultSourceSummaryBuilder = PerSourceProfileSummary.newBuilder();
    defaultSourceSummaryBuilder.setSourceInfo(ProfileSourceInfo.getDefaultInstance());

    for(Long id: workInfoLookup.keySet()) {
      ProfileWorkInfo profileInfo = workInfoLookup.get(id).buildProfileWorkInfoProto(workType, start, traces);
      if(profileInfo != null) {
        defaultSourceSummaryBuilder.addProfiles(profileInfo);
      }
    }

    summaryBuilder.addProfiles(defaultSourceSummaryBuilder);

    return summaryBuilder.build();
  }
}
