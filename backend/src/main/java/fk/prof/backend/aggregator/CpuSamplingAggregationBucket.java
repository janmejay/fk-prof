package fk.prof.backend.aggregator;

import fk.prof.common.stacktrace.MethodIdLookup;
import fk.prof.common.stacktrace.cpusampling.CpuSamplingContextDetail;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

public class CpuSamplingAggregationBucket {
    private final MethodIdLookup methodIdLookup = new MethodIdLookup();
    private final ConcurrentHashMap<String, CpuSamplingContextDetail> contextLookup = new ConcurrentHashMap<>();
}
