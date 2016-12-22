package fk.prof.common;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

public class Utils {
    private static HashFunction murmur3_32 = Hashing.murmur3_32();

    public static HashFunction getHashFunctionForMurmur3_32() {
        return murmur3_32;
    }

}
