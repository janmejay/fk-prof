package fk.prof.common.stacktrace;

import com.google.common.base.Charsets;
import fk.prof.common.Utils;

import java.util.concurrent.ConcurrentHashMap;

public class MethodIdLookup {

    private ConcurrentHashMap<String, Integer> lookup = new ConcurrentHashMap<>();

    public Integer getOrAdd(String methodSignature) {
        return lookup.computeIfAbsent(methodSignature, (key ->
                Utils.getHashFunctionForMurmur3_32().newHasher().putString(key, Charsets.UTF_8).hash().asInt()
        ));
    }
}
