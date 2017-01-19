package fk.prof.aggregation.cpusampling;

import fk.prof.aggregation.MethodIdLookup;
import fk.prof.aggregation.cpusampling.CpuSamplingFrameNode;
import fk.prof.aggregation.cpusampling.CpuSamplingTraceDetail;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class CpuSamplingTraceDetailTest {
  @Test
  public void testUnclassifiableRootIsChildOfGlobalRoot() {
    CpuSamplingTraceDetail traceDetail = new CpuSamplingTraceDetail();
    CpuSamplingFrameNode existingGlobalRoot = traceDetail.getGlobalRoot();
    CpuSamplingFrameNode existingUnclassifiableRoot = traceDetail.getUnclassifiableRoot();
    CpuSamplingFrameNode addedUnclassifiableRoot = existingGlobalRoot.getOrAddChild(MethodIdLookup.UNCLASSIFIABLE_ROOT_METHOD_ID, MethodIdLookup.DEFAULT_LINE_NUMBER);
    // Verify that the node returned when adding unclassifiable root as child is the same node instance as returned by tracedetail object
    Assert.assertTrue(existingUnclassifiableRoot == addedUnclassifiableRoot);
  }

  @Test
  public void testSampleIncrementAndGetMethods() {
//    CpuSamplingTraceDetail traceDetail = new CpuSamplingTraceDetail();
//    Assert.assertEquals(0, traceDetail.getSamples());
//    traceDetail.incrementSamples();
//    traceDetail.incrementSamples();
//    Assert.assertEquals(2, traceDetail.getSamples());
  }
}
