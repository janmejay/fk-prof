package fk.prof.backend.aggregator;

import fk.prof.backend.exception.AggregationFailure;
import recording.Recorder;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

public class DummyAggregationWindow implements IAggregationWindow {
  private final ConcurrentHashMap<Long, AggregationStatus> workStatusLookup = new ConcurrentHashMap<>();

  public DummyAggregationWindow(long[] workIds) {
    for (int i = 0; i < workIds.length; i++) {
      this.workStatusLookup.put(workIds[i], AggregationStatus.SCHEDULED);
    }
  }

  public AggregationStatus getStatus(long workId) {
    return workStatusLookup.get(workId);
  }

  public boolean startReceivingProfile(long workId) {
    return true;
  }

  public void abortOngoingProfiles() {
  }

  public void aggregate(Recorder.Wse wse) throws AggregationFailure {
    System.out.println(wse);
  }
}
