package fk.prof.backend.model.association;

public class ReportLoadPayload {
  private double load;

  public  ReportLoadPayload() {}
  public ReportLoadPayload(double load) {
    this.load = load;
  }

  public double getLoad() {
    return this.load;
  }
}
