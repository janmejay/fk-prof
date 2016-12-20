package com.insightfullogic.honest_profiler.parser;

import com.google.protobuf.CodedInputStream;
import com.insightfullogic.honest_profiler.testing_utilities.TestJni;
import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import com.insightfullogic.lambdabehave.expectations.Expect;
import org.junit.runner.RunWith;
import recording.Recorder;

import java.io.FileInputStream;
import java.util.*;
import java.util.zip.Adler32;

import static com.insightfullogic.lambdabehave.Suite.describe;

/**
 * @understands reading profile-file
 */
@RunWith(JunitSuiteRunner.class)
public class ProfileParsingTest {
    private static final String TMP_PROFILE_DATA = "/tmp/profile1.data";

    {
        describe("Encoded profile", it -> {

            it.should("be readable with checksum verification", expect -> {
                TestJni.loadJniLib();
                new TestJni().generateCpusampleSimpleProfile(TMP_PROFILE_DATA);
                FileInputStream fis = new FileInputStream(TMP_PROFILE_DATA);
                CodedInputStream is = CodedInputStream.newInstance(fis);
                expect.that(is.readUInt32()).is(1);

                int headerLen = is.readUInt32();

                int hdrLimit = is.pushLimit(headerLen);
                Recorder.RecordingHeader.Builder rhBuilder = Recorder.RecordingHeader.newBuilder();
                rhBuilder.mergeFrom(is);
                is.popLimit(hdrLimit);

                Recorder.RecordingHeader rh = rhBuilder.build();
                expect.that(rh.getRecorderVersion()).is(1);
                expect.that(rh.getControllerVersion()).is(2);
                expect.that(rh.getControllerId()).is(3);
                expect.that(rh.getWorkDescription()).is("Test cpu-sampling work");
                Recorder.WorkAssignment wa = rh.getWorkAssignment();
                expect.that(wa.getWorkId()).is(10l);
                expect.that(wa.getIssueTime()).is("2016-11-10T14:35:09.372");
                expect.that(wa.getDuration()).is(60);
                expect.that(wa.getDelay()).is(17);
                expect.that(wa.getWorkCount()).is(1);
                Recorder.Work w = wa.getWork(0);
                expect.that(w.getWType()).is(Recorder.WorkType.cpu_sample_work);
                Recorder.CpuSampleWork csw = w.getCpuSample();
                expect.that(csw.getFrequency()).is(49);
                expect.that(csw.getMaxFrames()).is(200);

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
                expect.that(e1.hasIndexedData()).is(true);
                Recorder.IndexedData idxData = e1.getIndexedData();
                Map<Long, String> methodIdToName = new HashMap<>();
                assertMethodInfoContents(expect, methodIdToName, idxData, 4);
                testWseContents(expect, e1, methodIdToName, new int[]{15000, 15050}, new long[]{200l, 200l}, new List[]{Arrays.asList("Y", "C", "D", "C", "D"), Arrays.asList("Y", "C", "D", "E", "C", "D")});


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
                assertMethodInfoContents(expect, methodIdToName, idxData, 1);
                testWseContents(expect, e2, methodIdToName, new int[]{25002}, new long[]{201l}, new List[]{Arrays.asList("Y", "C", "D", "E", "F", "C")});

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
                expect.that(new HashSet<String>(methodIdToName.values())).is(expectedFunctions);

                //////// now verify checksums
                fis.close();
                fis = new FileInputStream(TMP_PROFILE_DATA);
                is = CodedInputStream.newInstance(fis);

                byte[] bytes = is.readRawBytes(bytesBeforeHdrChksum);
                Adler32 csum = new Adler32();
                csum.reset();
                csum.update(bytes);
                expect.that((int) csum.getValue()).is(headerChksum);
                expect.that(is.readUInt32()).is(headerChksum);

                bytes = is.readRawBytes(byteCountE1);
                csum.reset();
                csum.update(bytes);
                expect.that((int) csum.getValue()).is(e1Chksum);
                expect.that(is.readUInt32()).is(e1Chksum);

                bytes = is.readRawBytes(byteCountE2);
                csum.reset();
                csum.update(bytes);
                expect.that((int) csum.getValue()).is(e2Chksum);
                expect.that(is.readUInt32()).is(e2Chksum);
                expect.that(is.isAtEnd()).is(true);
                fis.close();
            });
        });
    }

    private void assertMethodInfoContents(Expect expect, Map<Long, String> methodIdToName, Recorder.IndexedData cse, int methodInfoCount) {
        expect.that(cse.getMethodInfoCount()).is(methodInfoCount);
        for (Recorder.MethodInfo methodInfo : cse.getMethodInfoList()) {
            long methodId = methodInfo.getMethodId();
            expect.that(methodIdToName.containsKey(methodId)).is(false);
            String methodName = methodInfo.getMethodName();
            methodIdToName.put(methodId, methodName);
            expect.that(methodInfo.getFileName()).is("foo/Bar.java");
            expect.that(methodInfo.getClassFqdn()).is("foo.Bar");
            expect.that(methodInfo.getSignature()).is("([I)I");
        }
    }

    private void testWseContents(Expect expect, Recorder.Wse e, Map<Long, String> methodIdToName, final int[] startOffsets, final long[] threadIds, final List[] frames) {
        expect.that(e.getWType()).is(Recorder.WorkType.cpu_sample_work);
        Recorder.StackSampleWse cse = e.getCpuSampleEntry();
        expect.that(cse.getStackSampleCount()).is(frames.length);
        for (int i = 0; i < frames.length; i++) {
            Recorder.StackSample ss1 = cse.getStackSample(i);
            expect.that(ss1.getStartOffsetMicros()).is(startOffsets[i]);
            expect.that(ss1.getThreadId()).is(threadIds[i]);
            expect.that(ss1.getFrameCount()).is(frames[i].size());
            List<String> callChain = new ArrayList<>();
            for (Recorder.Frame frame : ss1.getFrameList()) {
                long methodId = frame.getMethodId();
                expect.that(methodIdToName.containsKey(methodId)).is(true);
                callChain.add(methodIdToName.get(methodId));
                expect.that(frame.getBci()).is((int) (17 * methodId));
                expect.that(frame.getLineNo()).is((int) (2 * methodId));
            }
            expect.that(callChain).is(frames[i]);
        }

    }
}
