package fk.prof.backend.aggregator;

import fk.prof.backend.exception.AggregationFailure;
import fk.prof.backend.model.request.RecordedProfileIndexes;
import recording.Recorder;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

public class DummyAggregationWindow implements IAggregationWindow {
  private final ConcurrentHashMap<Long, ProfileWorkInfo> workStatusLookup = new ConcurrentHashMap<>();

  public DummyAggregationWindow(long[] workIds) {
    for (int i = 0; i < workIds.length; i++) {
      this.workStatusLookup.put(workIds[i], new ProfileWorkInfo());
    }
  }

  public ProfileWorkInfo getWorkInfo(long workId) {
    return workStatusLookup.get(workId);
  }

  public boolean startReceivingProfile(long workId) {
    return true;
  }

  public void abortOngoingProfiles() {
  }

  public void aggregate(Recorder.Wse wse, RecordedProfileIndexes indexes) throws AggregationFailure {
    System.out.println(wse);
  }

  public void updateWorkInfo(long workId, Recorder.Wse wse) {
  }
}
