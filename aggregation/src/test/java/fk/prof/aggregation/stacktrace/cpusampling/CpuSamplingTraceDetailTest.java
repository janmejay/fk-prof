package fk.prof.aggregation.stacktrace.cpusampling;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class CpuSamplingTraceDetailTest {
  @Test
  public void testUnclassifiableRootIsChildOfGlobalRoot() {
    CpuSamplingTraceDetail traceDetail = new CpuSamplingTraceDetail();
    List<CpuSamplingFrameNode> childrenOfGlobalRoot = traceDetail.getGlobalRoot().getChildren();
    Assert.assertNotNull(traceDetail.getUnclassifiableRoot());
    Assert.assertTrue(childrenOfGlobalRoot.contains(traceDetail.getUnclassifiableRoot()));
  }

  @Test
  public void testSampleIncrementAndGetMethods() {
    CpuSamplingTraceDetail traceDetail = new CpuSamplingTraceDetail();
    Assert.assertEquals(0, traceDetail.getSamples());
    traceDetail.incrementSamples();
    traceDetail.incrementSamples();
    Assert.assertEquals(2, traceDetail.getSamples());
  }
}
