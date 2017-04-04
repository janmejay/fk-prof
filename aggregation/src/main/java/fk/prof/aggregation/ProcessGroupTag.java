package fk.prof.aggregation;

public class ProcessGroupTag {
  public static final ProcessGroupTag EMPTY = new ProcessGroupTag("", "", "");

  private final String value;

  private ProcessGroupTag(String value) {
    this.value = "pgt." + value;
  }

  public ProcessGroupTag(final String appId, final String clusterId, final String procName) {
    this(String.join("_", appId, clusterId, procName));
  }

  @Override
  public String toString() {
    return value;
  }
}
