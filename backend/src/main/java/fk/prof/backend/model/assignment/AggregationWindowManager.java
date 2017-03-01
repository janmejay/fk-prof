package fk.prof.backend.model.assignment;

import fk.prof.backend.aggregator.AggregationWindow;
import fk.prof.backend.proto.BackendDTO;
import fk.prof.backend.service.AggregationWindowReadContext;
import fk.prof.backend.service.AggregationWindowWriteContext;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import recording.Recorder;

import java.util.Random;
import java.util.function.Function;

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

  private BackendDTO.WorkProfile workProfile = null;
  private int relevantAggregationWindowForWorkProfile = 0;
  private int currentAggregationWindow = 0;
  private final long aggregationWindowScheduleTimer;

  public AggregationWindowManager(Vertx vertx,
                                  int aggregationWindowDurationInMins,
                                  int aggregationWindowToleranceSecs,
                                  ProcessGroupDetail processGroupDetail,
                                  Function<Recorder.ProcessGroup, Future<BackendDTO.WorkProfile>> workForBackendRequestor,
                                  AggregationWindowWriteContext aggregationWindowWriteContext) {
    this.vertx = vertx;
    this.processGroupDetail = processGroupDetail;
    this.workForBackendRequestor = workForBackendRequestor;
    this.aggregationWindowWriteContext = aggregationWindowWriteContext;
    this.aggregationWindowScheduleTimer = vertx.setPeriodic(
        aggregationWindowDurationInMins * 60 * 1000l,
        timerId -> aggregationWindowStart());
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

  private void aggregationWindowStart() {
    currentAggregationWindow++;
    if (currentAggregationWindow == relevantAggregationWindowForWorkProfile) {
      int coveragePct = workProfile.getCoveragePct();
    } else {
      //TODO: log this as metric somewhere
      logger.warn("Skipping work assignments because work profile was not fetched for the aggregation window of process_group=" + processGroupDetail.getProcessGroup());
    }
  }

  private void aggregationWindowEnd(AggregationWindow aggregationWindow) {

  }
}
