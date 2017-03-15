package fk.prof.backend;

import fk.prof.backend.model.assignment.RecorderDetail;
import fk.prof.backend.model.assignment.RecorderIdentifier;
import org.junit.Assert;
import org.junit.Test;
import recording.Recorder;

import java.time.Clock;
import java.time.LocalDateTime;

public class RecorderDetailTest {

  @Test
  public void testImpactOfPollOnRecorderDetail() throws InterruptedException {
    Recorder.RecorderInfo.Builder recorderInfoBuilder = buildRecorderInfo("1");
    RecorderDetail recorderDetail = new RecorderDetail(RecorderIdentifier.from(recorderInfoBuilder.build()), 1);
    Assert.assertEquals(true, recorderDetail.isDefunct());
    Assert.assertEquals(false, recorderDetail.canAcceptWork());

    //send poll with default work response
    boolean updated = recorderDetail.receivePoll(Recorder.PollReq.newBuilder()
        .setRecorderInfo(recorderInfoBuilder.setRecorderTick(1).build())
        .setWorkLastIssued(Recorder.WorkResponse.newBuilder()
            .setElapsedTime(100)
            .setWorkId(0)
            .setWorkResult(Recorder.WorkResponse.WorkResult.success)
            .setWorkState(Recorder.WorkResponse.WorkState.complete).build())
        .build());
    Assert.assertTrue(updated);
    Assert.assertEquals(false, recorderDetail.isDefunct());
    Assert.assertEquals(true, recorderDetail.canAcceptWork());

    //wait for recorder to go defunct
    Thread.sleep(1000);
    Assert.assertEquals(true, recorderDetail.isDefunct());
    Assert.assertEquals(false, recorderDetail.canAcceptWork());

    //send poll with running work
    updated = recorderDetail.receivePoll(Recorder.PollReq.newBuilder()
        .setRecorderInfo(recorderInfoBuilder.setRecorderTick(4).build())
        .setWorkLastIssued(Recorder.WorkResponse.newBuilder()
            .setElapsedTime(100)
            .setWorkId(1)
            .setWorkResult(Recorder.WorkResponse.WorkResult.unknown)
            .setWorkState(Recorder.WorkResponse.WorkState.running).build())
        .build());
    Assert.assertTrue(updated);
    Assert.assertEquals(false, recorderDetail.isDefunct());
    Assert.assertEquals(false, recorderDetail.canAcceptWork());

    //send poll with lower tick than before
    updated = recorderDetail.receivePoll(Recorder.PollReq.newBuilder()
        .setRecorderInfo(recorderInfoBuilder.setRecorderTick(3).build())
        .setWorkLastIssued(Recorder.WorkResponse.newBuilder()
            .setElapsedTime(100)
            .setWorkId(1)
            .setWorkResult(Recorder.WorkResponse.WorkResult.unknown)
            .setWorkState(Recorder.WorkResponse.WorkState.running).build())
        .build());
    Assert.assertFalse(updated);

    //send poll with completed work
    //send poll with lower tick than before
    updated = recorderDetail.receivePoll(Recorder.PollReq.newBuilder()
        .setRecorderInfo(recorderInfoBuilder.setRecorderTick(5).build())
        .setWorkLastIssued(Recorder.WorkResponse.newBuilder()
            .setElapsedTime(100)
            .setWorkId(1)
            .setWorkResult(Recorder.WorkResponse.WorkResult.success)
            .setWorkState(Recorder.WorkResponse.WorkState.complete).build())
        .build());
    Assert.assertTrue(updated);
    Assert.assertEquals(false, recorderDetail.isDefunct());
    Assert.assertEquals(true, recorderDetail.canAcceptWork());
  }

  @Test
  public void testEquality() {
    Recorder.RecorderInfo.Builder recorderInfoBuilder = buildRecorderInfo("1");
    RecorderDetail recorderDetail1 = new RecorderDetail(RecorderIdentifier.from(recorderInfoBuilder.build()), 1);
    RecorderDetail recorderDetail2 = new RecorderDetail(RecorderIdentifier.from(recorderInfoBuilder.build()), 1);
    Assert.assertEquals(recorderDetail1, recorderDetail2);
  }

  private Recorder.RecorderInfo.Builder buildRecorderInfo(String ip) {
    return Recorder.RecorderInfo.newBuilder()
        .setAppId("1")
        .setCluster("1")
        .setHostname("1")
        .setInstanceGrp("1")
        .setInstanceId("1")
        .setInstanceType("1")
        .setLocalTime(LocalDateTime.now(Clock.systemUTC()).toString())
        .setProcName("1")
        .setRecorderTick(0)
        .setRecorderUptime(100)
        .setRecorderVersion(1)
        .setVmId("1")
        .setZone("1")
        .setIp(ip);
  }
}
