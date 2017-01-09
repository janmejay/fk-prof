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
import java.util.function.BiConsumer;
import java.util.function.Function;

import static fk.prof.utils.Matchers.approximatelyBetween;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class ProfilingTest {
    private TestBackendServer server;
    private Function<byte[], byte[]>[] association = new Function[2];
    private Function<byte[], byte[]>[] poll = new Function[18];
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

        runner.start();

        assocAction[0].get(4, TimeUnit.SECONDS);
        long prevTime = System.currentTimeMillis();

        assertThat(assocAction[0].isDone(), is(true));
        pollAction[poll.length - 1].get(poll.length + 4, TimeUnit.SECONDS); //some grace time

        long idx = 0;
        for (PollReqWithTime prwt : pollReqs) {
            assertRecorderInfoAllGood(prwt.req.getRecorderInfo());
            assertItHadNoWork(prwt.req.getWorkLastIssued(), idx == 0 ? idx : idx + 99);
            if (idx > 0) {
                assertThat("idx = " + idx, prwt.time - prevTime, approximatelyBetween(1000l, 2000l)); //~1 sec tolerance
            }
            prevTime = prwt.time;
            idx++;
        }
    }

    @Test
    public void should_NotAcceptNewWork_WhenSomeWorkInAlreadyUnderway() throws ExecutionException, InterruptedException, IOException, TimeoutException {
        MutableObject<Recorder.RecorderInfo> recInfo = new MutableObject<>();
        association[0] = pointToAssociate(recInfo, 8090);
        PollReqWithTime pollReqs[] = new PollReqWithTime[poll.length];
        poll[0] = tellRecorderWeHaveNoWork(pollReqs, 0);
        poll[1] = tellRecorderWeHaveNoWork(pollReqs, 1, 10, 2);
        for (int i = 2; i < poll.length; i++) {
            poll[i] = tellRecorderWeHaveNoWork(pollReqs, i);
        }

        runner.start();

        assocAction[0].get(4, TimeUnit.SECONDS);
        long prevTime = System.currentTimeMillis();

        assertThat(assocAction[0].isDone(), is(true));
        pollAction[poll.length - 1].get(poll.length + 4, TimeUnit.SECONDS); //some grace time

        long idx = 0;
        for (PollReqWithTime prwt : pollReqs) {
            assertRecorderInfoAllGood(prwt.req.getRecorderInfo());
            if (idx > 0) {
                assertThat(prwt.time - prevTime, approximatelyBetween(1000l, 2000l)); //~1 sec tolerance
            }
            prevTime = prwt.time;
            idx++;
        }

        assertWorkStateAndResultIs(pollReqs[0].req.getWorkLastIssued(), 0, Recorder.WorkResponse.WorkState.complete, Recorder.WorkResponse.WorkResult.success, 0);
        assertWorkStateAndResultIs(pollReqs[1].req.getWorkLastIssued(), 100, Recorder.WorkResponse.WorkState.complete, Recorder.WorkResponse.WorkResult.success, 0);
        for (int i = 2; i < 4; i++) {
            assertWorkStateAndResultIs("i = " + i, pollReqs[i].req.getWorkLastIssued(), 101, Recorder.WorkResponse.WorkState.pre_start, Recorder.WorkResponse.WorkResult.unknown, 0);
        }
        for (int i = 4; i < 14; i++) {
            assertWorkStateAndResultIs("i = " + i, pollReqs[i].req.getWorkLastIssued(), 101, Recorder.WorkResponse.WorkState.running, Recorder.WorkResponse.WorkResult.unknown, i - 4);
        }
        assertWorkStateAndResultIs(pollReqs[14].req.getWorkLastIssued(), 101, Recorder.WorkResponse.WorkState.complete, Recorder.WorkResponse.WorkResult.success, 10);
        for (int i = 15; i < pollReqs.length; i++) {
            assertWorkStateAndResultIs(pollReqs[i].req.getWorkLastIssued(), i + 99, Recorder.WorkResponse.WorkState.complete, Recorder.WorkResponse.WorkResult.success, 0);
        }
    }

    @Test
    @Ignore
    public void should_Track_And_Retire_CpuProfileWork() throws ExecutionException, InterruptedException, IOException, TimeoutException {
        MutableObject<Recorder.RecorderInfo> recInfo = new MutableObject<>();
        association[0] = pointToAssociate(recInfo, 8090);
        PollReqWithTime pollReqs[] = new PollReqWithTime[poll.length];
        poll[0] = issueCpuProfilingWork(pollReqs, 0, 2, 10);
        for (int i = 1; i < poll.length; i++) {
            poll[i] = tellRecorderToContinueWhatItWasDoing(pollReqs, i);
        }

        runner.start();

        assocAction[0].get(4, TimeUnit.SECONDS);
        long prevTime = System.currentTimeMillis();

        assertThat(assocAction[0].isDone(), is(true));
        pollAction[poll.length - 1].get(poll.length + 4, TimeUnit.SECONDS); //some grace time

        long idx = 0;
        for (PollReqWithTime prwt : pollReqs) {
            assertRecorderInfoAllGood(prwt.req.getRecorderInfo());
            assertThat(prwt.time - prevTime, approximatelyBetween(1000l, 2000l)); //~1 sec tolerance
            prevTime = prwt.time;
            idx++;
        }

        assertWorkStateAndResultIs(pollReqs[0].req.getWorkLastIssued(), 0, Recorder.WorkResponse.WorkState.complete, Recorder.WorkResponse.WorkResult.success, 0);
        assertWorkStateAndResultIs(pollReqs[1].req.getWorkLastIssued(), 42, Recorder.WorkResponse.WorkState.pre_start, Recorder.WorkResponse.WorkResult.unknown, 0);
        assertWorkStateAndResultIs(pollReqs[2].req.getWorkLastIssued(), 42, Recorder.WorkResponse.WorkState.pre_start, Recorder.WorkResponse.WorkResult.unknown, 0);
        for (int i = 0; i < 10; i++) {
            assertWorkStateAndResultIs(pollReqs[i + 3].req.getWorkLastIssued(), 42, Recorder.WorkResponse.WorkState.running, Recorder.WorkResponse.WorkResult.unknown, i);
        }
        for (int i = 0; i < 2; i++) {
            assertWorkStateAndResultIs(pollReqs[i + 13].req.getWorkLastIssued(), 42, Recorder.WorkResponse.WorkState.complete, Recorder.WorkResponse.WorkResult.success, 10);
        }
    }

    private void assertItHadNoWork(Recorder.WorkResponse workLastIssued, long expectedId) {
        assertWorkStateAndResultIs(workLastIssued, expectedId, Recorder.WorkResponse.WorkState.complete, Recorder.WorkResponse.WorkResult.success, 0);
    }

    private void assertWorkStateAndResultIs(String ctx, Recorder.WorkResponse workLastIssued, long expectedId, final Recorder.WorkResponse.WorkState state, final Recorder.WorkResponse.WorkResult result, final int elapsedTime) {
        assertThat(ctx, workLastIssued.getWorkId(), is(expectedId));
        assertThat(ctx, workLastIssued.getWorkState(), is(state));
        assertThat(ctx, workLastIssued.getWorkResult(), is(result));
        assertThat(ctx, workLastIssued.getElapsedTime(), is(elapsedTime));
    }
    
    private void assertWorkStateAndResultIs(Recorder.WorkResponse workLastIssued, long expectedId, final Recorder.WorkResponse.WorkState state, final Recorder.WorkResponse.WorkResult result, final int elapsedTime) {
            assertWorkStateAndResultIs("UNKNOWN", workLastIssued, expectedId, state, result, elapsedTime);
        }

    private Function<byte[], byte[]> issueCpuProfilingWork(PollReqWithTime[] pollReqs, int idx, final int delay, final int duration) {
        return cookPollResponse(pollReqs, idx, (nowString, builder) -> {
            Recorder.WorkAssignment.Builder workAssignmentBuilder = prepareWorkAssignment(nowString, builder, idx, delay, duration, "ok, let us capture some on-cpu stack-samples, shall we?", 42);
            Recorder.Work.Builder workBuilder = workAssignmentBuilder.addWorkBuilder();
            workBuilder.setWType(Recorder.WorkType.cpu_sample_work).getCpuSampleBuilder()
                    .setFrequency(100)
                    .setMaxFrames(50);
        });
    }

    private Recorder.WorkAssignment.Builder prepareWorkAssignment(String nowString, Recorder.PollRes.Builder builder, int idx, int delay, int duration, final String desc, int workId) {
        return builder.getAssignmentBuilder()
                .setWorkId(workId)
                .setDescription(desc)
                .setDelay(delay)
                .setDuration(duration)
                .setIssueTime(nowString);
    }

    private Function<byte[], byte[]> tellRecorderToContinueWhatItWasDoing(PollReqWithTime[] pollReqs, int idx) {
        return cookPollResponse(pollReqs, idx, (nowString, builder) -> { });
    }

    private Function<byte[], byte[]> tellRecorderWeHaveNoWork(PollReqWithTime[] pollReqs, int idx) {
        return tellRecorderWeHaveNoWork(pollReqs, idx, 0, 0);
    }

    private Function<byte[], byte[]> tellRecorderWeHaveNoWork(PollReqWithTime[] pollReqs, int idx, int duration, final int delay) {
        return cookPollResponse(pollReqs, idx, (nowString, builder) -> {
            prepareWorkAssignment(nowString, builder, idx, delay, duration, "no work for ya!", idx + 100);
        });
    }

    private Function<byte[], byte[]> cookPollResponse(PollReqWithTime[] pollReqs, int idx, final BiConsumer<String, Recorder.PollRes.Builder> biConsumer) {
        return (req) -> {
            try {
                Recorder.PollReq.Builder pollReqBuilder = Recorder.PollReq.newBuilder();
                pollReqs[idx] = new PollReqWithTime(pollReqBuilder.mergeFrom(req).build());

                DateTime now = DateTime.now();
                String nowString = ISODateTimeFormat.dateTime().print(now);
                Recorder.PollRes.Builder builder = Recorder.PollRes.newBuilder()
                        .setLocalTime(nowString)
                        .setControllerId(2)
                        .setControllerVersion(1);
                biConsumer.accept(nowString, builder);
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
