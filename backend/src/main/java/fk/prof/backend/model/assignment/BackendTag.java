package fk.prof.backend.model.assignment;

public class BackendTag {
  public static final BackendTag EMPTY = new BackendTag("");

  private final String value;

  private BackendTag(String value) {
    this.value = "bt." + value;
  }

  public BackendTag(String host, int port) {
    this(String.join("_", host, Integer.toString(port)));
  }

  @Override
  public String toString() {
    return value;
  }
}
