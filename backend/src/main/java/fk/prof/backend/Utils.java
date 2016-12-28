package fk.prof.backend;

import com.google.common.primitives.Longs;

import java.util.Arrays;

public class Utils {

  public static byte[] unsignedIntToByteArray(long value) {
    return Arrays.copyOfRange(Longs.toByteArray(value), 4, 8);
  }

}
