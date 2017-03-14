package fk.prof.backend.model.assignment;

import com.google.common.base.Preconditions;
import fk.prof.backend.model.aggregation.AggregationWindowLookupStore;
import fk.prof.backend.model.slot.WorkSlotPool;
import fk.prof.backend.proto.BackendDTO;
import fk.prof.backend.util.proto.RecorderProtoUtil;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import recording.Recorder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class AggregationWindowPlannerStore {
  private final Map<Recorder.ProcessGroup, AggregationWindowPlanner> lookup = new HashMap<>();

  private final Vertx vertx;
  private final AggregationWindowLookupStore aggregationWindowLookupStore;
  private final WorkSlotPool workSlotPool;
  private final Function<Recorder.ProcessGroup, Future<BackendDTO.WorkProfile>> workForBackendRequestor;
  private final WorkAssignmentScheduleBootstrapConfig workAssignmentScheduleBootstrapConfig;
  private final int aggregationWindowDurationInMins;
  private final int workProfileRefreshBufferInSecs;

  public AggregationWindowPlannerStore(Vertx vertx,
                                       int windowDurationInMins,
                                       int windowEndToleranceInSecs,
                                       int workProfileRefreshBufferInSecs,
                                       int schedulingBufferInSecs,
                                       int maxAcceptableDelayForWorkAssignmentInSecs,
                                       WorkSlotPool workSlotPool,
                                       AggregationWindowLookupStore aggregationWindowLookupStore,
                                       Function<Recorder.ProcessGroup, Future<BackendDTO.WorkProfile>> workForBackendRequestor) {
    this.vertx = Preconditions.checkNotNull(vertx);
    this.workForBackendRequestor = Preconditions.checkNotNull(workForBackendRequestor);
    this.aggregationWindowLookupStore = Preconditions.checkNotNull(aggregationWindowLookupStore);
    this.workSlotPool = Preconditions.checkNotNull(workSlotPool);
    this.workAssignmentScheduleBootstrapConfig = new WorkAssignmentScheduleBootstrapConfig(windowDurationInMins,
        windowEndToleranceInSecs,
        schedulingBufferInSecs,
        maxAcceptableDelayForWorkAssignmentInSecs);
    this.aggregationWindowDurationInMins = windowDurationInMins;
    this.workProfileRefreshBufferInSecs = workProfileRefreshBufferInSecs;
  }

  /**
   * @param processGroupContextForScheduling
   * @return true if aggregation window planner was not associated earlier. false otherwise
   */
  public boolean associateAggregationWindowPlannerIfAbsent(ProcessGroupContextForScheduling processGroupContextForScheduling) {
    if (!this.lookup.containsKey(processGroupContextForScheduling.getProcessGroup())) {
      AggregationWindowPlanner aggregationWindowPlanner = new AggregationWindowPlanner(
          vertx,
          aggregationWindowDurationInMins,
          workProfileRefreshBufferInSecs,
          workAssignmentScheduleBootstrapConfig,
          workSlotPool,
          processGroupContextForScheduling,
          aggregationWindowLookupStore,
          workForBackendRequestor);
      this.lookup.put(processGroupContextForScheduling.getProcessGroup(), aggregationWindowPlanner);
      return true;
    }
    return false;
  }

  public void deAssociateAggregationWindowPlanner(Recorder.ProcessGroup processGroup)
    throws IllegalStateException {
    AggregationWindowPlanner aggregationWindowPlanner = this.lookup.remove(processGroup);
    if(aggregationWindowPlanner == null) {
      throw new IllegalStateException("Unexpected state. No aggregation window planner associated with process_group=" + RecorderProtoUtil.processGroupCompactRepr(processGroup));
    }
    aggregationWindowPlanner.close();
  }
}
