package fk.prof.backend.model.assignment;

import com.google.common.base.Preconditions;
import fk.prof.aggregation.model.FinalizedAggregationWindow;
import fk.prof.backend.aggregator.AggregationWindow;
import fk.prof.backend.model.aggregation.ActiveAggregationWindows;
import fk.prof.backend.model.slot.WorkSlotPool;
import fk.prof.backend.model.slot.WorkSlotWeightCalculator;
import fk.prof.backend.proto.BackendDTO;
import fk.prof.backend.util.BitOperationUtil;
import fk.prof.backend.util.proto.BackendDTOProtoUtil;
import fk.prof.backend.util.proto.RecorderProtoUtil;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import recording.Recorder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AggregationWindowPlanner {
  private static int workIdCounter = 1;
  private static final int MILLIS_IN_SEC = 1000;
  private static final Logger logger = LoggerFactory.getLogger(AggregationWindowPlanner.class);

  private final Vertx vertx;
  private final int backendId;
  private final WorkAssignmentScheduleBootstrapConfig workAssignmentScheduleBootstrapConfig;
  private final WorkSlotPool workSlotPool;
  private final ProcessGroupContextForScheduling processGroupContextForScheduling;
  private final ActiveAggregationWindows activeAggregationWindows;
  private final Function<Recorder.ProcessGroup, Future<BackendDTO.RecordingPolicy>> policyForBackendRequestor;
  private final Consumer<FinalizedAggregationWindow> aggregationWindowWriter;

  private final Recorder.ProcessGroup processGroup;
  private final int aggregationWindowDurationInSecs;
  private final int policyRefreshBufferInSecs;
  private final Future<Long> aggregationWindowScheduleTimer;

  private AggregationWindow currentAggregationWindow = null;
  private BackendDTO.RecordingPolicy latestRecordingPolicy = null;

  private int currentAggregationWindowIndex = 0;
  private int relevantAggregationWindowIndexForRecordingPolicy = 0;
  private List<WorkSlotPool.WorkSlot> occupiedSlots = null;

  public AggregationWindowPlanner(Vertx vertx,
                                  int backendId,
                                  int aggregationWindowDurationInSecs,
                                  int policyRefreshBufferInSecs,
                                  WorkAssignmentScheduleBootstrapConfig workAssignmentScheduleBootstrapConfig,
                                  WorkSlotPool workSlotPool,
                                  ProcessGroupContextForScheduling processGroupContextForScheduling,
                                  ActiveAggregationWindows activeAggregationWindows,
                                  Function<Recorder.ProcessGroup, Future<BackendDTO.RecordingPolicy>> policyForBackendRequestor,
                                  Consumer<FinalizedAggregationWindow> aggregationWindowWriter) {
    this.vertx = Preconditions.checkNotNull(vertx);
    this.backendId = backendId;
    this.processGroupContextForScheduling = Preconditions.checkNotNull(processGroupContextForScheduling);
    this.processGroup = processGroupContextForScheduling.getProcessGroup();
    this.policyForBackendRequestor = Preconditions.checkNotNull(policyForBackendRequestor);
    this.aggregationWindowWriter = aggregationWindowWriter;
    this.activeAggregationWindows = Preconditions.checkNotNull(activeAggregationWindows);
    this.workAssignmentScheduleBootstrapConfig = Preconditions.checkNotNull(workAssignmentScheduleBootstrapConfig);
    this.workSlotPool = Preconditions.checkNotNull(workSlotPool);
    this.aggregationWindowDurationInSecs = aggregationWindowDurationInSecs;
    this.policyRefreshBufferInSecs = policyRefreshBufferInSecs;

    this.aggregationWindowScheduleTimer = Future.future();
    getWorkForNextAggregationWindow(currentAggregationWindowIndex + 1).setHandler(ar -> {
      aggregationWindowSwitcher();

      // From vertx docs:
      // Keep in mind that the timer will fire on a periodic basis.
      // If your periodic treatment takes a long amount of time to proceed, your timer events could run continuously or even worse : stack up.
      // NOTE: The above is a fringe scenario since aggregation window duration is going to be in excess of 20 minutes
      // Still, there is a way to detect if this build-up happens. If aggregation window switch event happens before work profile is fetched, we publish a metric
      // If /leader/work API latency is within bounds but this metric is high, this implies a build-up of aggregation window events
      long periodicTimerId = vertx.setPeriodic(aggregationWindowDurationInSecs * MILLIS_IN_SEC,
          timerId -> aggregationWindowSwitcher());
      this.aggregationWindowScheduleTimer.complete(periodicTimerId);
    });
  }

  /**
   * This expires current aggregation window and cancels scheduling of upcoming aggregation windows
   * To be called when leader de-associates relevant process group from the backend
   */
  public void close() {
    aggregationWindowScheduleTimer.setHandler(ar -> {
      if(ar.succeeded()) {
        vertx.cancelTimer(ar.result());
      }
    });
    expireCurrentAggregationWindow();
  }

  /**
   * This method will be called before start of every aggregation window
   * There should be sufficient buffer to allow completion of this method before the next aggregation window starts
   */
  private Future<Void> getWorkForNextAggregationWindow(int aggregationWindowIndex) {
    Future<Void> result = Future.future();
    latestRecordingPolicy = null;
    this.policyForBackendRequestor.apply(processGroup).setHandler(ar -> {
      //Ensure that the policy is still being fetched for the next aggregation window. If not, then retire this method because some other timer will take care of fetching the relevant work
      if(aggregationWindowIndex == currentAggregationWindowIndex + 1) {
        relevantAggregationWindowIndexForRecordingPolicy = aggregationWindowIndex;
        if (ar.failed()) {
          //Cannot fetch work from leader, so chill out and let this aggregation window go by
          //TODO: Metric to indicate failure to fetch work for this process group from leader
          logger.error("Error fetching work from leader for process_group=" + RecorderProtoUtil.processGroupCompactRepr(processGroup) + ", error=" + ar.cause().getMessage());
          result.fail(ar.cause());
        } else {
          latestRecordingPolicy = ar.result();
          if (logger.isDebugEnabled()) {
            logger.debug("Fetched work successfully from leader for process_group=" + RecorderProtoUtil.processGroupCompactRepr(processGroup));
          }
          result.complete();
        }
      } else {
        result.fail("Stale check for work");
      }
    });
    return result;
  }

  private void aggregationWindowSwitcher() {
    expireCurrentAggregationWindow();
    currentAggregationWindowIndex++;

    if (currentAggregationWindowIndex == relevantAggregationWindowIndexForRecordingPolicy && latestRecordingPolicy != null) {
      if(logger.isDebugEnabled()) {
        logger.debug("Initializing aggregation window with index=" + currentAggregationWindowIndex +
            ", process_group=" + RecorderProtoUtil.processGroupCompactRepr(processGroup) +
            ", recording_policy=" + BackendDTOProtoUtil.recordingPolicyCompactRepr(latestRecordingPolicy));
      }
      try {
        if(latestRecordingPolicy.getCoveragePct() == 0) {
          logger.info("Skipping aggregation window with index=" + currentAggregationWindowIndex +
              " for process_group=" + RecorderProtoUtil.processGroupCompactRepr(processGroup) +
              " because coverage is zero");
        } else {
          int targetRecordersCount = processGroupContextForScheduling.getRecorderTargetCountToMeetCoverage(latestRecordingPolicy.getCoveragePct());
          if (targetRecordersCount == 0) {
            logger.info("Skipping aggregation window with index=" + currentAggregationWindowIndex +
                " for process_group=" + RecorderProtoUtil.processGroupCompactRepr(processGroup) +
                " because no healthy recorders are known");
          } else {
            Recorder.WorkAssignment.Builder[] workAssignmentBuilders = new Recorder.WorkAssignment.Builder[targetRecordersCount];
            long workIds[] = new long[targetRecordersCount];
            for (int i = 0; i < workIds.length; i++) {
              Recorder.WorkAssignment.Builder workAssignmentBuilder = Recorder.WorkAssignment.newBuilder()
                  .setWorkId(BitOperationUtil.constructLongFromInts(backendId, workIdCounter++))
                  .addAllWork(latestRecordingPolicy.getWorkList().stream()
                      .map(RecorderProtoUtil::translateWorkFromBackendDTO)
                      .collect(Collectors.toList()))
                  .setDescription(latestRecordingPolicy.getDescription())
                  .setDuration(latestRecordingPolicy.getDuration());

              workAssignmentBuilders[i] = workAssignmentBuilder;
              workIds[i] = workAssignmentBuilder.getWorkId();
            }
            setupAggregationWindow(workAssignmentBuilders, workIds);
            logger.info("Initialized aggregation window with index=" + currentAggregationWindowIndex +
                ", process_group=" + RecorderProtoUtil.processGroupCompactRepr(processGroup) +
                ", recording_policy=" + BackendDTOProtoUtil.recordingPolicyCompactRepr(latestRecordingPolicy) +
                ", work_count=" + targetRecordersCount);
          }
        }
      } catch (Exception ex) {
        reset();
        //TODO: log this as metric somewhere, fatal failure wrt to aggregation window
        logger.error("Skipping work assignments and setup of aggregation window because of error while processing for process_group=" + RecorderProtoUtil.processGroupCompactRepr(processGroup), ex);
      }
    } else {
      //TODO: log this as metric somewhere, fatal failure wrt to aggregation window
      logger.error("Skipping work assignments and setup of aggregation window because work profile was not fetched in time for process_group=" + RecorderProtoUtil.processGroupCompactRepr(processGroup));
    }

    vertx.setTimer((aggregationWindowDurationInSecs - policyRefreshBufferInSecs) * MILLIS_IN_SEC,
        timerId -> {
      int windowIndex = currentAggregationWindowIndex + 1;
      Future<Void> fut = getWorkForNextAggregationWindow(windowIndex);
      fut.setHandler(ar -> {
        if(ar.failed()) {
          if(logger.isDebugEnabled()) {
            logger.debug("Failure when fetching work for aggregation window index=" + windowIndex, ar.cause());
          }
        }
      });
    });
  }

  private void setupAggregationWindow(Recorder.WorkAssignment.Builder[] workAssignmentBuilders, long[] workIds)
      throws Exception {
    LocalDateTime windowStart = LocalDateTime.now(Clock.systemUTC());
    WorkAssignmentSchedule workAssignmentSchedule = new WorkAssignmentSchedule(workAssignmentScheduleBootstrapConfig, workAssignmentBuilders, latestRecordingPolicy.getDuration());
    int requiredSlots = workAssignmentSchedule.getMaxOverlap() * WorkSlotWeightCalculator.weight(latestRecordingPolicy);

    occupiedSlots = workSlotPool.acquire(requiredSlots);
    currentAggregationWindow = new AggregationWindow(
        processGroup.getAppId(),
        processGroup.getCluster(),
        processGroup.getProcName(),
        windowStart,
        aggregationWindowDurationInSecs,
        workIds,
        latestRecordingPolicy.getDuration());
    processGroupContextForScheduling.updateWorkAssignmentSchedule(workAssignmentSchedule);
    activeAggregationWindows.associateAggregationWindow(workIds, currentAggregationWindow);
  }

  private void expireCurrentAggregationWindow() {
    if(currentAggregationWindow != null) {
      try {
        FinalizedAggregationWindow finalizedAggregationWindow = currentAggregationWindow.expireWindow(activeAggregationWindows);
        logger.info("Initiating serialization and write of aggregated profile");
        aggregationWindowWriter.accept(finalizedAggregationWindow);
      } finally {
        reset();
      }
    }
  }

  private void reset() {
    //Release slots if holding any slots currently
    workSlotPool.release(occupiedSlots);
    occupiedSlots = null;
    currentAggregationWindow = null;
    processGroupContextForScheduling.updateWorkAssignmentSchedule(null);
  }
}
