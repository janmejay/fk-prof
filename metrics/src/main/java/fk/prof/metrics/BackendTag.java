package fk.prof.metrics;

public class BackendTag {
  public static final BackendTag EMPTY = new BackendTag("", 0);
  private static final String prefix = "bt.";

  private final String value;

  public BackendTag(String host, int port) {
    this.value = prefix + host.replaceAll("\\W", "_") + '_' + Integer.toString(port);
  }

  @Override
  public String toString() {
    return value;
  }
}
