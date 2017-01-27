package fk.prof;

import com.google.protobuf.CodedInputStream;
import org.junit.Test;
import recording.Recorder;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.Adler32;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @understands reading profile-file
 */
public class ProfileParsingTest {
    private static final String TMP_PROFILE_DATA = "/tmp/profile1.data";

    @Test
    public void testProfileRead() throws IOException {
        TestJni.loadJniLib();
        new TestJni().generateCpusampleSimpleProfile(TMP_PROFILE_DATA);
        FileInputStream fis = new FileInputStream(TMP_PROFILE_DATA);
        CodedInputStream is = CodedInputStream.newInstance(fis);
        assertThat(is.readUInt32(), is(1));

        int headerLen = is.readUInt32();

        int hdrLimit = is.pushLimit(headerLen);
        Recorder.RecordingHeader.Builder rhBuilder = Recorder.RecordingHeader.newBuilder();
        rhBuilder.mergeFrom(is);
        is.popLimit(hdrLimit);

        Recorder.RecordingHeader rh = rhBuilder.build();
        assertThat(rh.getRecorderVersion(), is(1));
        assertThat(rh.getControllerVersion(), is(2));
        assertThat(rh.getControllerId(), is(3));
        Recorder.WorkAssignment wa = rh.getWorkAssignment();
        assertThat(wa.getWorkId(), is(10l));
        assertThat(wa.getIssueTime(), is("2016-11-10T14:35:09.372"));
        assertThat(wa.getDuration(), is(60));
        assertThat(wa.getDelay(), is(17));
        assertThat(wa.getWorkCount(), is(1));
        Recorder.Work w = wa.getWork(0);
        assertThat(w.getWType(), is(Recorder.WorkType.cpu_sample_work));
        Recorder.CpuSampleWork csw = w.getCpuSample();
        assertThat(csw.getFrequency(), is(49));
        assertThat(csw.getMaxFrames(), is(200));

        //// Hdr len and chksum
        int bytesBeforeHdrChksum = is.getTotalBytesRead();
        int headerChksum = is.readUInt32();
        int bytesOffsetAfterHdrChksum = is.getTotalBytesRead();
        ///////////////////////

        int wse1Len = is.readUInt32();
        int wse1Lim = is.pushLimit(wse1Len);
        Recorder.Wse.Builder wseBuilder = Recorder.Wse.newBuilder();
        wseBuilder.mergeFrom(is);
        is.popLimit(wse1Lim);

        Recorder.Wse e1 = wseBuilder.build();
        assertThat(e1.hasIndexedData(), is(true));
        Recorder.IndexedData idxData = e1.getIndexedData();
        Map<Long, String> methodIdToName = new HashMap<>();
        assertMethodInfoContents(methodIdToName, idxData, 4);
        testWseContents(e1, methodIdToName, new int[]{15000, 15050}, new long[]{200l, 200l}, new int[]{401, 405}, new List[]{Arrays.asList("Y", "C", "D", "C", "D"), Arrays.asList("Y", "C", "D", "E", "C", "D")});


        //// E1 len and chksum
        int byteCountE1 = is.getTotalBytesRead() - bytesOffsetAfterHdrChksum;
        int e1Chksum = is.readUInt32();
        int bytesOffsetAfterE1Chksum = is.getTotalBytesRead();
        ///////////////////////

        int wse2Len = is.readUInt32();
        int wse2Lim = is.pushLimit(wse2Len);
        wseBuilder.clear();
        wseBuilder.mergeFrom(is);
        is.popLimit(wse2Lim);

        Recorder.Wse e2 = wseBuilder.build();
        idxData = e2.getIndexedData();
        assertMethodInfoContents(methodIdToName, idxData, 1);
        testWseContents(e2, methodIdToName, new int[]{25002}, new long[]{201l}, new int[]{802}, new List[]{Arrays.asList("Y", "C", "D", "E", "F", "C")});

        //// E2 len and chksum
        int byteCountE2 = is.getTotalBytesRead() - bytesOffsetAfterE1Chksum;
        int e2Chksum = is.readUInt32();
        ///////////////////////


        Set<String> expectedFunctions = new HashSet<>();
        expectedFunctions.add("Y");
        expectedFunctions.add("C");
        expectedFunctions.add("D");
        expectedFunctions.add("E");
        expectedFunctions.add("F");
        assertThat(new HashSet<String>(methodIdToName.values()), is(expectedFunctions));

        //////// now verify checksums
        fis.close();
        fis = new FileInputStream(TMP_PROFILE_DATA);
        is = CodedInputStream.newInstance(fis);

        byte[] bytes = is.readRawBytes(bytesBeforeHdrChksum);
        Adler32 csum = new Adler32();
        csum.reset();
        csum.update(bytes);
        assertThat((int) csum.getValue(), is(headerChksum));
        assertThat(is.readUInt32(), is(headerChksum));

        bytes = is.readRawBytes(byteCountE1);
        csum.reset();
        csum.update(bytes);
        assertThat((int) csum.getValue(), is(e1Chksum));
        assertThat(is.readUInt32(), is(e1Chksum));

        bytes = is.readRawBytes(byteCountE2);
        csum.reset();
        csum.update(bytes);
        assertThat((int) csum.getValue(), is(e2Chksum));
        assertThat(is.readUInt32(), is(e2Chksum));
        assertThat(is.isAtEnd(), is(true));
        fis.close();
    }

    private void assertMethodInfoContents(Map<Long, String> methodIdToName, Recorder.IndexedData cse, int methodInfoCount) {
        assertThat(cse.getMethodInfoCount(), is(methodInfoCount));
        for (Recorder.MethodInfo methodInfo : cse.getMethodInfoList()) {
            long methodId = methodInfo.getMethodId();
            assertThat(methodIdToName.containsKey(methodId), is(false));
            String methodName = methodInfo.getMethodName();
            methodIdToName.put(methodId, methodName);
            assertThat(methodInfo.getFileName(), is("foo/Bar.java"));
            assertThat(methodInfo.getClassFqdn(), is("foo.Bar"));
            assertThat(methodInfo.getSignature(), is("([I)I"));
        }
    }

    private void testWseContents(Recorder.Wse e, Map<Long, String> methodIdToName, final int[] startOffsets, final long[] threadIds, int[] traceIds, final List[] frames) {
        assertThat(e.getWType(), is(Recorder.WorkType.cpu_sample_work));
        Recorder.StackSampleWse cse = e.getCpuSampleEntry();
        assertThat(cse.getStackSampleCount(), is(frames.length));
        for (int i = 0; i < frames.length; i++) {
            Recorder.StackSample ss1 = cse.getStackSample(i);
            assertThat(ss1.getStartOffsetMicros(), is(startOffsets[i]));
            assertThat(ss1.getThreadId(), is(threadIds[i]));
            assertThat(ss1.getTraceId(), is(traceIds[i]));
            assertThat(ss1.getFrameCount(), is(frames[i].size()));
            List<String> callChain = new ArrayList<>();
            for (Recorder.Frame frame : ss1.getFrameList()) {
                long methodId = frame.getMethodId();
                assertThat(methodIdToName.containsKey(methodId), is(true));
                callChain.add(methodIdToName.get(methodId));
                assertThat(frame.getBci(), is((int) (17 * methodId)));
                assertThat(frame.getLineNo(), is((int) (2 * methodId)));
            }
            assertThat(callChain, is(frames[i]));
        }

    }
}
