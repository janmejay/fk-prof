package fk.prof.userapi.model;

import fk.prof.aggregation.AggregatedProfileNamingStrategy;
import fk.prof.aggregation.Constants;
import fk.prof.aggregation.proto.AggregatedProfileModel;
import fk.prof.aggregation.serialize.Serializer;
import fk.prof.storage.AsyncStorage;
import fk.prof.storage.ObjectNotFoundException;
import fk.prof.storage.S3AsyncStorage;
import fk.prof.storage.buffer.StorageBackedInputStream;
import fk.prof.userapi.Deserializer;
import fk.prof.userapi.api.ProfileStoreAPI;
import fk.prof.userapi.api.ProfileStoreAPIImpl;
import fk.prof.userapi.model.json.ProtoSerializers;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.Adler32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Checksum;

import static org.mockito.Mockito.*;

/**
 * @author gaurav.ashok
 */
@RunWith(VertxUnitRunner.class)
public class ParseProfileTest {

    ProfileStoreAPI profileDiscoveryAPI;
    AsyncStorage asyncStorage;
    Vertx vertx;

    final String traceName1 = "print-trace-1";
    final String traceName2 = "doSome-trace-2";

    @BeforeClass
    public static void setup() {
        ProtoSerializers.registerSerializers(Json.mapper);
    }

