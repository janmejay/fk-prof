package fk.prof.metrics;

public class RecorderTag {
  public static final RecorderTag EMPTY = new RecorderTag("", "");
  private static final String prefix = "rt.";

  private final String value;

  public RecorderTag(String ip, String procName) {
    this.value = prefix + ip.replaceAll("\\W", "_") + '_' + Util.encodeTags(procName);
  }

  @Override
  public String toString() {
    return value;
  }
}
