package fk.prof.backend.worker;

import com.google.common.primitives.Ints;
import com.google.protobuf.InvalidProtocolBufferException;
import fk.prof.backend.model.assignment.WorkAssignmentManager;
import fk.prof.backend.util.IPAddressUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import recording.Recorder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class WorkAssignmentScheduler extends AbstractVerticle {
  private static int POLL_WINDOW_SECONDS = 5;

  private final WorkAssignmentManager workAssignmentManager;

  public WorkAssignmentScheduler(WorkAssignmentManager workAssignmentManager) {
    this.workAssignmentManager = workAssignmentManager;
  }

  @Override
  public void start() {
    schedulePollWindowTimer();
    registerPollRequestConsumer();
  }

  private void schedulePollWindowTimer() {
    vertx.setPeriodic(POLL_WINDOW_SECONDS * 1000, timerId -> {
      this.workAssignmentManager.startPollWindow();
    });
  }

  private void registerPollRequestConsumer() {
    vertx.eventBus().consumer("recorder.poll", (Message<byte[]> message) -> {
      try {
        Recorder.PollReq pollReq = Recorder.PollReq.parseFrom(message.body());
        Recorder.WorkAssignment nextWorkAssignment = this.workAssignmentManager.receivePoll(
            pollReq.getRecorderInfo(), pollReq.getWorkLastIssued());
        Recorder.PollRes pollRes = Recorder.PollRes.newBuilder()
            .setAssignment(nextWorkAssignment)
            .setControllerVersion(config().getInteger("backend.version"))
            .setControllerId(Ints.fromByteArray(IPAddressUtil.getIPAddressAsBytes()))
            .setLocalTime(nextWorkAssignment == null
                ? LocalDateTime.now(Clock.systemUTC()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : nextWorkAssignment.getIssueTime())
            .build();
        message.reply(pollRes.toByteArray());
      } catch (InvalidProtocolBufferException ex) {
        message.fail(400, "Error parsing poll request body");
      } catch (IllegalArgumentException ex) {
        message.fail(400, ex.getMessage());
      }
    });
  }
}
