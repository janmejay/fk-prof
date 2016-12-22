package fk.prof.backend.aggregator.store;

import com.google.common.base.Charsets;
import fk.prof.backend.aggregator.bucket.CpuSamplingAggregationBucket;
import fk.prof.common.Utils;

import java.util.concurrent.ConcurrentHashMap;

public class CpuSamplingAggregationStore {
    private ConcurrentHashMap<Integer, CpuSamplingAggregationBucket> store = new ConcurrentHashMap<>();

//    public Integer getOrAdd(String appId, String clusterId, String procId) {
//        return store.computeIfAbsent(methodSignature, (key ->
//                Utils.getHashFunctionForMurmur3_32().newHasher().putString(key, Charsets.UTF_8).hash().asInt()
//        ));
//    }

}
