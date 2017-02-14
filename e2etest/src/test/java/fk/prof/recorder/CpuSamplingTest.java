package fk.prof.recorder;

import fk.prof.recorder.cpuburn.Burn20Of100;
import fk.prof.recorder.cpuburn.Burn80Of100;
import fk.prof.recorder.cpuburn.WrapperA;
import fk.prof.recorder.main.Burn20And80PctCpu;
import fk.prof.recorder.main.Burn50And50PctCpu;
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
    private static final String USUAL_RECORDER_ARGS = "service_endpoint=http://127.0.0.1:8080," +
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
            "logLvl=trace";
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

        runner = new AgentRunner(Burn20And80PctCpu.class.getCanonicalName(), USUAL_RECORDER_ARGS);
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
        List<Recorder.Wse> profileEntries = new ArrayList<>();
        MutableObject<Recorder.RecordingHeader> hdr = new MutableObject<>();
        MutableBoolean profileCalledSecondTime = new MutableBoolean(false);
        String cpuSamplingWorkIssueTime = ISODateTimeFormat.dateTime().print(DateTime.now());

        PollReqWithTime[] pollReqs = stubRecorderInteraction(profileEntries, hdr, profileCalledSecondTime, cpuSamplingWorkIssueTime);

        runner = new AgentRunner(Burn20And80PctCpu.class.getCanonicalName(), USUAL_RECORDER_ARGS);
        runner.start();

        assocAction[0].get(4, TimeUnit.SECONDS);
        long prevTime = System.currentTimeMillis();

        assertThat(assocAction[0].isDone(), is(true));
        pollAction[poll.length - 1].get(poll.length + 4, TimeUnit.SECONDS); //some grace time

        assertPollIntervalIsGood(pollReqs, prevTime);

        assertPolledInStatusIsGood(pollReqs);

        assertRecordingHeaderIsGood(cpuSamplingWorkIssueTime, hdr);

        assertProfileCallAndContent(profileCalledSecondTime, profileEntries, Collections.singletonMap("inferno",
                rootMatcher(DUMMY_ROOT_NODE_FN_NAME, DUMMY_ROOT_NOTE_FN_SIGNATURE, 0, 1, childrenMatcher(
                        nodeMatcher(Burn20And80PctCpu.class, "main", "([Ljava/lang/String;)V", 0, 1, childrenMatcher(
                                nodeMatcher(Burn20And80PctCpu.class, "burnCpu", "()V", 0, 1, childrenMatcher(
                                        nodeMatcher(Burn20Of100.class, "burn", "()V", 0, 1, childrenMatcher(
                                                nodeMatcher(Blackhole.class, "consumeCPU", "(J)V", 20, 10, Collections.emptySet()))),
                                        nodeMatcher(WrapperA.class, "burnSome", "(S)V", 0, 1, childrenMatcher(
                                                nodeMatcher(Burn80Of100.class, "burn", "()V", 0, 1, childrenMatcher(
                                                        nodeMatcher(Blackhole.class, "consumeCPU", "(J)V", 80, 10, Collections.emptySet())))))))))))));
    }

    @Test
    public void should_respect_Coverage_and_MergeSemantic() throws ExecutionException, InterruptedException, IOException, TimeoutException {
        List<Recorder.Wse> profileEntries = new ArrayList<>();
        MutableObject<Recorder.RecordingHeader> hdr = new MutableObject<>();
        MutableBoolean profileCalledSecondTime = new MutableBoolean(false);
        String cpuSamplingWorkIssueTime = ISODateTimeFormat.dateTime().print(DateTime.now());

        PollReqWithTime[] pollReqs = stubRecorderInteraction(profileEntries, hdr, profileCalledSecondTime, cpuSamplingWorkIssueTime);

        runner = new AgentRunner(Burn50And50PctCpu.class.getCanonicalName(), USUAL_RECORDER_ARGS);
        runner.start();

        assocAction[0].get(4, TimeUnit.SECONDS);
        long prevTime = System.currentTimeMillis();

        assertThat(assocAction[0].isDone(), is(true));
        pollAction[poll.length - 1].get(poll.length + 4, TimeUnit.SECONDS); //some grace time

        assertPollIntervalIsGood(pollReqs, prevTime);

        assertPolledInStatusIsGood(pollReqs);

        assertRecordingHeaderIsGood(cpuSamplingWorkIssueTime, hdr);
        
        assertProfileCallAndContent(profileCalledSecondTime, profileEntries, new HashMap<String, StackNodeMatcher>(){{
            Class klass = Burn50And50PctCpu.class;
            put("100_pct_single_inferno", generateStackSampleMatcher(66, klass));
            put("50_pct_duplicate_inferno", generateStackSampleMatcher(33, klass));
            put("50_pct_duplicate_inferno_child", generateStackSampleMatcher(33, klass));
        }});
    }

    private StackNodeMatcher generateStackSampleMatcher(final int expectedOncpuPct, final Class klass) {
        return rootMatcher(DUMMY_ROOT_NODE_FN_NAME, DUMMY_ROOT_NOTE_FN_SIGNATURE, 0, 1, childrenMatcher(
                nodeMatcher(klass, "main", "([Ljava/lang/String;)V", 0, 1, childrenMatcher(
                        nodeMatcher(klass, "immolate", "(I)V", 0, 1, childrenMatcher(
                                nodeMatcher(Blackhole.class, "consumeCPU", "(J)V", expectedOncpuPct, 25, Collections.emptySet())))))));
    }

    private PollReqWithTime[] stubRecorderInteraction(List<Recorder.Wse> profileEntries, MutableObject<Recorder.RecordingHeader> hdr, MutableBoolean profileCalledSecondTime, String cpuSamplingWorkIssueTime) {
        PollReqWithTime pollReqs[] = new PollReqWithTime[poll.length];

        MutableObject<Recorder.RecorderInfo> recInfo = new MutableObject<>();

        association[0] = pointToAssociate(recInfo, 8090);

        poll[0] = tellRecorderWeHaveNoWork(pollReqs, 0);

        poll[1] = issueCpuProfilingWork(pollReqs, 1, 10, 2, cpuSamplingWorkIssueTime, CPU_SAMPLING_WORK_ID);
        for (int i = 2; i < poll.length; i++) {
            poll[i] = tellRecorderWeHaveNoWork(pollReqs, i);
        }


        profile[0] = (req) -> {
            recordProfile(req, hdr, profileEntries);
            return new byte[0];
        };
        profile[1] = (req) -> {
            profileCalledSecondTime.setTrue();
            return null;
        };
        return pollReqs;
    }

    private void assertPolledInStatusIsGood(PollReqWithTime[] pollReqs) {
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
    }

    private void assertPollIntervalIsGood(PollReqWithTime[] pollReqs, long prevTime) {
        long idx = 0;
        for (PollReqWithTime prwt : pollReqs) {
            assertRecorderInfoAllGood(prwt.req.getRecorderInfo());
            if (idx > 0) {
                assertThat("idx = " + idx, prwt.time - prevTime, approximatelyBetween(1000l, 2000l)); //~1 sec tolerance
            }
            prevTime = prwt.time;
            idx++;
        }
    }

    private void assertRecordingHeaderIsGood(String cpuSamplingWorkIssueTime, MutableObject<Recorder.RecordingHeader> hdr) {
        Recorder.Work w = Recorder.Work.newBuilder()
                .setWType(Recorder.WorkType.cpu_sample_work)
                .setCpuSample(Recorder.CpuSampleWork.newBuilder()
                        .setMaxFrames(CPU_SAMPLING_MAX_FRAMES)
                        .setFrequency(CPU_SAMPLING_FREQ)
                        .build())
                .build();
        WorkHandlingTest.assertRecordingHeaderIsGood(hdr.getValue(), CONTROLLER_ID, CPU_SAMPLING_WORK_ID, cpuSamplingWorkIssueTime, 10, 2, 1, new Recorder.Work[]{w});
    }

    private void assertProfileCallAndContent(MutableBoolean profileCalledSecondTime, List<Recorder.Wse> profileEntries, Map<String, StackNodeMatcher> expectedContent) {
        assertCpuProfileEntriesAre(profileEntries, expectedContent,
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

        @Override
        public String toString() {
            return "TraceInfo{" +
                    "name='" + name + '\'' +
                    ", coverage=" + coverage +
                    '}';
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
        Map<String, SampledStackNode> aggregations = new HashMap<>();
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
                String name = traceEntry.getTraceName();
                traceInfoMap.put(id, new TraceInfo(name, traceEntry.getCoveragePct()));
                aggregations.put(name, new SampledStackNode("L" + DUMMY_ROOT_NODE_CLASS_NAME.replaceAll("\\.", "/") + ";", DUMMY_ROOT_NODE_CLASS_NAME + ".java", DUMMY_ROOT_NODE_FN_NAME, DUMMY_ROOT_NOTE_FN_SIGNATURE, 0, 0, 0, new HashSet<>()));
            }
            for (Recorder.MethodInfo mthdEntry : idxData.getMethodInfoList()) {
                long id = mthdEntry.getMethodId();
                assertThat(mthdInfoMap.containsKey(id), is(false));
                mthdInfoMap.put(id, new MthdInfo(mthdEntry.getClassFqdn(), mthdEntry.getMethodName(), mthdEntry.getSignature(), mthdEntry.getFileName()));
            }
            Recorder.StackSampleWse e = entry.getCpuSampleEntry();
            for (int i = 0; i < e.getStackSampleCount(); i++) {
                Recorder.StackSample stackSample = e.getStackSample(i);
                for (Integer traceId : stackSample.getTraceIdList()) {
                    TraceInfo traceInfo = traceInfoMap.get(traceId);
                    SampledStackNode currentNode = aggregations.get(traceInfo.name);
                    for (int j = stackSample.getFrameCount(); j > 0; j--) {
                        Recorder.Frame frame = stackSample.getFrame(j - 1);
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
        assertThat(node.file, containsString(value.klassSimpleName + ".java"));
        assertThat(node.klass, is(value.klass));
        assertThat(node.fnName, is(value.fnName));
        assertThat(node.fnSig, is(value.fnSig));
        //TODO: uncomment me!!
        //assertThat(node.lineNo, is(value.lineNo));
        //assertThat(node.bci, is(value.bci));
        int childrenMatched = 0;
        int totalSamplesAccountedFor = 0;
        for (StackNodeMatcher child : value.children) {
            SampledStackNode matchingChild = node.findChildLike(child);
            childrenMatched++;
            assertThat(matchingChild, notNullValue());
            if (matchingChild.onCpuSampleCount > 0) {
                int actualSampleCount = matchingChild.onCpuSampleCount;
                totalSamplesAccountedFor += actualSampleCount;
                int expectedSamples = child.expectedOncpuPct * totalSamples / 100;
                int toleranceInSamples = child.pctMatchTolerance * totalSamples / 100;
                assertThat((long) actualSampleCount, approximately((long) expectedSamples, (long) toleranceInSamples));
            }
            assertTreesMatch(matchingChild, child, totalSamples);
        }
        int immediateChildrenSampleCount = node.immediateChildrenSampleCount();
        if (immediateChildrenSampleCount > 5) {
            assertThat((long) totalSamplesAccountedFor, approximately(immediateChildrenSampleCount));    
        }
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
            return "{\"type\": \"Sampled\"," +
                    "\"klass\":\"" + klass + "\"" +
                    ", \"file\":\"" + file + "\"" +
                    ", \"fnName\":\"" + fnName + "\"" +
                    ", \"fnSig\":\"" + fnSig + '\"' +
//                    ", \"lineNo\":" + lineNo +
//                    ", \"bci\":" + bci +
                    ", \"onCpuSampleCount\":" + onCpuSampleCount +
                    ", \"children\":" + childrenArray(children) +
                    '}';
        }

        private String childrenArray(Map<SampledStackNode, SampledStackNode> children) {
            StringBuilder b = new StringBuilder();
            b.append("[");
            for (SampledStackNode node : children.keySet()) {
                if (b.length() > 1) b.append(",");
                b.append(node);
            }
            b.append("]");
            return b.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SampledStackNode that = (SampledStackNode) o;

//            if (lineNo != that.lineNo) return false;
//            if (bci != that.bci) return false;
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
//            result = 31 * result + lineNo;
//            result = 31 * result + bci;
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

        public SampledStackNode findChildLike(StackNodeMatcher like) {
            for (SampledStackNode child : children.keySet()) {
                if (child.klass.equals(like.klass) &&
                        child.fnName.equals(like.fnName) &&
                        child.fnSig.equals(like.fnSig)) {
                    return child;
                }
            }
            return null;
        }

        public Set<SampledStackNode> findChildrenLike(StackNodeMatcher like) {
            Set<SampledStackNode> matched = new HashSet<>();
            String targetKlassName = "L" + like.klass.replaceAll("\\.", "/") + ";";
            for (SampledStackNode child : children.keySet()) {
                if (child.klass.equals(targetKlassName) &&
                        child.fnName.equals(like.fnName) &&
                        child.fnSig.equals(like.fnSig)) {
                    matched.add(child);
                }
            }
            return matched;
        }

        public int immediateChildrenSampleCount() {
            int count = 0;
            for (SampledStackNode stackNode : children.keySet()) {
                count += stackNode.onCpuSampleCount;
            }
            return count;
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
            this.klass = "L" + klass.replaceAll("\\.", "/") + ";";
            this.fnName = fnName;
            this.fnSig = fnSig;
            this.expectedOncpuPct = onCpuPct;
            this.pctMatchTolerance = pctMatchTolerance;
            this.children = children;
        }

        @Override
        public String toString() {
            return "{\"type\": \"Matcher\"," +
                    "\"klass\":\"" + klass + "\"" +
                    ", \"fnName\":\"" + fnName + "\"" +
                    ", \"fnSig\":\"" + fnSig + '\"' +
                    ", \"expectedOncpuPct\":" + expectedOncpuPct +
                    ", \"pctMatchTolerance\":" + pctMatchTolerance +
                    ", \"children\":" + children +
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
