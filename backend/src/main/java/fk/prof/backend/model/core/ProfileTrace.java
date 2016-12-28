package fk.prof.backend.model.core;

public class ProfileTrace {
  private String name;
  private long coveragePct;

  public ProfileTrace(String name, long coveragePct) {
    this.name = name;
    this.coveragePct = coveragePct;
  }

  public String getName() {
    return name;
  }

  public long getCoveragePct() {
    return coveragePct;
  }
}
