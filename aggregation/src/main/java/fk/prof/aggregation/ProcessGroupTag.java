package fk.prof.aggregation;

public class ProcessGroupTag {
  public static final ProcessGroupTag EMPTY = new ProcessGroupTag("");

  private final String value;

  private ProcessGroupTag(String value) {
    this.value = "pg." + value;
  }

  public ProcessGroupTag(final String appId, final String clusterId, final String procName) {
    this(String.join(".", appId, clusterId, procName));
  }

  private static String sanitizeTagPart(String tagPart) {
    return tagPart.replace('.', '_');
  }

  @Override
  public String toString() {
    return value;
  }
}
