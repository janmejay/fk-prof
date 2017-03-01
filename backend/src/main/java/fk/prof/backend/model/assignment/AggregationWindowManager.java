package fk.prof.backend.model.assignment;

import com.google.common.base.Preconditions;
import fk.prof.backend.aggregator.AggregationWindow;
import fk.prof.backend.proto.BackendDTO;
import fk.prof.backend.service.AggregationWindowWriteContext;
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

public class AggregationWindowManager {
  public final static int BACKEND_IDENTIFIER;

  static {
    Random random = new Random();
    BACKEND_IDENTIFIER = random.nextInt();
  }

  private static Logger logger = LoggerFactory.getLogger(AggregationWindowManager.class);
  private static int workIdCounter = 0;
  private static int aggregationWindowCounter = 0;

  private final Vertx vertx;
  private final ProcessGroupDetail processGroupDetail;
  private final Function<Recorder.ProcessGroup, Future<BackendDTO.WorkProfile>> workForBackendRequestor;
  private final AggregationWindowWriteContext aggregationWindowWriteContext;
  private final int aggregationWindowDurationInMins;
  private final int aggregationWindowToleranceInSecs;
  private final int schedulingBufferInSecs;
  private final int concurrentSchedulingLimit;
  private final long aggregationWindowScheduleTimer;

  private BackendDTO.WorkProfile workProfile = null;
  private int relevantAggregationWindowForWorkProfile = 0;
  private AggregationWindow aggregationWindow = null;
  private int currentAggregationWindow = 0;

  public AggregationWindowManager(Vertx vertx,
                                  int aggregationWindowDurationInMins,
                                  int aggregationWindowToleranceInSecs,
                                  int schedulingBufferInSecs,
                                  int concurrentSchedulingLimit,
                                  ProcessGroupDetail processGroupDetail,
                                  Function<Recorder.ProcessGroup, Future<BackendDTO.WorkProfile>> workForBackendRequestor,
                                  AggregationWindowWriteContext aggregationWindowWriteContext) {
    this.vertx = Preconditions.checkNotNull(vertx);
    this.processGroupDetail = Preconditions.checkNotNull(processGroupDetail);
    this.workForBackendRequestor = Preconditions.checkNotNull(workForBackendRequestor);
    this.aggregationWindowWriteContext = Preconditions.checkNotNull(aggregationWindowWriteContext);
    this.aggregationWindowDurationInMins = aggregationWindowDurationInMins;
    this.aggregationWindowToleranceInSecs = aggregationWindowToleranceInSecs;
    this.schedulingBufferInSecs = schedulingBufferInSecs;
    this.concurrentSchedulingLimit = concurrentSchedulingLimit;

    this.aggregationWindowScheduleTimer = vertx.setPeriodic(
        aggregationWindowDurationInMins * 60 * 1000l,
        timerId -> aggregationWindowSwitcher());
  }

  public void close() {
    vertx.cancelTimer(aggregationWindowScheduleTimer);
  }

  /**
   * This method will be called before start of every aggregation window
   * There should be sufficient buffer to allow completion of this method before the next aggregation window starts
   * Not adding any guarantees here, but a lead of few minutes for this method's execution
   * should ensure that the request to get work should complete in time for next aggregation window
   */
  private void getWorkForNextAggregationWindow() {
    this.workForBackendRequestor.apply(processGroupDetail.getProcessGroup()).setHandler(ar -> {
      if(ar.failed()) {
        //Cannot get work from leader, so chill out and let this aggregation window go by
        //TODO: Metric to indicate failure to get work for this process group from leader
        workProfile = null;
      } else {
        if(ar.succeeded()) {
          workProfile = ar.result();
        }
      }
      relevantAggregationWindowForWorkProfile++;
    });
  }

  private void aggregationWindowSwitcher() {
    if(aggregationWindow != null) {
      aggregationWindow.expireWindow(aggregationWindowWriteContext);
    }

    currentAggregationWindow++;
    if (currentAggregationWindow == relevantAggregationWindowForWorkProfile) {
      RecorderSupplier recorderSupplier = processGroupDetail.getRecorderSupplier(workProfile.getCoveragePct());
      Recorder.WorkAssignment.Builder[] workAssignments = new Recorder.WorkAssignment.Builder[recorderSupplier.getTargetRecordersCount()];
      long workIds[] = new long[recorderSupplier.getTargetRecordersCount()];
      LocalDateTime windowStart = LocalDateTime.now(Clock.systemUTC());

      for(int i = 0; i < recorderSupplier.getTargetRecordersCount(); i++) {
        Recorder.WorkAssignment.Builder workAssignmentBuilder = Recorder.WorkAssignment.newBuilder()
            .setWorkId(workIdCounter++)
            .addAllWork(workProfile.getWorkList().stream()
                .map(RecorderProtoUtil::translateWorkFromBackendDTO)
                .collect(Collectors.toList()))
            .setDescription(workProfile.getDescription())
            .setDuration(workProfile.getDuration());

        workAssignments[i] = workAssignmentBuilder;
        workIds[i] = workAssignmentBuilder.getWorkId();
      }

      aggregationWindow = new AggregationWindow(
          processGroupDetail.getProcessGroup().getAppId(),
          processGroupDetail.getProcessGroup().getCluster(),
          processGroupDetail.getProcessGroup().getProcName(),
          windowStart,
          workIds);

      WorkAssignmentSchedule workAssignmentSchedule = new WorkAssignmentSchedule(workAssignments,
          workProfile.getCoveragePct(),
          aggregationWindowDurationInMins,
          aggregationWindowToleranceInSecs,
          schedulingBufferInSecs,
          concurrentSchedulingLimit,
          workProfile.getDuration(),
          workProfile.getInterval());
      //TODO: Do something with workassignmentschedule
    } else {
      //TODO: log this as metric somewhere
      logger.warn("Skipping work assignments because work profile was not fetched for the aggregation window of process_group=" + processGroupDetail.getProcessGroup());
    }
  }
}
