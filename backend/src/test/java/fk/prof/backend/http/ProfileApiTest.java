package fk.prof.backend.http;

import com.google.common.primitives.Longs;
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

import java.util.zip.Adler32;
import java.util.zip.Checksum;

@RunWith(VertxUnitRunner.class)
public class ProfileApiTest {

    private Vertx vertx;
    private Integer port = 9300;

    @Before
    public void setUp(TestContext context) {
        vertx = Vertx.vertx();
        vertx.deployVerticle(MainVerticle.class.getName(), context.asyncAssertSuccess());
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void scratch() {
        int max = Integer.MAX_VALUE;
        long umax1 = 1l + Integer.MAX_VALUE;
        long umax = 4294967295l;
        System.out.println("Yo");
    }

    @Test
    public void testWithOnlyHeader(TestContext context) {
        final Async async = context.async();
        HttpClientRequest request = vertx.createHttpClient().post(port, "localhost", "/profile")
                .handler(response -> {
//                    context.assertEquals(response.statusCode(), 200);
                    response.bodyHandler(body -> {
                        System.out.println(body.toString());
                        async.complete();
                    });
                });
        request.setChunked(true);

        Long encodedVersion = 1l;
        Buffer buffer = Buffer.buffer();
        buffer.appendUnsignedInt(encodedVersion);
        request.write(buffer);

        Recorder.WorkAssignment workAssignment = Recorder.WorkAssignment.newBuilder()
                .addWork(
                        Recorder.Work.newBuilder()
                                .setWType(Recorder.WorkType.cpu_sample_work)
                                .setCpuSample(Recorder.CpuSampleWork.newBuilder()
                                        .setFrequency(100)
                                        .setMaxFrames(64)
                                )
                )
                .setWorkId(100l)
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

        buffer = Buffer.buffer();
        buffer.appendUnsignedInt(recordingHeaderBytes.length);
        request.write(buffer);

        buffer = Buffer.buffer();
        buffer.appendBytes(recordingHeaderBytes);
        request.write(buffer);

        Checksum recordingHeaderChecksum = new Adler32();
        recordingHeaderChecksum.update(encodedVersion.intValue());
        recordingHeaderChecksum.update(recordingHeaderBytes.length);
        recordingHeaderChecksum.update(recordingHeaderBytes, 0, recordingHeaderBytes.length);
        System.out.println("Checksum sent = " + recordingHeaderChecksum.getValue());

        buffer = Buffer.buffer();
        buffer.appendUnsignedInt(recordingHeaderChecksum.getValue());
        request.write(buffer);

        request.end();
    }
}