    @Before
    public void testSetUp(TestContext context) {
        vertx = Vertx.vertx();
        asyncStorage = mock(AsyncStorage.class);
        profileDiscoveryAPI = new ProfileStoreAPIImpl(vertx, asyncStorage, 30);
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    private void checksumVerify(Checksum checksum, int value, String msg) {
        assert value == (int)checksum.getValue() : msg;
    }

    @Test
    public void testAggregatedProfileStoreS3Impl(TestContext context) throws Exception {
        Async async = context.async();

        S3AsyncStorage storage = mock(S3AsyncStorage.class);
        String fileName = new AggregatedProfileNamingStrategy("profiles", buildHeader()).getFileName(0);
        InputStream s3InputStream = buildDefaultS3DataStream();

        // for above filename return the inputStream
        when(storage.fetchAsync(fileName)).thenReturn(CompletableFuture.supplyAsync(() -> s3InputStream));
        // for other filenames throw ObjectNotFoundException
        when(storage.fetchAsync(argThat(arg -> !fileName.equals(arg)))).thenReturn(CompletableFuture.supplyAsync(() -> {
            throw new ObjectNotFoundException("not found");
        }));

        profileDiscoveryAPI = new ProfileStoreAPIImpl(vertx, storage, 30);

        Future<AggregatedProfileInfo> future1 = Future.future();
        Future<AggregatedProfileInfo> future2 = Future.future();

        CompositeFuture cfuture = CompositeFuture.all(future1, future2);
        cfuture.setHandler(result -> {
            try {
                if (result.failed()) {
                    context.fail(result.cause());
                } else {
                    // match the response
                    AggregatedProfileInfo firstResult = result.result().resultAt(0);
                    AggregatedProfileInfo secondResult = result.result().resultAt(1);
                    testEquality(context, buildDefaultProfileInfo(), firstResult);
                    // both results are actually the same cached object
                    context.assertTrue(firstResult == secondResult);

                    verify(storage, times(1)).fetchAsync(any());
                    verifyNoMoreInteractions(storage);
                }
            }
            catch (Exception e) {
                context.fail(e);
            }
            finally {
                async.complete();
            }
        });

        profileDiscoveryAPI.load(future1, new AggregatedProfileNamingStrategy("profiles", buildHeader()));
        profileDiscoveryAPI.load(future2, new AggregatedProfileNamingStrategy("profiles", buildHeader()));
    }

    private void testEquality(TestContext context, AggregatedProfileInfo expected, AggregatedProfileInfo actual) {
        context.assertEquals(expected.getHeader(), actual.getHeader());
        context.assertEquals(expected.getProfileSummary().getTraces(), actual.getProfileSummary().getTraces());
        context.assertEquals(expected.getProfileSummary().getProfilesSummary(), actual.getProfileSummary().getProfilesSummary());
        context.assertEquals(expected.getAggregatedSamples(traceName1).getMethodLookup(), actual.getAggregatedSamples(traceName1).getMethodLookup());
        context.assertEquals(expected.getAggregatedSamples(traceName2).getMethodLookup(), actual.getAggregatedSamples(traceName2).getMethodLookup());

        if(expected.getAggregatedSamples(traceName1).getAggregatedSamples() instanceof AggregatedCpuSamplesData) {
            testEquality(context, (AggregatedCpuSamplesData)expected.getAggregatedSamples(traceName1).getAggregatedSamples(),
                    (AggregatedCpuSamplesData)actual.getAggregatedSamples(traceName1).getAggregatedSamples());

            testEquality(context, (AggregatedCpuSamplesData)expected.getAggregatedSamples(traceName2).getAggregatedSamples(),
                    (AggregatedCpuSamplesData)actual.getAggregatedSamples(traceName2).getAggregatedSamples());
        }
        else {
            context.fail("Unexpected type of AggregatedSamples in profileInfo");
        }
    }

    private void testEquality(TestContext context, AggregatedCpuSamplesData expected, AggregatedCpuSamplesData actual) {
        Iterator<AggregatedProfileModel.FrameNode> expectedFN = expected.getFrameNodes().iterator();
        Iterator<AggregatedProfileModel.FrameNode> actualFN = actual.getFrameNodes().iterator();

        while(expectedFN.hasNext() && actualFN.hasNext()) {
            context.assertEquals(expectedFN.next(), actualFN.next());
        }

        if(expectedFN.hasNext() && !actualFN.hasNext()) {
            context.fail("expected more FrameNodes in the actual list");
        }
        else if(!expectedFN.hasNext() && actualFN.hasNext()) {
            context.fail("found more frameNodes than expected in the actual list");
        }
    }

    private AggregatedProfileInfo buildDefaultProfileInfo() {
        List<AggregatedProfileModel.FrameNodeList> frameNodes = buildFrameNodes();
        Map<String, AggregatedSamplesPerTraceCtx> samples = new HashMap<>();
        // first 2 elements belong to trace1
        samples.put(traceName1, new AggregatedSamplesPerTraceCtx(buildMethodLookup(), new AggregatedCpuSamplesData(new StacktraceTreeIterable(frameNodes.subList(0,2)))));
        // next 2 elements belong to trace 2
        samples.put(traceName2, new AggregatedSamplesPerTraceCtx(buildMethodLookup(), new AggregatedCpuSamplesData(new StacktraceTreeIterable(frameNodes.subList(2,4)))));

        return new AggregatedProfileInfo(buildHeader(), new ScheduledProfilesSummary(buildTraceCtxList(), buildProfilesSummary()), samples);
    }

    private InputStream buildDefaultS3DataStream() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Adler32 adler32 = new Adler32();

        CheckedOutputStream cout = new CheckedOutputStream(out, adler32);

        Serializer.writeFixedWidthInt32(Constants.AGGREGATION_FILE_MAGIC_NUM, cout);

        adler32.reset();
        buildHeader().writeDelimitedTo(cout);
        Serializer.writeFixedWidthInt32((int)adler32.getValue(), cout);

        adler32.reset();
        buildTraceCtxList().writeDelimitedTo(cout);
        Serializer.writeFixedWidthInt32((int)adler32.getValue(), cout);

        adler32.reset();
        buildProfilesSummary().writeDelimitedTo(cout);
        Serializer.writeFixedWidthInt32((int)adler32.getValue(), cout);

        adler32.reset();
        buildMethodLookup().writeDelimitedTo(cout);
        Serializer.writeFixedWidthInt32((int)adler32.getValue(), cout);

        adler32.reset();
        for (AggregatedProfileModel.FrameNodeList frameNodes : buildFrameNodes()) {
            frameNodes.writeDelimitedTo(cout);
        }
        Serializer.writeFixedWidthInt32((int)adler32.getValue(), cout);

        cout.flush();

        return new ByteArrayInputStream(out.toByteArray());
    }

