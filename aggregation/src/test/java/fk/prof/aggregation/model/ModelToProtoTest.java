package fk.prof.aggregation.model;

import fk.prof.aggregation.proto.AggregatedProfileModel.*;
import fk.prof.aggregation.state.AggregationState;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * @author gaurav.ashok
 */
public class ModelToProtoTest {

    @Test
    public void testProfileSummary_aggregationWindowsShouldProperlyBuildSummaryProto() {
        LocalDateTime now = LocalDateTime.now(Clock.systemUTC());
        FinalizedAggregationWindow window = new FinalizedAggregationWindow("app1", "cluster1", "proc1", now, now.plusSeconds(1200),
                buildMap(
                        101l, new FinalizedProfileWorkInfo(1, AggregationState.COMPLETED, now.plusSeconds(10), now.plusSeconds(90), buildMap("trace1", 5, "trace2", 10), buildMap(WorkType.cpu_sample_work, 100, WorkType.thread_sample_work, 80)),
                        102l, new FinalizedProfileWorkInfo(1, AggregationState.ABORTED, now.plusSeconds(100), now.plusSeconds(200), buildMap("trace1", 10, "trace2", 10), buildMap(WorkType.cpu_sample_work, 1000, WorkType.thread_sample_work, 800))
                        ),
                null
                );

        ProfilesSummary s = window.buildProfileSummary(WorkType.cpu_sample_work, buildTraceList("trace1", 500, "trace2", 600));

        assertNotNull(s);

        // all profiles are group under 1 source, because source info is not being populated yet.
        assertEquals(s.getProfilesCount(), 1);
        PerSourceProfileSummary ps =  s.getProfiles(0);
        assertEquals(ps.getProfilesCount(), 2);
        assertThat(ps.getProfiles(0).getStatus(), anyOf(is(AggregationStatus.Completed), is(AggregationStatus.Aborted)));

        ProfileWorkInfo info1, info2;

        if(ps.getProfiles(0).getStatus().equals(AggregationStatus.Completed)) {
            info1 = ps.getProfiles(0);
            info2 = ps.getProfiles(1);
        }
        else {
            info1 = ps.getProfiles(1);
            info2 = ps.getProfiles(0);
        }

        assertThat(info1.getDuration(), is(80));
        assertThat(info2.getDuration(), is(100));

        assertThat(info1.getStartOffset(), is(10));
        assertThat(info2.getStartOffset(), is(100));

        assertThat(info1.getSampleCount(), is(100));
        assertThat(info2.getSampleCount(), is(1000));

        assertThat(info1.getTraceCoverageMapCount(), is(2));
    }

    @Test
    public void testBuildWorkInfo_profileWorkInfoShouldProperlyBuildWorkInfoProto() {
        LocalDateTime now = LocalDateTime.now(Clock.systemUTC());

        FinalizedProfileWorkInfo wi1 = new FinalizedProfileWorkInfo(1, AggregationState.COMPLETED, now, now.plusMinutes(1),
                buildMap("trace1", 5, "trace2", 10, "trace3", 15),
                buildMap(WorkType.cpu_sample_work, 100, WorkType.thread_sample_work, 80));

        ProfileWorkInfo workInfo = wi1.buildProfileWorkInfo(WorkType.cpu_sample_work, now, buildTraceList("trace1", 50, "trace2", 50));

        assertEquals(workInfo.getDuration(), 60);
        assertEquals(workInfo.getRecorderVersion(), 1);
        assertEquals(workInfo.getSampleCount(), 100);
        assertEquals(workInfo.getStartOffset(), 0);
        assertEquals(workInfo.getStatus(), AggregationStatus.Completed);
        assertEquals(workInfo.getTraceCoverageMapCount(), 2);

        assertThat(workInfo.getTraceCoverageMap(0).getTraceCtxIdx(), is(0));
        assertThat(workInfo.getTraceCoverageMap(0).getCoveragePct(), is(5.0f));

        assertThat(workInfo.getTraceCoverageMap(1).getTraceCtxIdx(), is(1));
        assertThat(workInfo.getTraceCoverageMap(1).getCoveragePct(), is(10.0f));
    }

    @Test
    public void testStackTraceTreeToProto_cpuSamplingTraceDetailsShouldSerializeStacktraceTreeInBatches() throws Exception {
        CpuSamplingTraceDetail traceDetail = new CpuSamplingTraceDetail();

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        /** add nodes in this order:
         *  A
         *  |_ B
         *  |  |_ C
         *  |     |_ D
         *  |_ E
         */

        traceDetail
                // A
                .getGlobalRoot()
                // B
                .getOrAddChild(1, 0)
                // C
                .getOrAddChild(2, 0)
                // D
                .getOrAddChild(3, 0);

        traceDetail.getGlobalRoot()
                // E
                .getOrAddChild(4, 0);

        FinalizedCpuSamplingAggregationBucket.NodeVisitor visitor = new FinalizedCpuSamplingAggregationBucket.NodeVisitor(out, 3, 0);
        traceDetail.getGlobalRoot().traverse(visitor);
        visitor.end();

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

        FrameNodeList nodes = FrameNodeList.parseDelimitedFrom(in);

        assertNotNull(nodes);
        assertThat(nodes.getFrameNodesCount(), is(3));
        assertThat(nodes.getFrameNodes(0).getMethodId(), is(0));
        assertThat(nodes.getFrameNodes(1).getMethodId(), is(1));
        assertThat(nodes.getFrameNodes(2).getMethodId(), is(2));

        FrameNodeList nextNodes = FrameNodeList.parseDelimitedFrom(in);
        assertNotNull(nextNodes);
        assertThat(nextNodes.getFrameNodesCount(), is(2));
        assertThat(nextNodes.getFrameNodes(0).getMethodId(), is(3));
        assertThat(nextNodes.getFrameNodes(1).getMethodId(), is(4));

        assertThat(in.available(), is(0));
    }

    private TraceCtxDetail buildTraceCtxDetails(String name, int count) {
        return TraceCtxDetail.newBuilder().setName(name).setSampleCount(count).build();
    }

    private TraceCtxList buildTraceList(Object... objects) {
        assert objects.length % 2 == 0;

        TraceCtxList.Builder builder = TraceCtxList.newBuilder();

        for(int i = 0 ; i < objects.length; i += 2) {
            builder.addAllTraceCtx(buildTraceCtxDetails((String)objects[i], (Integer)objects[i+1]));
        }

        return builder.build();
    }

    private <K,V> Map<K, V> buildMap(Object... objects) {
        assert objects.length % 2 == 0;

        Map<K, V> map = new HashMap<>();
        for(int i = 0;i < objects.length; i += 2) {
            map.put((K)objects[i], (V)objects[i+1]);
        }

        return map;
    }
}
