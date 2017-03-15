package fk.prof.backend.util;

public class BitOperationUtil {

  public static long constructLongFromInts(int mostSignificantBits, int leastSignificantBits) {
    return (((long)mostSignificantBits) << 32) | (leastSignificantBits & 0xffffffffL);
  }

}