    private AggregatedProfileModel.MethodLookUp buildMethodLookup() {
        return AggregatedProfileModel.MethodLookUp.newBuilder()
                .addFqdn("~ ROOT ~.()")
                .addFqdn("~ UNCLASSIFIABLE ~.()")
                .addFqdn("com.example.App.main(String[])")
                .addFqdn("com.example.App.print(String)")
                .addFqdn("com.example.App.doSomething(String, int)")
                .build();
    }

    /**
     * @return Returns a List of FrameNodes for a stackTrace tree:
     *         root
     *         |_ unclassified
     *         |_ main
     *            |_ dosomething
     *            |_ print
     */
    private List<AggregatedProfileModel.FrameNodeList> buildFrameNodes() {
        List<AggregatedProfileModel.FrameNodeList> list = new ArrayList<>();

        list.add(AggregatedProfileModel.FrameNodeList.newBuilder()
                .addFrameNodes(AggregatedProfileModel.FrameNode.newBuilder().setMethodId(0).setLineNo(0).setChildCount(2).setCpuSamplingProps(AggregatedProfileModel.CPUSamplingNodeProps.newBuilder().setOnStackSamples(600).setOnCpuSamples(0)))
                .addFrameNodes(AggregatedProfileModel.FrameNode.newBuilder().setMethodId(1).setLineNo(0).setChildCount(0).setCpuSamplingProps(AggregatedProfileModel.CPUSamplingNodeProps.newBuilder().setOnStackSamples(0).setOnCpuSamples(0)))
                .addFrameNodes(AggregatedProfileModel.FrameNode.newBuilder().setMethodId(2).setLineNo(10).setChildCount(1).setCpuSamplingProps(AggregatedProfileModel.CPUSamplingNodeProps.newBuilder().setOnStackSamples(600).setOnCpuSamples(0)))
                .setTraceCtxIdx(0)
                .build());

        list.add(AggregatedProfileModel.FrameNodeList.newBuilder()
                .addFrameNodes(AggregatedProfileModel.FrameNode.newBuilder().setMethodId(4).setLineNo(20).setChildCount(1).setCpuSamplingProps(AggregatedProfileModel.CPUSamplingNodeProps.newBuilder().setOnStackSamples(600).setOnCpuSamples(0)))
                .addFrameNodes(AggregatedProfileModel.FrameNode.newBuilder().setMethodId(3).setLineNo(40).setChildCount(0).setCpuSamplingProps(AggregatedProfileModel.CPUSamplingNodeProps.newBuilder().setOnStackSamples(600).setOnCpuSamples(600)))
                .setTraceCtxIdx(0)
                .build());

        list.add(AggregatedProfileModel.FrameNodeList.newBuilder()
                .addFrameNodes(AggregatedProfileModel.FrameNode.newBuilder().setMethodId(0).setLineNo(0).setChildCount(2).setCpuSamplingProps(AggregatedProfileModel.CPUSamplingNodeProps.newBuilder().setOnStackSamples(1280).setOnCpuSamples(0)))
                .addFrameNodes(AggregatedProfileModel.FrameNode.newBuilder().setMethodId(1).setLineNo(0).setChildCount(0).setCpuSamplingProps(AggregatedProfileModel.CPUSamplingNodeProps.newBuilder().setOnStackSamples(0).setOnCpuSamples(0)))
                .addFrameNodes(AggregatedProfileModel.FrameNode.newBuilder().setMethodId(2).setLineNo(10).setChildCount(1).setCpuSamplingProps(AggregatedProfileModel.CPUSamplingNodeProps.newBuilder().setOnStackSamples(1280).setOnCpuSamples(0)))
                .setTraceCtxIdx(1)
                .build());

        list.add(AggregatedProfileModel.FrameNodeList.newBuilder()
                .addFrameNodes(AggregatedProfileModel.FrameNode.newBuilder().setMethodId(4).setLineNo(21).setChildCount(0).setCpuSamplingProps(AggregatedProfileModel.CPUSamplingNodeProps.newBuilder().setOnStackSamples(1280).setOnCpuSamples(1280)))
                .setTraceCtxIdx(1)
                .build());

        return list;
    }

