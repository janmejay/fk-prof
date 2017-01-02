package fk.prof.backend.http;

import com.google.protobuf.CodedOutputStream;
import fk.prof.backend.aggregator.AggregationStatus;
import fk.prof.backend.aggregator.AggregationWindow;
import fk.prof.backend.aggregator.CpuSamplingAggregationBucket;
import fk.prof.backend.aggregator.ProfileWorkInfo;
import fk.prof.backend.mock.MockProfileComponents;
import fk.prof.backend.service.IProfileWorkService;
import fk.prof.backend.service.ProfileWorkService;
import fk.prof.common.stacktrace.cpusampling.CpuSamplingContextDetail;
import fk.prof.common.stacktrace.cpusampling.CpuSamplingFrameNode;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import recording.Recorder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

@RunWith(VertxUnitRunner.class)
public class ProfileApiTest {

  private Vertx vertx;
  private Integer port = 9300;
  private IProfileWorkService profileWorkService;
  private AtomicLong workIdCounter = new AtomicLong(0);

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    profileWorkService = new ProfileWorkService();
    //Deploying two verticles with shared profileworkservice instance
    Verticle mainVerticle1 = new MainVerticle(profileWorkService);
    Verticle mainVerticle2 = new MainVerticle(profileWorkService);
    DeploymentOptions deploymentOptions = new DeploymentOptions();
    vertx.deployVerticle(mainVerticle1, deploymentOptions, context.asyncAssertSuccess());
    vertx.deployVerticle(mainVerticle2, deploymentOptions, context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void testWithSingleProfile(TestContext context) throws IOException {
    long workId = workIdCounter.incrementAndGet();
    profileWorkService.associateAggregationWindow(workId,
        new AggregationWindow("a", "c", "p", LocalDateTime.now(), 20, 60, new long[]{workId}));

    final Async async = context.async();
    Future<Buffer> future = makeProfileRequest(context, workId, getMockWseEntriesForSingleProfile());
    future.setHandler(ar -> {
      if (ar.failed()) {
        context.fail(ar.cause());
      } else {
        //Validate aggregation
        AggregationWindow aggregationWindow = profileWorkService.getAssociatedAggregationWindow(workId);
        validateAggregationBucketOfPredefinedSamples(context, aggregationWindow);

        ProfileWorkInfo wi = aggregationWindow.getWorkInfo(workId);
        validateWorkInfo(context, wi, 3);

        async.complete();
      }
    });
  }

  @Test
  public void testWithMultipleProfiles(TestContext context) throws IOException {
    long workId1 = workIdCounter.incrementAndGet();
    long workId2 = workIdCounter.incrementAndGet();
    long workId3 = workIdCounter.incrementAndGet();
    AggregationWindow aw = new AggregationWindow("a", "c", "p", LocalDateTime.now(), 20, 60, new long[]{workId1, workId2, workId3});
    profileWorkService.associateAggregationWindow(workId1, aw);
    profileWorkService.associateAggregationWindow(workId2, aw);
    profileWorkService.associateAggregationWindow(workId3, aw);
    List<Recorder.Wse> wseList = getMockWseEntriesForMultipleProfiles();

    final Async async = context.async();
    Future<Buffer> f1 = makeProfileRequest(context, workId1, Arrays.asList(wseList.get(0)));
    Future<Buffer> f2 = makeProfileRequest(context, workId2, Arrays.asList(wseList.get(1)));
    Future<Buffer> f3 = makeProfileRequest(context, workId3, Arrays.asList(wseList.get(2)));
    CompositeFuture.all(Arrays.asList(f1, f2, f3)).setHandler(ar -> {
      if (ar.failed()) {
        context.fail(ar.cause());
      } else {
        AggregationWindow aggregationWindow = profileWorkService.getAssociatedAggregationWindow(workId1);
        validateAggregationBucketOfPredefinedSamples(context, aggregationWindow);

        ProfileWorkInfo wi1 = aggregationWindow.getWorkInfo(workId1);
        validateWorkInfo(context, wi1, 1);
        ProfileWorkInfo wi2 = aggregationWindow.getWorkInfo(workId2);
        validateWorkInfo(context, wi2, 1);
        ProfileWorkInfo wi3 = aggregationWindow.getWorkInfo(workId3);
        validateWorkInfo(context, wi3, 1);

        async.complete();
      }
    });

  }

  private Future<Buffer> makeProfileRequest(TestContext context, long workId, List<Recorder.Wse> wseList) {
    Future<Buffer> future = Future.future();
    vertx.executeBlocking(blockingFuture -> {
      try {
        HttpClientRequest request = vertx.createHttpClient()
            .post(port, "localhost", "/profile")
            .handler(response -> {
              context.assertEquals(response.statusCode(), 200);
              response.bodyHandler(buffer -> {
                //If any error happens, returned in formatted json, printing for debugging purposes
                System.out.println(buffer.toString());
                context.assertEquals(response.statusCode(), 200);
                blockingFuture.complete(buffer);
              });
            })
            .exceptionHandler(throwable -> blockingFuture.fail(throwable))
            .setChunked(true);

        ByteArrayOutputStream requestStream = new ByteArrayOutputStream();
        writeMockHeaderToRequest(workId, requestStream);
        writeMockWseEntriesToRequest(wseList, requestStream);
        byte[] requestBytes = requestStream.toByteArray();
        chunkAndWriteToRequest(request, requestBytes, 32);
      } catch (IOException ex) {
        blockingFuture.fail(ex);
      }
    }, false, future.completer());

    return future;
  }

  private void validateAggregationBucketOfPredefinedSamples(TestContext context, AggregationWindow aggregationWindow) {
    context.assertNotNull(aggregationWindow);
    context.assertTrue(aggregationWindow.hasProfileData(Recorder.WorkType.cpu_sample_work));

    CpuSamplingAggregationBucket aggregationBucket = aggregationWindow.getCpuSamplingAggregationBucket();
    context.assertEquals(aggregationBucket.getAvailableContexts(), new HashSet<>(Arrays.asList("1")));
    Map<Long, String> methodNameLookup = aggregationBucket.getMethodNameLookup();
    //Subtracting method count by 2, because ROOT, UNCLASSIFIABLE are static names always present in the map
    context.assertEquals(methodNameLookup.size() - 2, 5);
    CpuSamplingContextDetail traceDetail = aggregationBucket.getContext("1");
    context.assertTrue(traceDetail.getUnclassifiableRoot().getChildrenUnsafe().size() == 1);

    CpuSamplingFrameNode logicalRoot = traceDetail.getUnclassifiableRoot().getChildrenUnsafe().get(0);
    context.assertEquals(methodNameLookup.get(logicalRoot.getMethodId()), ".Y()");
    context.assertEquals(logicalRoot.getOnStackSamples(), 3);
    context.assertEquals(logicalRoot.getOnCpuSamples(), 0);

    List<CpuSamplingFrameNode> leaves = new ArrayList<>();
    Queue<CpuSamplingFrameNode> toVisit = new ArrayDeque<>();
    toVisit.add(logicalRoot);
    while (toVisit.size() > 0) {
      CpuSamplingFrameNode visiting = toVisit.poll();
      List<CpuSamplingFrameNode> children = visiting.getChildrenUnsafe();
      if (children.size() == 0) {
        leaves.add(visiting);
      } else {
        for (CpuSamplingFrameNode child : children) {
          toVisit.offer(child);
        }
      }
    }
    context.assertEquals(leaves.size(), 3);

    int dMethodLeaves = 0, cMethodLeaves = 0;
    for (CpuSamplingFrameNode leaf : leaves) {
      context.assertEquals(leaf.getOnStackSamples(), 1);
      context.assertEquals(leaf.getOnCpuSamples(), 1);
      if (methodNameLookup.get(leaf.getMethodId()).equals(".C()")) {
        cMethodLeaves++;
      } else if (methodNameLookup.get(leaf.getMethodId()).equals(".D()")) {
        dMethodLeaves++;
      }
    }
    context.assertEquals(cMethodLeaves, 1);
    context.assertEquals(dMethodLeaves, 2);
  }

  private void validateWorkInfo(TestContext context, ProfileWorkInfo workInfo, int expectedSamples) {
    context.assertEquals(workInfo.getStatus(), AggregationStatus.COMPLETED);
    context.assertNotNull(workInfo.getStartedAt());
    context.assertNotNull(workInfo.getEndedAt());
    context.assertEquals(workInfo.getTraceCoverages().size(), 1);
    context.assertEquals(workInfo.getTraceCoverages().get("1"), 5);
    context.assertEquals(workInfo.getSamples(), expectedSamples);
  }

  private static void chunkAndWriteToRequest(HttpClientRequest request, byte[] requestBytes, int chunkSizeInBytes) {
    int i = 0;
    for (; (i + chunkSizeInBytes) <= requestBytes.length; i += chunkSizeInBytes) {
      writeChunkToRequest(request, requestBytes, i, i + chunkSizeInBytes);
    }
    writeChunkToRequest(request, requestBytes, i, requestBytes.length);
    writeChunkToRequest(request, requestBytes, 0, requestBytes.length);

    request.end();
  }

  private static void writeChunkToRequest(HttpClientRequest request, byte[] bytes, int start, int end) {
    request.write(Buffer.buffer(Arrays.copyOfRange(bytes, start, end)));
    try {
      Thread.sleep(10);
    } catch (Exception ex) {
    }
  }

  private static void writeMockHeaderToRequest(long workId, ByteArrayOutputStream requestStream) throws IOException {
    int encodedVersion = 1;
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(outputStream);

    Recorder.RecordingHeader recordingHeader = MockProfileComponents.getRecordingHeader(workId);
    byte[] recordingHeaderBytes = recordingHeader.toByteArray();
    codedOutputStream.writeUInt32NoTag(encodedVersion);
    codedOutputStream.writeUInt32NoTag(recordingHeaderBytes.length);
    recordingHeader.writeTo(codedOutputStream);
    codedOutputStream.flush();
    byte[] bytesWritten = outputStream.toByteArray();

    Checksum recordingHeaderChecksum = new Adler32();
    recordingHeaderChecksum.update(bytesWritten, 0, bytesWritten.length);
    long checksumValue = recordingHeaderChecksum.getValue();
    codedOutputStream.writeUInt32NoTag((int) checksumValue);
    codedOutputStream.flush();
    outputStream.writeTo(requestStream);
  }

  private static void writeMockWseEntriesToRequest(List<Recorder.Wse> wseList, ByteArrayOutputStream requestStream) throws IOException {
    for (Recorder.Wse wse : wseList) {
      writeWseToRequest(wse, requestStream);
    }
  }

  private static void writeWseToRequest(Recorder.Wse wse, ByteArrayOutputStream requestStream) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(outputStream);
    byte[] wseBytes = wse.toByteArray();

    codedOutputStream.writeUInt32NoTag(wseBytes.length);
    wse.writeTo(codedOutputStream);
    codedOutputStream.flush();
    byte[] bytesWritten = outputStream.toByteArray();

    Checksum wseChecksum = new Adler32();
    wseChecksum.update(bytesWritten, 0, bytesWritten.length);
    long checksumValue = wseChecksum.getValue();
    codedOutputStream.writeUInt32NoTag((int) checksumValue);
    codedOutputStream.flush();

    outputStream.writeTo(requestStream);
  }

