package fk.prof;

import fk.prof.nodep.SleepForever;
import fk.prof.utils.AgentRunner;
import fk.prof.utils.TestBackendServer;
import org.apache.commons.lang3.mutable.MutableObject;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import recording.Recorder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class ProfilingTest {
    private TestBackendServer server;
    private Function<byte[], byte[]>[] association = new Function[2];
    private Function<byte[], byte[]>[] poll = new Function[16];
    private Future[] assocAction;
    private Future[] pollAction;
    private AgentRunner runner;
    private TestBackendServer associateServer;

    @Before
    public void setUp() {
        server = new TestBackendServer(8080);
        associateServer = new TestBackendServer(8090);
        assocAction = server.register("/association", association);
        pollAction = associateServer.register("/poll", poll);
        runner = new AgentRunner(SleepForever.class.getCanonicalName(), "service_endpoint=http://127.0.0.1:8080," +
                "ip=10.20.30.40," +
                "host=foo-host," +
                "appid=bar-app," +
                "igrp=baz-grp," +
                "cluster=quux-cluster," +
                "instid=corge-iid," +
                "proc=grault-proc," +
                "vmid=garply-vmid," +
                "zone=waldo-zone," +
                "ityp=c0.small," +
                "backoffStart=2," +
                "backoffMax=5," +
                "pollItvl=1," +
                "logLvl=trace"
        );
    }

    @After
    public void tearDown() {
        runner.stop();
        server.stop();
        associateServer.stop();
    }

    private static class PollReqWithTime {
        public PollReqWithTime(Recorder.PollReq req) {
            this.req = req;
            time = System.currentTimeMillis();
        }

        final Recorder.PollReq req;
        final long time;
    }

    @Test
    public void should_TrackAssignedWork() throws ExecutionException, InterruptedException, IOException, TimeoutException {
        MutableObject<Recorder.RecorderInfo> recInfo = new MutableObject<>();
        association[0] = pointToAssociate(recInfo, 8090);
        PollReqWithTime pollReqs[] = new PollReqWithTime[poll.length];
        for (int i = 0; i < poll.length; i++) {
            poll[i] = tellRecorderWeHaveNoWork(pollReqs, i);
        }

        //start process here
        runner.start();

        assocAction[0].get(4, TimeUnit.SECONDS);
        long prevTime = System.currentTimeMillis();

        assertThat(assocAction[0].isDone(), is(true));
        pollAction[poll.length - 1].get(poll.length + 4, TimeUnit.SECONDS); //some grace time

        long idx = 0;
        for (PollReqWithTime prwt : pollReqs) {
            assertRecorderInfoAllGood(prwt.req.getRecorderInfo());
            assertItHadNoWork(prwt.req.getWorkLastIssued(), idx == 0 ? idx : idx + 99);
            assertThat(prwt.time - prevTime, allOf(greaterThan(1000l), lessThan(2000l))); //~1 sec tolerance
            prevTime = prwt.time;
            idx++;
        }
    }

    @Test
    @Ignore
    public void should_Track_And_Retire_CpuProfileWork() throws ExecutionException, InterruptedException, IOException, TimeoutException {
        MutableObject<Recorder.RecorderInfo> recInfo = new MutableObject<>();
        association[0] = pointToAssociate(recInfo, 8090);
        Recorder.PollReq pollReqs[] = new Recorder.PollReq[]{null, null};
        poll[0] = issueCpuProfilingWork(pollReqs, 0);

        poll[0] = issueCpuProfilingWork(pollReqs, 1);

        //start process here
        runner.start();

        assocAction[0].get(4, TimeUnit.SECONDS);

        assertThat(assocAction[0].isDone(), is(true));
        pollAction[0].get(4, TimeUnit.SECONDS);
        assertRecorderInfoAllGood(pollReqs[0].getRecorderInfo());
        assertItHadNoWork(pollReqs[0].getWorkLastIssued(), 10l);
    }

    private void assertItHadNoWork(Recorder.WorkResponse workLastIssued, long expectedId) {
        assertThat(workLastIssued.getWorkId(), is(expectedId));
        assertThat(workLastIssued.getWorkState(), is(Recorder.WorkResponse.WorkState.complete));
        assertThat(workLastIssued.getWorkResult(), is(Recorder.WorkResponse.WorkResult.success));
        assertThat(workLastIssued.getElapsedTime(), is(0));
    }

    private Function<byte[], byte[]> issueCpuProfilingWork(Recorder.PollReq[] pollReqs, int idx) {
        return (req) -> {
            try {
                Recorder.PollReq.Builder pollReqBuilder = Recorder.PollReq.newBuilder();
                pollReqs[idx] = pollReqBuilder.mergeFrom(req).build();
                DateTime now = DateTime.now();
                String nowString = ISODateTimeFormat.dateTime().print(now);
                Recorder.PollRes.Builder builder = Recorder.PollRes.newBuilder()
                        .setLocalTime(nowString)
                        .setWorkDescription("no work for ya!")
                        .setControllerId(2)
                        .setControllerVersion(1);
                builder.getAssignmentBuilder()
                        .setWorkId(10)
                        .setDelay(0)
                        .setDuration(0)
                        .setIssueTime(nowString);
                Recorder.PollRes pollRes = builder.build();
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                pollRes.writeTo(os);
                return os.toByteArray();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        };
    }

    private Function<byte[], byte[]> tellRecorderWeHaveNoWork(PollReqWithTime[] pollReqs, int idx) {
        return (req) -> {
            try {
                Recorder.PollReq.Builder pollReqBuilder = Recorder.PollReq.newBuilder();
                pollReqs[idx] = new PollReqWithTime(pollReqBuilder.mergeFrom(req).build());

                DateTime now = DateTime.now();
                String nowString = ISODateTimeFormat.dateTime().print(now);
                Recorder.PollRes.Builder builder = Recorder.PollRes.newBuilder()
                        .setLocalTime(nowString)
                        .setWorkDescription("no work for ya!")
                        .setControllerId(2)
                        .setControllerVersion(1);
                builder.getAssignmentBuilder()
                        .setWorkId(idx + 100)
                        .setDelay(0)
                        .setDuration(0)
                        .setIssueTime(nowString);
                Recorder.PollRes pollRes = builder.build();
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                pollRes.writeTo(os);
                return os.toByteArray();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        };
    }

    private Function<byte[], byte[]> pointToAssociate(MutableObject<Recorder.RecorderInfo> recInfo, final int associatePort) {
        return (req) -> {
            try {
                Recorder.RecorderInfo.Builder recInfoBuilder = Recorder.RecorderInfo.newBuilder();
                recInfo.setValue(recInfoBuilder.mergeFrom(req).build());
                Recorder.AssignedBackend assignedBackend = Recorder.AssignedBackend.newBuilder()
                        .setHost("127.0.0.15")
                        .setPort(associatePort)
                        .build();

                ByteArrayOutputStream os = new ByteArrayOutputStream();
                assignedBackend.writeTo(os);
                return os.toByteArray();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        };
    }

    private void assertRecorderInfoAllGood(Recorder.RecorderInfo recorderInfo) {
        assertThat(recorderInfo.getIp(), is("10.20.30.40"));
        assertThat(recorderInfo.getHostname(), is("foo-host"));
        assertThat(recorderInfo.getAppId(), is("bar-app"));
        assertThat(recorderInfo.getInstanceGrp(), is("baz-grp"));
        assertThat(recorderInfo.getCluster(), is("quux-cluster"));
        assertThat(recorderInfo.getInstanceId(), is("corge-iid"));
        assertThat(recorderInfo.getProcName(), is("grault-proc"));
        assertThat(recorderInfo.getVmId(), is("garply-vmid"));
        assertThat(recorderInfo.getZone(), is("waldo-zone"));
        assertThat(recorderInfo.getInstanceType(), is("c0.small"));
        DateTime dateTime = ISODateTimeFormat.dateTimeParser().parseDateTime(recorderInfo.getLocalTime());
        DateTime now = DateTime.now();
        assertThat(dateTime, allOf(greaterThan(now.minusMinutes(1)), lessThan(now.plusMinutes(1))));
        assertThat(recorderInfo.getRecorderVersion(), is(1));
        assertThat(recorderInfo.getRecorderUptime(), allOf(greaterThanOrEqualTo(0), lessThan(60)));
    }
}
