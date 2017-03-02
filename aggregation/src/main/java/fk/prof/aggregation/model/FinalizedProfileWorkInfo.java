package fk.prof.aggregation.model;

import fk.prof.aggregation.proto.AggregatedProfileModel.*;
import fk.prof.aggregation.proto.AggregatedProfileModel.ProfileWorkInfo.TraceCtxToCoveragePctMap;
import fk.prof.aggregation.state.AggregationState;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

public class FinalizedProfileWorkInfo {
  private final int recorderVersion;
  private final int recorderIdx;
  private final AggregationState state;
  private final LocalDateTime startedAt;
  private final LocalDateTime endedAt;
  private final Map<String, Integer> traceCoverages;
  private final Map<WorkType, Integer> samples;

  // TODO add source info also.
  public FinalizedProfileWorkInfo(int recorderVersion,
                                  int recorderIdx,
                                  AggregationState state,
                                  LocalDateTime startedAt,
                                  LocalDateTime endedAt,
                                  Map<String, Integer> traceCoverages,
                                  Map<WorkType, Integer> samples) {
    this.recorderVersion = recorderVersion;
    this.recorderIdx = recorderIdx;
    this.state = state;
    this.startedAt = startedAt;
    this.endedAt = endedAt;
    this.traceCoverages = traceCoverages;
    this.samples = samples;
  }

  //NOTE: Exposing this to make the class more testable since startedAt has generated value
  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  //NOTE: Exposing this to make the class more testable since endedAt has generated value
  public LocalDateTime getEndedAt() {
    return endedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof FinalizedProfileWorkInfo)) {
      return false;
    }

    FinalizedProfileWorkInfo other = (FinalizedProfileWorkInfo) o;
    return this.recorderVersion == other.recorderVersion
        && this.state.equals(other.state)
        && this.startedAt.equals(other.startedAt)
        && this.endedAt.equals(other.endedAt)
        && this.traceCoverages.equals(other.traceCoverages)
        && this.samples.equals(other.samples);
  }

  protected Set<String> recordedTraces() {
    return traceCoverages.keySet();
  }

  protected ProfileWorkInfo buildProfileWorkInfoProto(WorkType workType, LocalDateTime aggregationStartTime, TraceCtxNames traces) {
    if(workType == null || samples.containsKey(workType)) {
      ProfileWorkInfo.Builder builder = ProfileWorkInfo.newBuilder()
              .setRecorderVersion(recorderVersion)
              .setRecorderIdx(recorderIdx)
              .setStartOffset((int) aggregationStartTime.until(startedAt, ChronoUnit.SECONDS))
              .setDuration((int) startedAt.until(endedAt, ChronoUnit.SECONDS))
              .setStatus(toAggregationStatusProto(state));

      if(workType != null) {
        builder.addSampleCount(ProfileWorkInfo.SampleCount.newBuilder().setWorkType(workType).setSampleCount(samples.getOrDefault(workType, 0)));
      }
      else {
        // add all the sample counts
        for(Map.Entry<WorkType, Integer> wsSample : samples.entrySet()) {
          builder.addSampleCount(ProfileWorkInfo.SampleCount.newBuilder().setWorkType(wsSample.getKey()).setSampleCount(wsSample.getValue()));
        }
      }

      int index = 0;
      for(String traceName: traces.getNameList()) {
        Integer cvrg = traceCoverages.getOrDefault(traceName, null);
        if(cvrg != null) {
          builder.addTraceCoverageMap(TraceCtxToCoveragePctMap.newBuilder().setTraceCtxIdx(index).setCoveragePct(cvrg));
        }
        ++index;
      }

      return builder.build();
    }
    return null;
  }

  protected ProfileWorkInfo buildProfileWorkInfoProto(LocalDateTime aggregationStartTime, TraceCtxNames traces) {
    return buildProfileWorkInfoProto(null, aggregationStartTime, traces);
  }

  private AggregationStatus toAggregationStatusProto(AggregationState status) {
    switch (status) {
      case ABORTED: return AggregationStatus.Aborted;
      case COMPLETED: return AggregationStatus.Completed;
      case ONGOING: throw new IllegalArgumentException("ONGOING state is not a terminal state");
      case ONGOING_PARTIAL: throw new IllegalArgumentException("ONGOING_PARTIAL state is not a terminal state");
      case PARTIAL: return AggregationStatus.Partial;
      case RETRIED: return AggregationStatus.Retried;
      case SCHEDULED: return AggregationStatus.Scheduled;
      default: throw new IllegalArgumentException(state.name() + " state is not supported");
    }
  }
}