    private AggregatedProfileModel.Header buildHeader() {
        ZonedDateTime start = ZonedDateTime.parse("2017-01-30T09:54:53.852Z", DateTimeFormatter.ISO_ZONED_DATE_TIME);
        return AggregatedProfileModel.Header.newBuilder()
                .setAppId("app1")
                .setProcId("svc1")
                .setClusterId("cluster1")
                .setWorkType(AggregatedProfileModel.WorkType.cpu_sample_work)
                .setAggregationStartTime(start.format(DateTimeFormatter.ISO_ZONED_DATE_TIME))
                .setAggregationEndTime(start.plusMinutes(30).format(DateTimeFormatter.ISO_ZONED_DATE_TIME))
                .setFormatVersion(1)
                .build();
    }

    private AggregatedProfileModel.TraceCtxList buildTraceCtxList() {
        return AggregatedProfileModel.TraceCtxList.newBuilder()
                .addAllTraceCtx(AggregatedProfileModel.TraceCtxDetail.newBuilder()
                        .setName(traceName1)
                        .setSampleCount(600))
                .addAllTraceCtx(AggregatedProfileModel.TraceCtxDetail.newBuilder()
                        .setName(traceName2)
                        .setSampleCount(1280)).build();
    }

    private AggregatedProfileModel.ProfilesSummary buildProfilesSummary() {
        return AggregatedProfileModel.ProfilesSummary.newBuilder()
                .addProfiles(
                        AggregatedProfileModel.PerSourceProfileSummary.newBuilder()
                            .setSourceInfo(AggregatedProfileModel.ProfileSourceInfo.newBuilder()
                            .setZone("chennai-1")
                            .setProcessName("svc1")
                            .setIp("192.168.1.1")
                            .setInstanceType("c1.xlarge")
                            .setHostname("some-box-1"))
                        .addProfiles(AggregatedProfileModel.ProfileWorkInfo.newBuilder()
                                .setStartOffset(10)
                                .setDuration(60)
                                .setRecorderVersion(1)
                                .setSampleCount(600)
                                .setStatus(AggregatedProfileModel.AggregationStatus.Completed)
                                .addTraceCoverageMap(AggregatedProfileModel.TraceCtxToCoveragePctMap.newBuilder()
                                        .setTraceCtxIdx(0)
                                        .setCoveragePct(5))
                                .addTraceCoverageMap(AggregatedProfileModel.TraceCtxToCoveragePctMap.newBuilder()
                                        .setTraceCtxIdx(1)
                                        .setCoveragePct(10))))
                .addProfiles(
                        AggregatedProfileModel.PerSourceProfileSummary.newBuilder()
                                .setSourceInfo(AggregatedProfileModel.ProfileSourceInfo.newBuilder()
                                        .setZone("chennai-1")
                                        .setProcessName("svc1")
                                        .setIp("192.168.1.2")
                                        .setInstanceType("c1.xlarge")
                                        .setHostname("some-box-2"))
                                .addProfiles(AggregatedProfileModel.ProfileWorkInfo.newBuilder()
                                        .setStartOffset(24)
                                        .setDuration(60)
                                        .setRecorderVersion(1)
                                        .setSampleCount(680)
                                        .setStatus(AggregatedProfileModel.AggregationStatus.Retried)
                                        .addTraceCoverageMap(AggregatedProfileModel.TraceCtxToCoveragePctMap.newBuilder()
                                                .setTraceCtxIdx(0)
                                                .setCoveragePct(5))
                                        .addTraceCoverageMap(AggregatedProfileModel.TraceCtxToCoveragePctMap.newBuilder()
                                                .setTraceCtxIdx(1)
                                                .setCoveragePct(10)))).build();
    }
}
