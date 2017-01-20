package fk.prof;

import com.google.protobuf.CodedInputStream;
import fk.prof.nodep.SleepForever;
import fk.prof.utils.AgentRunner;
import fk.prof.utils.TestBackendServer;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableObject;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import recording.Recorder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.zip.Adler32;

import static fk.prof.utils.Matchers.approximatelyBetween;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class WorkHandlingTest {
    public static final String CPU_SAMPLING_WORK_DESCRIPTION = "ok, let us capture some on-cpu stack-samples, shall we?";
    public static final int CPU_SAMPLING_FREQ = 100;
    public static final int CPU_SAMPLING_MAX_FRAMES = 50;
    public static final int CONTROLLER_ID = 2;
    public static final int CPU_SAMPLING_WORK_ID = 42;
    private TestBackendServer server;
    private Function<byte[], byte[]>[] association = new Function[2];
    private Function<byte[], byte[]>[] poll = new Function[18];
    private Function<byte[], byte[]>[] profile = new Function[2];
    private Future[] assocAction;
    private Future[] pollAction;
    private Future[] profileAction;
    private AgentRunner runner;
    private TestBackendServer associateServer;

    @Before
    public void setUp() {
        server = new TestBackendServer(8080);
        associateServer = new TestBackendServer(8090);
        assocAction = server.register("/association", association);
        pollAction = associateServer.register("/poll", poll);
        profileAction = associateServer.register("/profile", profile);
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
        MutableBoolean profileCalled = new MutableBoolean(false);
        association[0] = pointToAssociate(recInfo, 8090);
        profile[0] = (req) -> {
            profileCalled.setTrue();
            return null;
        };
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
        assertThat(profileCalled.getValue(), is(false));
    }

    @Test
    public void should_Track_And_Retire_CpuProfileWork() throws ExecutionException, InterruptedException, IOException, TimeoutException {
        MutableObject<Recorder.RecorderInfo> recInfo = new MutableObject<>();
        MutableBoolean profileCalledSecondTime = new MutableBoolean(false);
        association[0] = pointToAssociate(recInfo, 8090);
        PollReqWithTime pollReqs[] = new PollReqWithTime[poll.length];
        poll[0] = tellRecorderWeHaveNoWork(pollReqs, 0);
        String cpuSamplingWorkIssueTime = ISODateTimeFormat.dateTime().print(DateTime.now());
        poll[1] = issueCpuProfilingWork(pollReqs, 1, 10, 2, cpuSamplingWorkIssueTime, CPU_SAMPLING_WORK_ID);
        for (int i = 2; i < poll.length; i++) {
            poll[i] = tellRecorderWeHaveNoWork(pollReqs, i);
        }
        MutableObject<Recorder.RecordingHeader> hdr = new MutableObject<>();
        List<Recorder.Wse> profileEntries = new ArrayList<>();

        profile[0] = (req) -> {
            recordProfile(req, hdr, profileEntries);
            return new byte[0];
        };
        profile[1] = (req) -> {
            profileCalledSecondTime.setTrue();
            return null;
        };

        runner.start();

        assocAction[0].get(4, TimeUnit.SECONDS);
        long prevTime = System.currentTimeMillis();

        assertThat(assocAction[0].isDone(), is(true));
        pollAction[poll.length - 1].get(poll.length + 4, TimeUnit.SECONDS); //some grace time

        long idx = 0;
        for (PollReqWithTime prwt : pollReqs) {
            assertRecorderInfoAllGood(prwt.req.getRecorderInfo());
            if (idx > 0) {
                assertThat("idx = " + idx, prwt.time - prevTime, approximatelyBetween(1000l, 2000l)); //~1 sec tolerance
            }
            prevTime = prwt.time;
            idx++;
        }

        assertWorkStateAndResultIs(pollReqs[0].req.getWorkLastIssued(), 0, Recorder.WorkResponse.WorkState.complete, Recorder.WorkResponse.WorkResult.success, 0);
        assertWorkStateAndResultIs(pollReqs[1].req.getWorkLastIssued(), 100, Recorder.WorkResponse.WorkState.complete, Recorder.WorkResponse.WorkResult.success, 0);
        for (int i = 2; i < 4; i++) {
            assertWorkStateAndResultIs("i = " + i, pollReqs[i].req.getWorkLastIssued(), CPU_SAMPLING_WORK_ID, Recorder.WorkResponse.WorkState.pre_start, Recorder.WorkResponse.WorkResult.unknown, 0);
        }
        for (int i = 4; i < 14; i++) {
            assertWorkStateAndResultIs("i = " + i, pollReqs[i].req.getWorkLastIssued(), CPU_SAMPLING_WORK_ID, Recorder.WorkResponse.WorkState.running, Recorder.WorkResponse.WorkResult.unknown, i - 4);
        }
        assertWorkStateAndResultIs(pollReqs[14].req.getWorkLastIssued(), CPU_SAMPLING_WORK_ID, Recorder.WorkResponse.WorkState.complete, Recorder.WorkResponse.WorkResult.success, 10);
        for (int i = 15; i < pollReqs.length; i++) {
            assertWorkStateAndResultIs(pollReqs[i].req.getWorkLastIssued(), i + 99, Recorder.WorkResponse.WorkState.complete, Recorder.WorkResponse.WorkResult.success, 0);
        }

        Recorder.Work w = Recorder.Work.newBuilder()
                .setWType(Recorder.WorkType.cpu_sample_work)
                .setCpuSample(Recorder.CpuSampleWork.newBuilder()
                        .setMaxFrames(CPU_SAMPLING_MAX_FRAMES)
                        .setFrequency(CPU_SAMPLING_FREQ)
                        .build())
                .build();
        assertRecordingHeaderIsGood(hdr.getValue(), CONTROLLER_ID, CPU_SAMPLING_WORK_ID, cpuSamplingWorkIssueTime, 10, 2, 1, new Recorder.Work[] {w});
        
        assertThat(profileCalledSecondTime.getValue(), is(false));
    }

    @Test
    public void should_Abort_CpuProfileWork_When_Profile_Upload_Fails() throws ExecutionException, InterruptedException, IOException, TimeoutException {
        MutableObject<Recorder.RecorderInfo> recInfo = new MutableObject<>();
        boolean[] profileCalled = {false, false};
        association[0] = pointToAssociate(recInfo, 8090);
        PollReqWithTime pollReqs[] = new PollReqWithTime[poll.length];
        poll[0] = tellRecorderWeHaveNoWork(pollReqs, 0);
        String cpuSamplingWorkIssueTime = ISODateTimeFormat.dateTime().print(DateTime.now());
        poll[1] = issueCpuProfilingWork(pollReqs, 1, 10, 2, cpuSamplingWorkIssueTime, CPU_SAMPLING_WORK_ID);
        for (int i = 2; i < poll.length; i++) {
            poll[i] = tellRecorderWeHaveNoWork(pollReqs, i);
        }
        MutableObject<Recorder.RecordingHeader> hdr = new MutableObject<>();

        profile[0] = (req) -> {
            profileCalled[0] = true;
            throw new RuntimeException("Ouch! something went wrong.");
        };
        profile[1] = (req) -> {
            profileCalled[1] = true;
            return null;
        };

        runner.start();

        assocAction[0].get(4, TimeUnit.SECONDS);
        long prevTime = System.currentTimeMillis();

        assertThat(assocAction[0].isDone(), is(true));
        pollAction[poll.length - 1].get(poll.length + 4, TimeUnit.SECONDS); //some grace time

        long idx = 0;
        for (PollReqWithTime prwt : pollReqs) {
            assertRecorderInfoAllGood(prwt.req.getRecorderInfo());
            if (idx > 0) {
                assertThat("idx = " + idx, prwt.time - prevTime, approximatelyBetween(1000l, 2000l)); //~1 sec tolerance
            }
            prevTime = prwt.time;
            idx++;
        }

        assertWorkStateAndResultIs(pollReqs[0].req.getWorkLastIssued(), 0, Recorder.WorkResponse.WorkState.complete, Recorder.WorkResponse.WorkResult.success, 0);
        assertWorkStateAndResultIs(pollReqs[1].req.getWorkLastIssued(), 100, Recorder.WorkResponse.WorkState.complete, Recorder.WorkResponse.WorkResult.success, 0);
        for (int i = 2; i < 4; i++) {
            assertWorkStateAndResultIs("i = " + i, pollReqs[i].req.getWorkLastIssued(), CPU_SAMPLING_WORK_ID, Recorder.WorkResponse.WorkState.pre_start, Recorder.WorkResponse.WorkResult.unknown, 0);
        }
        assertWorkStateAndResultIs(pollReqs[4].req.getWorkLastIssued(), CPU_SAMPLING_WORK_ID, Recorder.WorkResponse.WorkState.complete, Recorder.WorkResponse.WorkResult.failure, 0);
        for (int i = 5; i < pollReqs.length; i++) {
            assertWorkStateAndResultIs(pollReqs[i].req.getWorkLastIssued(), i + 99, Recorder.WorkResponse.WorkState.complete, Recorder.WorkResponse.WorkResult.success, 0);
        }

        assertThat(Arrays.asList(profileCalled), is(Arrays.asList(true, false)));
    }
    
    private void assertRecordingHeaderIsGood(Recorder.RecordingHeader rh, final int controllerId, final long workId, String cpuSamplingWorkIssueTime, final int duration, final int delay, final int workCount, final Recorder.Work[] expectedWork) {
        assertThat(rh, notNullValue());
        assertThat(rh.getRecorderVersion(), is(Versions.RECORDER_VERSION));
        assertThat(rh.getControllerVersion(), is(Versions.CONTROLLER_VERSION));
        assertThat(rh.getControllerId(), is(controllerId));
        Recorder.WorkAssignment wa = rh.getWorkAssignment();
        assertThat(wa.getWorkId(), is(workId));
        assertThat(wa.getIssueTime(), is(cpuSamplingWorkIssueTime));
        assertThat(wa.getDescription(), is(CPU_SAMPLING_WORK_DESCRIPTION));
        assertThat(wa.getDuration(), is(duration));
        assertThat(wa.getDelay(), is(delay));
        assertThat(wa.getWorkCount(), is(expectedWork.length));
        for (int i = 0; i < expectedWork.length; i++) {
            Recorder.Work w = wa.getWork(i);
            assertThat(w, is(expectedWork[i]));
        }
    }

    private void recordProfile(byte[] req, MutableObject<Recorder.RecordingHeader> hdr, List<Recorder.Wse> entries) {
        try {
            recordProfile_(req, hdr, entries);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void recordProfile_(byte[] req, MutableObject<Recorder.RecordingHeader> hdr, List<Recorder.Wse> entries) throws IOException {
        CodedInputStream is = CodedInputStream.newInstance(req);
        assertThat(is.readUInt32(), is(Versions.RECORDER_ENCODING_VERSION));

        int headerLen = is.readUInt32();

        int hdrLimit = is.pushLimit(headerLen);
        Recorder.RecordingHeader.Builder rhBuilder = Recorder.RecordingHeader.newBuilder();
        rhBuilder.mergeFrom(is);
        is.popLimit(hdrLimit);

        Recorder.RecordingHeader rh = rhBuilder.build();
        hdr.setValue(rh);
        //// Hdr len and chksum
        int bytesBeforeChksum = is.getTotalBytesRead();
        int chksum = is.readUInt32();
        int bytesAfterChksum = is.getTotalBytesRead();
        ///////////////////////

        Adler32 csum = new Adler32();
        csum.reset();
        csum.update(req, 0, bytesBeforeChksum);
        assertThat((int) csum.getValue(), is(chksum));

        while (bytesAfterChksum < req.length) {
            int wseLen = is.readUInt32();
            int wseLim = is.pushLimit(wseLen);
            Recorder.Wse.Builder wseBuilder = Recorder.Wse.newBuilder();
            wseBuilder.mergeFrom(is);
            is.popLimit(wseLim);
            entries.add(wseBuilder.build());

            //// len and chksum
            bytesBeforeChksum = is.getTotalBytesRead() - bytesAfterChksum;
            csum.reset();
            csum.update(req, bytesAfterChksum, bytesBeforeChksum);
            int wseChksum = is.readUInt32();
            assertThat(wseChksum, is(csum.getValue()));
            bytesAfterChksum = is.getTotalBytesRead();
            ///////////////////////
        }
    }

    private void assertItHadNoWork(Recorder.WorkResponse workLastIssued, long expectedId) {
        assertWorkStateAndResultIs(workLastIssued, expectedId, Recorder.WorkResponse.WorkState.complete, Recorder.WorkResponse.WorkResult.success, 0);
    }

    private void assertWorkStateAndResultIs(String ctx, Recorder.WorkResponse workLastIssued, long expectedId, final Recorder.WorkResponse.WorkState state, final Recorder.WorkResponse.WorkResult result, final int elapsedTime) {
        assertThat(ctx, workLastIssued.getWorkId(), is(expectedId));
        assertThat(ctx, workLastIssued.getWorkResult(), is(result));
        assertThat(ctx, workLastIssued.getWorkState(), is(state));
        assertThat(ctx, workLastIssued.getElapsedTime(), is(elapsedTime));
    }

    private void assertWorkStateAndResultIs(Recorder.WorkResponse workLastIssued, long expectedId, final Recorder.WorkResponse.WorkState state, final Recorder.WorkResponse.WorkResult result, final int elapsedTime) {
        assertWorkStateAndResultIs("UNKNOWN", workLastIssued, expectedId, state, result, elapsedTime);
    }

    private Function<byte[], byte[]> issueCpuProfilingWork(PollReqWithTime[] pollReqs, int idx, final int duration, final int delay, final String issueTime, final int workId) {
        return cookPollResponse(pollReqs, idx, (nowString, builder) -> {
            Recorder.WorkAssignment.Builder workAssignmentBuilder = prepareWorkAssignment(nowString, builder, idx, delay, duration, CPU_SAMPLING_WORK_DESCRIPTION, workId);
            Recorder.Work.Builder workBuilder = workAssignmentBuilder.addWorkBuilder();
            workBuilder.setWType(Recorder.WorkType.cpu_sample_work).getCpuSampleBuilder()
                    .setFrequency(CPU_SAMPLING_FREQ)
                    .setMaxFrames(CPU_SAMPLING_MAX_FRAMES);
        }, issueTime);
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
        return cookPollResponse(pollReqs, idx, (nowString, builder) -> {
        }, ISODateTimeFormat.dateTime().print(DateTime.now()));
    }

    private Function<byte[], byte[]> tellRecorderWeHaveNoWork(PollReqWithTime[] pollReqs, int idx) {
        return tellRecorderWeHaveNoWork(pollReqs, idx, 0, 0);
    }

    private Function<byte[], byte[]> tellRecorderWeHaveNoWork(PollReqWithTime[] pollReqs, int idx, int duration, final int delay) {
        return cookPollResponse(pollReqs, idx, (nowString, builder) -> {
            prepareWorkAssignment(nowString, builder, idx, delay, duration, "no work for ya!", idx + 100);
        }, ISODateTimeFormat.dateTime().print(DateTime.now()));
    }

    private Function<byte[], byte[]> cookPollResponse(PollReqWithTime[] pollReqs, int idx, final BiConsumer<String, Recorder.PollRes.Builder> biConsumer, final String nowString) {
        return (req) -> {
            try {
                Recorder.PollReq.Builder pollReqBuilder = Recorder.PollReq.newBuilder();
                pollReqs[idx] = new PollReqWithTime(pollReqBuilder.mergeFrom(req).build());

                Recorder.PollRes.Builder builder = Recorder.PollRes.newBuilder()
                        .setLocalTime(nowString)
                        .setControllerId(CONTROLLER_ID)
                        .setControllerVersion(Versions.CONTROLLER_VERSION);
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
