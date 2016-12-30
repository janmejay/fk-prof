package fk.prof;

import fk.prof.nodep.SleepForever;
import fk.prof.utils.AgentRunner;
import fk.prof.utils.TestBackendServer;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import recording.Recorder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertThat;

public class AssociationTest {

    private TestBackendServer server;
    private Function<byte[], byte[]>[] association = new Function[1];
    private Function<byte[], byte[]>[] poll = new Function[2];
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
                "ityp=c0.small"
        );
    }

    @After
    public void tearDown() {
        server.stop();
        associateServer.stop();
    }

    @Test
    public void should_DiscoverAssociate_and_SayHelloToIt() throws ExecutionException, InterruptedException, IOException {
        MutableObject<Recorder.RecorderInfo> recInfo = new MutableObject<>();
        association[0] = (req) -> {
            try {
                Recorder.RecorderInfo.Builder recInfoBuilder = Recorder.RecorderInfo.newBuilder();
                recInfo.setValue(recInfoBuilder.mergeFrom(req).build());
                Recorder.AssignedBackend assignedBackend = Recorder.AssignedBackend.newBuilder()
                        .setHost("127.0.0.15")
                        .setPort(8090)
                        .build();

                ByteArrayOutputStream os = new ByteArrayOutputStream();
                assignedBackend.writeTo(os);
                return os.toByteArray();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        MutableObject<Recorder.PollReq> pollReq = new MutableObject<>();
        poll[0] = (req) -> {
            try {
                Recorder.PollReq.Builder pollReqBuilder = Recorder.PollReq.newBuilder();
                pollReq.setValue(pollReqBuilder.mergeFrom(req).build());

                LocalDateTime now = LocalDateTime.now();
                String nowString = DateTimeFormatter.ISO_INSTANT.format(now);
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
                throw new RuntimeException(e);
            }
        };
        MutableBoolean pollCalledTwice = new MutableBoolean(false);
        poll[1] = (req) -> {
            pollCalledTwice.setValue(true);
            return new byte[0];
        };

        //start process here
        runner.start();
        
        assocAction[0].get();

        assertThat(assocAction[0].isDone(), is(true));
        assertRecorderInfoAllGood(recInfo.getValue());

        pollAction[0].get();

        Recorder.PollReq pollRequest = pollReq.getValue();
        assertRecorderInfoAllGood(pollRequest.getRecorderInfo());
        Recorder.WorkResponse workLastIssued = pollReq.getValue().getWorkLastIssued();
        assertThat(workLastIssued.getWorkId(), is(-1));
        assertThat(workLastIssued.getWorkState(), is(Recorder.WorkResponse.WorkState.complete));
        assertThat(workLastIssued.getWorkResult(), is(Recorder.WorkResponse.WorkResult.success));
        assertThat(workLastIssued.getElapsedTime(), is(0));
        
        //stop process here
        runner.stop();
        
        assertThat(pollCalledTwice.getValue(), is(false));
    }

    private void assertRecorderInfoAllGood(Recorder.RecorderInfo recorderInfo) {
        assertThat(recorderInfo.getIp(), is("10.20.30.40"));
        assertThat(recorderInfo.getHostname(), is("foo-host"));
        assertThat(recorderInfo.getAppId(), is("bar-app"));
        assertThat(recorderInfo.getInstanceGrp(), is("baz-grp"));
        assertThat(recorderInfo.getCluster(), is("quux-cluster"));
        assertThat(recorderInfo.getProcName(), is("corge-proc"));
        assertThat(recorderInfo.getVmId(), is("grault-vmid"));
        assertThat(recorderInfo.getZone(), is("garply-zone"));
        assertThat(recorderInfo.getInstanceType(), is("c0.small"));
        TemporalAccessor localTime = DateTimeFormatter.ISO_INSTANT.parse(recorderInfo.getLocalTime());
        LocalDateTime recorderTime = LocalDateTime.from(localTime);
        LocalDateTime now = LocalDateTime.now();
        assertThat(recorderTime, allOf(greaterThan(now.minusMinutes(1)), lessThan(now.plusMinutes(1))));
        assertThat(recorderInfo.getRecorderVersion(), is(1));
        assertThat(recorderInfo.getRecorderUptime(), allOf(greaterThan(0), lessThan(60)));
    }
}
