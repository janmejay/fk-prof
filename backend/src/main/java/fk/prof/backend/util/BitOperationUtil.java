package fk.prof.backend.util;

public class BitOperationUtil {

  public static long constructLongFromInts(int mostSignificantBits, int leastSignificantBits) {
    return (((long)mostSignificantBits) << 32) | (leastSignificantBits & 0xffffffffL);
  }

  public static IntTuple deconstructLongToInts(long val) {
    int x = (int)(val >> 32);
    int y = (int)val;
    return new IntTuple(x, y);
  }

  private static class IntTuple {
    private final int x;
    private final int y;

    IntTuple(int x, int y) {
      this.x = x;
      this.y = y;
    }

    public int getX() {
      return x;
    }

    public int getY() {
      return y;
    }
  }

}
