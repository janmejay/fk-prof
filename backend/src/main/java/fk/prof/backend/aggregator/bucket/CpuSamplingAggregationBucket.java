package fk.prof.backend.aggregator.bucket;

import fk.prof.common.stacktrace.MethodIdLookup;
import fk.prof.common.stacktrace.cpusampling.CpuSamplingContextDetail;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

public class CpuSamplingAggregationBucket {
    private final String appId;
    private final String clusterId;
    private final String procId;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;

    private final MethodIdLookup methodIdLookup = new MethodIdLookup();
    private final ConcurrentHashMap<String, CpuSamplingContextDetail> contextLookup = new ConcurrentHashMap<>();

    public CpuSamplingAggregationBucket(String appId, String clusterId, String procId) {
        this.appId = appId;
        this.clusterId = clusterId;
        this.procId = procId;
    }

}
