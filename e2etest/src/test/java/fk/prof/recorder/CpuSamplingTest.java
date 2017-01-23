package fk.prof.recorder;

import fk.prof.recorder.cpuburn.Burn20Of100;
import fk.prof.recorder.cpuburn.Burn80Of100;
import fk.prof.recorder.cpuburn.WrapperA;
import fk.prof.recorder.main.Burn20And80PctCpu;
import fk.prof.recorder.utils.AgentRunner;
import fk.prof.recorder.utils.TestBackendServer;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableObject;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openjdk.jmh.infra.Blackhole;
import recording.Recorder;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static fk.prof.recorder.AssociationTest.assertRecorderInfoAllGood;
import static fk.prof.recorder.WorkHandlingTest.*;
import static fk.prof.recorder.utils.Matchers.approximately;
import static fk.prof.recorder.utils.Matchers.approximatelyBetween;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

public class CpuSamplingTest {
    public static final int CPU_SAMPLING_FREQ = 100;
    public static final int CPU_SAMPLING_MAX_FRAMES = 50;
    public static final int CONTROLLER_ID = 2;
    public static final int CPU_SAMPLING_WORK_ID = 42;
    public static final String DUMMY_ROOT_NODE_CLASS_NAME = "ROOT";
    public static final String DUMMY_ROOT_NODE_FN_NAME = "~ root ~";
    public static final String DUMMY_ROOT_NOTE_FN_SIGNATURE = "()V";
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
        runner = new AgentRunner(Burn20And80PctCpu.class.getCanonicalName(), "service_endpoint=http://127.0.0.1:8080," +
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

    @Test
    public void should_TrackAssignedWork() throws ExecutionException, InterruptedException, IOException, TimeoutException {
        MutableObject<Recorder.RecorderInfo> recInfo = new MutableObject<>();
        association[0] = pointToAssociate(recInfo, 8090);
        WorkHandlingTest.PollReqWithTime pollReqs[] = new WorkHandlingTest.PollReqWithTime[poll.length];
        for (int i = 0; i < poll.length; i++) {
            poll[i] = tellRecorderWeHaveNoWork(pollReqs, i);
        }

        runner.start();

        assocAction[0].get(4, TimeUnit.SECONDS);
        long prevTime = System.currentTimeMillis();

        assertThat(assocAction[0].isDone(), is(true));
        pollAction[poll.length - 1].get(poll.length + 4, TimeUnit.SECONDS); //some grace time

        long idx = 0;
        for (WorkHandlingTest.PollReqWithTime prwt : pollReqs) {
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
    public void should_Track_And_Retire_CpuProfileWork() throws ExecutionException, InterruptedException, IOException, TimeoutException {
        MutableObject<Recorder.RecorderInfo> recInfo = new MutableObject<>();
        MutableBoolean profileCalledSecondTime = new MutableBoolean(false);
        association[0] = pointToAssociate(recInfo, 8090);
        WorkHandlingTest.PollReqWithTime pollReqs[] = new WorkHandlingTest.PollReqWithTime[poll.length];
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
        for (WorkHandlingTest.PollReqWithTime prwt : pollReqs) {
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
        assertRecordingHeaderIsGood(hdr.getValue(), CONTROLLER_ID, CPU_SAMPLING_WORK_ID, cpuSamplingWorkIssueTime, 10, 2, 1, new Recorder.Work[]{w});

        assertCpuProfileEntriesAre(profileEntries, Collections.singletonMap("inferno",
                rootMatcher(DUMMY_ROOT_NODE_FN_NAME, DUMMY_ROOT_NOTE_FN_SIGNATURE, 0, 1, childrenMatcher(
                        nodeMatcher(Burn20And80PctCpu.class, "main", "([Ljava/lang/String;)V", 0, 1, childrenMatcher(
                                nodeMatcher(Burn20Of100.class, "burn", "()V", 0, 1, childrenMatcher(
                                        nodeMatcher(Blackhole.class, "consumeCPU", "(J)V", 20, 5, Collections.emptySet()))),
                                nodeMatcher(WrapperA.class, "burnSome", "(S)V", 0, 1, childrenMatcher(
                                        nodeMatcher(Burn80Of100.class, "burn", "()V", 0, 1, childrenMatcher(
                                                nodeMatcher(Blackhole.class, "consumeCPU", "(J)V", 80, 5, Collections.emptySet())))))))))), 
                false);

        assertThat(profileCalledSecondTime.getValue(), is(false));
    }

    private static class TraceInfo {
        final String name;
        final int coverage;

        private TraceInfo(String name, int coverage) {
            this.name = name;
            this.coverage = coverage;
        }
    }

    private static class MthdInfo {
        final String klass;
        final String name;
        final String sig;
        final String fileName;

        private MthdInfo(String klass, String name, String sig, String fileName) {
            this.klass = klass;
            this.name = name;
            this.sig = sig;
            this.fileName = fileName;
        }
    }

    private void assertCpuProfileEntriesAre(List<Recorder.Wse> entries, Map<String, StackNodeMatcher> expected, final boolean ignoreOtherWseTypes) {
        //first let us build the tree
        Map<Integer, TraceInfo> traceInfoMap = new HashMap<>();
        Map<Long, MthdInfo> mthdInfoMap = new HashMap<>();
        Map<Integer, SampledStackNode> aggregations = new HashMap<>();
        int totalSamples = 0;
        for (Recorder.Wse entry : entries) {
            if (!ignoreOtherWseTypes) {
                assertThat(entry.getWType(), is(Recorder.WorkType.cpu_sample_work));
            } else if (entry.getWType() != Recorder.WorkType.cpu_sample_work) {
                continue;
            }
            assertThat(entry.hasCpuSampleEntry(), is(true));
            Recorder.IndexedData idxData = entry.getIndexedData();
            //TODO: add support for thread too
            for (Recorder.TraceContext traceEntry : idxData.getTraceCtxList()) {
                int id = traceEntry.getTraceId();
                assertThat(traceInfoMap.containsKey(id), is(false));
                traceInfoMap.put(id, new TraceInfo(traceEntry.getTraceName(), traceEntry.getCoveragePct()));
                aggregations.put(id, new SampledStackNode(DUMMY_ROOT_NODE_CLASS_NAME, DUMMY_ROOT_NODE_CLASS_NAME, DUMMY_ROOT_NODE_FN_NAME, DUMMY_ROOT_NOTE_FN_SIGNATURE, 0, 0, 0, new HashSet<>()));
            }
            for (Recorder.MethodInfo mthdEntry : idxData.getMethodInfoList()) {
                long id = mthdEntry.getMethodId();
                assertThat(mthdInfoMap.containsKey(id), is(false));
                mthdInfoMap.put(id, new MthdInfo(mthdEntry.getClassFqdn(), mthdEntry.getMethodName(), mthdEntry.getSignature(), mthdEntry.getFileName()));
            }
            Recorder.StackSampleWse e = entry.getCpuSampleEntry();
            for (int i = 0; i < e.getStackSampleCount(); i++) {
                Recorder.StackSample stackSample = e.getStackSample(i);
                SampledStackNode currentNode = aggregations.get(stackSample.getTraceId());
                for (int j = 0; j < stackSample.getFrameCount(); j++) {
                    Recorder.Frame frame = stackSample.getFrame(j);
                    long methodId = frame.getMethodId();
                    int lineNo = frame.getLineNo();
                    int bci = frame.getBci();
                    MthdInfo mthdInfo = mthdInfoMap.get(methodId);
                    currentNode = currentNode.findOrCreateChild(mthdInfo, lineNo, bci);
                }
                currentNode.onCpuSampleCount++;
                totalSamples++;
            }
        }

        //now let us match it
        for (Map.Entry<String, StackNodeMatcher> expectedEntry : expected.entrySet()) {
            SampledStackNode node = aggregations.get(expectedEntry.getKey());
            assertThat(node, notNullValue());
            assertTreesMatch(node, expectedEntry.getValue(), totalSamples);
        }
        assertThat(aggregations.size(), is(expected.size()));
    }

    private void assertTreesMatch(SampledStackNode node, StackNodeMatcher value, int totalSamples) {
        assertThat(node.file, containsString(value.klassSimpleName + ".class"));
        assertThat(node.klass, is(value.getClass().getCanonicalName()));
        assertThat(node.fnName, is(value.fnName));
        assertThat(node.fnSig, is(value.fnSig));
        //TODO: uncomment me!!
        //assertThat(node.lineNo, is(value.lineNo));
        //assertThat(node.bci, is(value.bci));
        int childrenMatched = 0;
        for (StackNodeMatcher child : value.children) {
            Set<SampledStackNode> matchingChildren = node.findChildrenLike(child);
            childrenMatched += matchingChildren.size();
            int actualSampleCount = 0;
            for (SampledStackNode matchingChild : matchingChildren) {
                actualSampleCount += matchingChild.onCpuSampleCount;
            }
            int expectedSamples = child.expectedOncpuPct * totalSamples / 100;
            int toleranceInSamples = child.pctMatchTolerance * totalSamples / 100;
            assertThat((long) actualSampleCount, approximately((long) expectedSamples, (long) toleranceInSamples));
        }
        assertThat(childrenMatched, is(node.children.size()));
    }

    private static final class SampledStackNode {
        private final String klass;
        private final String file;
        private final String fnName;
        private final String fnSig;
        private final int lineNo;
        private int onCpuSampleCount;
        private final int bci;
        private final Map<SampledStackNode, SampledStackNode> children;

        public SampledStackNode(String klass, String file, String fnName, String fnSig, int onCpuSampleCount, int lineNo, int bci, Set<SampledStackNode> children) {
            this.klass = klass;
            this.file = file;
            this.fnName = fnName;
            this.fnSig = fnSig;
            this.lineNo = lineNo;
            this.onCpuSampleCount = onCpuSampleCount;
            this.bci = bci;
            this.children = new HashMap<>();
            for (SampledStackNode child : children) {
                this.children.put(child, child);
            }
        }

        public SampledStackNode(MthdInfo mthdInfo, int lineNo, int bci) {
            this(mthdInfo.klass, mthdInfo.fileName, mthdInfo.name, mthdInfo.sig, 0, lineNo, bci, Collections.emptySet());
        }

        @Override
        public String toString() {
            return "StackNode{" +
                    "klass=" + klass +
                    ", file=" + file +
                    ", fnName='" + fnName + '\'' +
                    ", fnSig='" + fnSig + '\'' +
                    ", lineNo=" + lineNo +
                    ", bci=" + bci +
                    ", onCpuSampleCount=" + onCpuSampleCount +
                    ", children=" + children +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SampledStackNode that = (SampledStackNode) o;

            if (lineNo != that.lineNo) return false;
            if (bci != that.bci) return false;
            if (klass != null ? !klass.equals(that.klass) : that.klass != null) return false;
            if (file != null ? !file.equals(that.file) : that.file != null) return false;
            if (fnName != null ? !fnName.equals(that.fnName) : that.fnName != null) return false;
            if (fnSig != null ? !fnSig.equals(that.fnSig) : that.fnSig != null) return false;
            return true;
        }

        @Override
        public int hashCode() {
            int result = klass != null ? klass.hashCode() : 0;
            result = 31 * result + (file != null ? file.hashCode() : 0);
            result = 31 * result + (fnName != null ? fnName.hashCode() : 0);
            result = 31 * result + (fnSig != null ? fnSig.hashCode() : 0);
            result = 31 * result + lineNo;
            result = 31 * result + bci;
            return result;
        }

        public SampledStackNode findOrCreateChild(MthdInfo mthdInfo, int lineNo, int bci) {
            SampledStackNode node = new SampledStackNode(mthdInfo, lineNo, bci);
            SampledStackNode existingNode = children.get(node);
            if (existingNode == null) {
                children.put(node, node);
                return node;
            }
            return existingNode;
        }

        public Set<SampledStackNode> findChildrenLike(StackNodeMatcher like) {
            Set<SampledStackNode> matched = new HashSet<>();
            for (SampledStackNode child : children.keySet()) {
                if (child.klass.equals(like.klass) &&
                        child.fnName.equals(like.fnName) &&
                        child.fnSig.equals(like.fnSig)) {
                    matched.add(child);
                }
            }
            return matched;
        }
    }

    private static final class StackNodeMatcher {
        private final String klassSimpleName;
        private final String klass;
        private final String fnName;
        private final String fnSig;
        private final int expectedOncpuPct;
        private final int pctMatchTolerance;
        private final Set<StackNodeMatcher> children;

        public StackNodeMatcher(Class klass, String fnName, String fnSig, int onCpuPct, int pctMatchTolerance, Set<StackNodeMatcher> children) {
            this(klass.getSimpleName(), klass.getCanonicalName(), fnName, fnSig, onCpuPct, pctMatchTolerance, children);
        }

        public StackNodeMatcher(String simpleName, String klass, String fnName, String fnSig, int onCpuPct, int pctMatchTolerance, Set<StackNodeMatcher> children) {
            this.klassSimpleName = simpleName;
            this.klass = klass;
            this.fnName = fnName;
            this.fnSig = fnSig;
            this.expectedOncpuPct = onCpuPct;
            this.pctMatchTolerance = pctMatchTolerance;
            this.children = children;
        }

        @Override
        public String toString() {
            return "StackNode{" +
                    "klass=" + klass +
                    ", fnName='" + fnName + '\'' +
                    ", fnSig='" + fnSig + '\'' +
                    ", expectedOncpuPct=" + expectedOncpuPct +
                    ", pctMatchTolerance=" + pctMatchTolerance +
                    ", children=" + children +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            StackNodeMatcher stackNodeMatcher = (StackNodeMatcher) o;

            if (expectedOncpuPct != stackNodeMatcher.expectedOncpuPct) return false;
            if (pctMatchTolerance != stackNodeMatcher.pctMatchTolerance) return false;
            if (!klass.equals(stackNodeMatcher.klass)) return false;
            if (!fnName.equals(stackNodeMatcher.fnName)) return false;
            if (!fnSig.equals(stackNodeMatcher.fnSig)) return false;
            return children.equals(stackNodeMatcher.children);
        }

        @Override
        public int hashCode() {
            int result = klass.hashCode();
            result = 31 * result + fnName.hashCode();
            result = 31 * result + fnSig.hashCode();
            return result;
        }
    }

    private static StackNodeMatcher nodeMatcher(Class klass, String fnName, final String fnSig, int expectedOncpuPct, int pctMatchTolerance, Set<StackNodeMatcher> children) {
        return new StackNodeMatcher(klass, fnName, fnSig, expectedOncpuPct, pctMatchTolerance, children);
    }

    private static StackNodeMatcher rootMatcher(String fnName, final String fnSig, int expectedOncpuPct, int pctMatchTolerance, Set<StackNodeMatcher> children) {
        return new StackNodeMatcher(DUMMY_ROOT_NODE_CLASS_NAME, DUMMY_ROOT_NODE_CLASS_NAME, fnName, fnSig, expectedOncpuPct, pctMatchTolerance, children);
    }

    private static Set<StackNodeMatcher> childrenMatcher(StackNodeMatcher... nodes) {
        Set<StackNodeMatcher> children = new HashSet<>();
        for (StackNodeMatcher node : nodes) {
            children.add(node);
        }
        return children;
    }
}
