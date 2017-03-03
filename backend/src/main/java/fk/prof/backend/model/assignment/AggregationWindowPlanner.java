package fk.prof.backend.model.assignment;

import com.google.common.base.Preconditions;
import fk.prof.aggregation.model.FinalizedAggregationWindow;
import fk.prof.backend.aggregator.AggregationWindow;
import fk.prof.backend.model.aggregation.AggregationWindowLookupStore;
import fk.prof.backend.proto.BackendDTO;
import fk.prof.backend.util.BitOperationUtil;
import fk.prof.backend.util.proto.RecorderProtoUtil;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import recording.Recorder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AggregationWindowPlanner {
  private static int workIdCounter = 0;
  private static final int MILLIS_IN_SEC = 1000;
  private static final Logger logger = LoggerFactory.getLogger(AggregationWindowPlanner.class);
  private static final int BACKEND_IDENTIFIER;

  static {
    Random random = new Random();
    BACKEND_IDENTIFIER = random.nextInt();
  }

  private final Vertx vertx;
  private final WorkAssignmentScheduleFactory workAssignmentScheduleFactory;
  private final SimultaneousWorkAssignmentCounter simultaneousWorkAssignmentCounter;
  private final ProcessGroupContextForScheduling processGroupContextForScheduling;
  private final AggregationWindowLookupStore aggregationWindowLookupStore;
  private final Function<Recorder.ProcessGroup, Future<BackendDTO.WorkProfile>> workForBackendRequestor;
  private final Recorder.ProcessGroup processGroup;
  private final int aggregationWindowDurationInMins;
  private final int workProfileRefreshBufferInSecs;
  private final long aggregationWindowScheduleTimer;

  private AggregationWindow aggregationWindow = null;
  private BackendDTO.WorkProfile workProfile = null;
  private int currentlyOccupiedWorkAssignmentSlots = 0;
  private int currentAggregationWindow = 0;
  private int relevantAggregationWindowForWorkProfile = 0;

  public AggregationWindowPlanner(Vertx vertx,
                                  int aggregationWindowDurationInMins,
                                  int workProfileRefreshBufferInSecs,
                                  WorkAssignmentScheduleFactory workAssignmentScheduleFactory,
                                  SimultaneousWorkAssignmentCounter simultaneousWorkAssignmentCounter,
                                  ProcessGroupContextForScheduling processGroupContextForScheduling,
                                  AggregationWindowLookupStore aggregationWindowLookupStore,
                                  Function<Recorder.ProcessGroup, Future<BackendDTO.WorkProfile>> workForBackendRequestor) {
    this.vertx = Preconditions.checkNotNull(vertx);
    this.processGroupContextForScheduling = Preconditions.checkNotNull(processGroupContextForScheduling);
    this.processGroup = processGroupContextForScheduling.getProcessGroup();
    this.workForBackendRequestor = Preconditions.checkNotNull(workForBackendRequestor);
    this.aggregationWindowLookupStore = Preconditions.checkNotNull(aggregationWindowLookupStore);
    this.workAssignmentScheduleFactory = Preconditions.checkNotNull(workAssignmentScheduleFactory);
    this.simultaneousWorkAssignmentCounter = Preconditions.checkNotNull(simultaneousWorkAssignmentCounter);
    this.aggregationWindowDurationInMins = aggregationWindowDurationInMins;
    this.workProfileRefreshBufferInSecs = workProfileRefreshBufferInSecs;

    this.aggregationWindowScheduleTimer = vertx.setPeriodic(
        aggregationWindowDurationInMins * 60 * MILLIS_IN_SEC,
        timerId -> aggregationWindowSwitcher());
  }

  public void close() {
    vertx.cancelTimer(aggregationWindowScheduleTimer);
    expireCurrentAggregationWindow();
  }

  /**
   * This method will be called before start of every aggregation window
   * There should be sufficient buffer to allow completion of this method before the next aggregation window starts
   * Not adding any guarantees here, but a lead of few minutes for this method's execution should ensure that the request to get work should complete in time for next aggregation window
   */
  private void getWorkForNextAggregationWindow(int aggregationWindowIndex) {
    this.workForBackendRequestor.apply(processGroup).setHandler(ar -> {
      if(ar.failed()) {
        //Cannot get work from leader, so chill out and let this aggregation window go by
        //TODO: Metric to indicate failure to get work for this process group from leader
        workProfile = null;
      } else {
        if(ar.succeeded()) {
          workProfile = ar.result();
        }
      }
      relevantAggregationWindowForWorkProfile = aggregationWindowIndex;
    });
  }

  private void aggregationWindowSwitcher() {
    expireCurrentAggregationWindow();
    currentAggregationWindow++;
    if (currentAggregationWindow == relevantAggregationWindowForWorkProfile && workProfile != null) {
      try {
        int targetRecordersCount = processGroupContextForScheduling.getRecorderTargetCountToMeetCoverage(workProfile.getCoveragePct());
        Recorder.WorkAssignment.Builder[] workAssignmentBuilders = new Recorder.WorkAssignment.Builder[targetRecordersCount];
        long workIds[] = new long[targetRecordersCount];
        for (int i = 0; i < workIds.length; i++) {
          Recorder.WorkAssignment.Builder workAssignmentBuilder = Recorder.WorkAssignment.newBuilder()
              .setWorkId(BitOperationUtil.constructLongFromInts(BACKEND_IDENTIFIER, workIdCounter++))
              .addAllWork(workProfile.getWorkList().stream()
                  .map(RecorderProtoUtil::translateWorkFromBackendDTO)
                  .collect(Collectors.toList()))
              .setDescription(workProfile.getDescription())
              .setDuration(workProfile.getDuration());

          workAssignmentBuilders[i] = workAssignmentBuilder;
          workIds[i] = workAssignmentBuilder.getWorkId();
        }
        setupAggregationWindow(workAssignmentBuilders, workIds);
      } catch (Exception ex) {
        //TODO: log this as metric somewhere, fatal failure wrt to aggregation window
        logger.error("Skipping work assignments and setup of aggregation window because of unexpected error while processing for process_group=" + processGroup);
      }
    } else {
      //TODO: log this as metric somewhere, fatal failure wrt to aggregation window
      logger.error("Skipping work assignments and setup of aggregation window because work profile was not fetched in time for process_group=" + processGroup);
    }

    vertx.setTimer(((aggregationWindowDurationInMins * 60) - workProfileRefreshBufferInSecs) * MILLIS_IN_SEC,
        timerId -> getWorkForNextAggregationWindow(currentAggregationWindow + 1));
  }

  private void setupAggregationWindow(Recorder.WorkAssignment.Builder[] workAssignmentBuilders, long[] workIds) {
    LocalDateTime windowStart = LocalDateTime.now(Clock.systemUTC());
    int maxConcurrentSlotsForScheduling = simultaneousWorkAssignmentCounter.acquireSlots(processGroup, workProfile);
    try {
      WorkAssignmentSchedule workAssignmentSchedule = workAssignmentScheduleFactory.getNewWorkAssignmentSchedule(workAssignmentBuilders,
          workProfile.getCoveragePct(),
          maxConcurrentSlotsForScheduling,
          workProfile.getDuration(),
          workProfile.getInterval());
      currentlyOccupiedWorkAssignmentSlots = workAssignmentSchedule.getMaxConcurrentlyScheduledEntries();
      aggregationWindow = new AggregationWindow(
          processGroup.getAppId(),
          processGroup.getCluster(),
          processGroup.getProcName(),
          windowStart,
          workIds);
      processGroupContextForScheduling.updateWorkAssignmentSchedule(workAssignmentSchedule);
    } catch (Exception ex) {
      simultaneousWorkAssignmentCounter.releaseSlots(maxConcurrentSlotsForScheduling);
      reset();
      throw ex;
    }
    simultaneousWorkAssignmentCounter.releaseSlots(maxConcurrentSlotsForScheduling - currentlyOccupiedWorkAssignmentSlots);
    aggregationWindowLookupStore.associateAggregationWindow(workIds, aggregationWindow);
  }

  private void expireCurrentAggregationWindow() {
    if(aggregationWindow != null) {
      simultaneousWorkAssignmentCounter.releaseSlots(currentlyOccupiedWorkAssignmentSlots);
      FinalizedAggregationWindow finalizedAggregationWindow = aggregationWindow.expireWindow(aggregationWindowLookupStore);
      //TODO: Serialization and persistence of aggregated profile should hookup here

      reset(); //this should be the last statement in this method
    }
  }

  private void reset() {
    aggregationWindow = null;
    currentlyOccupiedWorkAssignmentSlots = 0;
    processGroupContextForScheduling.updateWorkAssignmentSchedule(null);
  }
}