  private static List<Recorder.Wse> getMockWseEntriesForSingleProfile() {
    List<Recorder.StackSample> samples = MockProfileComponents.getPredefinedStackSamples(1);
    Recorder.StackSampleWse ssw1 = Recorder.StackSampleWse.newBuilder()
        .addStackSample(samples.get(0))
        .addStackSample(samples.get(1))
        .build();
    Recorder.StackSampleWse ssw2 = Recorder.StackSampleWse.newBuilder()
        .addStackSample(samples.get(2))
        .build();

    Recorder.Wse wse1 = MockProfileComponents.getMockCpuWseWithStackSample(ssw1, null);
    Recorder.Wse wse2 = MockProfileComponents.getMockCpuWseWithStackSample(ssw2, ssw1);

    return Arrays.asList(wse1, wse2);
  }

  private static List<Recorder.Wse> getMockWseEntriesForMultipleProfiles() {
    List<Recorder.StackSample> samples = MockProfileComponents.getPredefinedStackSamples(1);
    Recorder.StackSampleWse ssw1 = Recorder.StackSampleWse.newBuilder()
        .addStackSample(samples.get(0))
        .build();
    Recorder.StackSampleWse ssw2 = Recorder.StackSampleWse.newBuilder()
        .addStackSample(samples.get(1))
        .build();
    Recorder.StackSampleWse ssw3 = Recorder.StackSampleWse.newBuilder()
        .addStackSample(samples.get(2))
        .build();

    Recorder.Wse wse1 = MockProfileComponents.getMockCpuWseWithStackSample(ssw1, null);
    Recorder.Wse wse2 = MockProfileComponents.getMockCpuWseWithStackSample(ssw2, null);
    Recorder.Wse wse3 = MockProfileComponents.getMockCpuWseWithStackSample(ssw3, null);

    return Arrays.asList(wse1, wse2, wse3);
  }
}
