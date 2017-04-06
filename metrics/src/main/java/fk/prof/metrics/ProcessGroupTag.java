package fk.prof.metrics;

public class ProcessGroupTag {
  public static final ProcessGroupTag EMPTY = new ProcessGroupTag("", "", "");
  private static final String prefix = "pgt.";

  private final String value;

  public ProcessGroupTag(final String appId, final String clusterId, final String procName) {
    this.value = prefix + Util.encodeTags(appId, clusterId, procName);
  }

  @Override
  public String toString() {
    return value;
  }
}
