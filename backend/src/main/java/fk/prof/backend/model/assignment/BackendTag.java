package fk.prof.backend.model.assignment;

public class BackendTag {
  public static final BackendTag EMPTY = new BackendTag("");

  private final String value;

  private BackendTag(String value) {
    this.value = "b." + value;
  }

  public BackendTag(String host, int port) {
    this(String.join(".", host, Integer.toString(port)));
  }

  private static String sanitizeTagPart(String tagPart) {
    return tagPart.replace('.', '_');
  }

  @Override
  public String toString() {
    return value;
  }
}
