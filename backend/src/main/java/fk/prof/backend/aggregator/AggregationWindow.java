package fk.prof.backend.aggregator;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

public class AggregationWindow {
    private final ConcurrentHashMap<Long, AggregationStatus> workStatusLookup = new ConcurrentHashMap<>();
    private LocalDateTime start = null, endWithTolerance = null;

    private final CpuSamplingAggregationBucket cpuSamplingAggregationBucket = new CpuSamplingAggregationBucket();

    public AggregationWindow(LocalDateTime start, int windowDurationInMinutes, int toleranceInSeconds, long[] workIds) {
        this.start = start;
        this.endWithTolerance = this.start.plusMinutes(windowDurationInMinutes).plusSeconds(toleranceInSeconds);
        for(int i = 0;i < workIds.length;i++) {
            this.workStatusLookup.put(workIds[i], AggregationStatus.SCHEDULED);
        }
    }

    public void abortOngoingWork() {
        this.workStatusLookup.replaceAll((workId, status) -> {
            if(status.equals(AggregationStatus.ONGOING) || status.equals(AggregationStatus.ONGOING_PARTIAL)) {
                return AggregationStatus.ABORTED;
            } else {
                return status;
            }
        });
    }
}
