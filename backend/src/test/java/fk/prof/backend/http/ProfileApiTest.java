package fk.prof.backend.http;

import com.google.common.primitives.UnsignedInts;
import com.google.protobuf.CodedOutputStream;
import fk.prof.backend.Utils;
import fk.prof.backend.service.IProfileWorkService;
import fk.prof.backend.service.DummyProfileWorkService;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.joda.time.LocalDateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import recording.Recorder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

@RunWith(VertxUnitRunner.class)
public class ProfileApiTest {

    private Vertx vertx;
    private Integer port = 9300;
    private IProfileWorkService profileWorkService;

    @Before
    public void setUp(TestContext context) {
        vertx = Vertx.vertx();
        profileWorkService = new DummyProfileWorkService();
        Verticle mainVerticle = new MainVerticle(profileWorkService);
        DeploymentOptions deploymentOptions = new DeploymentOptions().setInstances(1);
        vertx.deployVerticle(mainVerticle, deploymentOptions, context.asyncAssertSuccess());
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void testWithOnlyHeader(TestContext context) throws IOException {
        final Async async = context.async();
        HttpClientRequest request = vertx.createHttpClient().post(port, "localhost", "/profile")
                .handler(response -> {
                    response.bodyHandler(body -> {
                        System.out.println(body.toString());
                        context.assertEquals(response.statusCode(), 200);
                        async.complete();
                    });
                });
        request.setChunked(true);

        int encodedVersion = 1;
        Recorder.WorkAssignment workAssignment = Recorder.WorkAssignment.newBuilder()
                .addWork(
                        Recorder.Work.newBuilder()
                                .setWType(Recorder.WorkType.cpu_sample_work)
                                .setCpuSample(Recorder.CpuSampleWork.newBuilder()
                                        .setFrequency(100)
                                        .setMaxFrames(64)
                                )
                )
                .setWorkId(1l)
                .setIssueTime(LocalDateTime.now().toString())
                .setDelay(180)
                .setDuration(60)
                .build();
        Recorder.RecordingHeader recordingHeader = Recorder.RecordingHeader.newBuilder()
                .setRecorderVersion(1)
                .setControllerVersion(2)
                .setControllerId(3)
                .setWorkAssignment(workAssignment)
                .setWorkDescription("Test Work")
                .build();

        byte[] recordingHeaderBytes = recordingHeader.toByteArray();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(outputStream);
        codedOutputStream.writeUInt32NoTag(encodedVersion);
        codedOutputStream.writeUInt32NoTag(recordingHeaderBytes.length);
        recordingHeader.writeTo(codedOutputStream);
        codedOutputStream.flush();
        byte[] bytesWritten = outputStream.toByteArray();

        Checksum recordingHeaderChecksum = new Adler32();
        recordingHeaderChecksum.update(bytesWritten, 0, bytesWritten.length);
        long checksumValue = recordingHeaderChecksum.getValue();
        codedOutputStream.writeUInt32NoTag((int)checksumValue);
        codedOutputStream.flush();
        bytesWritten = outputStream.toByteArray();

        request.write(Buffer.buffer(Arrays.copyOfRange(bytesWritten, 0, 10)));
        try { Thread.sleep(50); } catch (Exception ex) {}
        request.write(Buffer.buffer(Arrays.copyOfRange(bytesWritten, 10, 40)));
        try { Thread.sleep(50); } catch (Exception ex) {}
        request.write(Buffer.buffer(Arrays.copyOfRange(bytesWritten, 40, 65)));
        try { Thread.sleep(50); } catch (Exception ex) {}
        request.write(Buffer.buffer(Arrays.copyOfRange(bytesWritten, 65, bytesWritten.length)));
        try { Thread.sleep(50); } catch (Exception ex) {}

        request.end();
    }
}
