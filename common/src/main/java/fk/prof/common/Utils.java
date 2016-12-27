package fk.prof.common;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Longs;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedInts;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class Utils {
    private static HashFunction murmur3_32 = Hashing.murmur3_32();

    public static HashFunction getHashFunctionForMurmur3_32() {
        return murmur3_32;
    }

    public byte[] unsignedIntToByteArray(long value) {
        return Longs.toByteArray(value);
    }

}
