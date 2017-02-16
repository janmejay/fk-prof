package fk.prof.aggregation.model;

import org.junit.Assert;
import org.junit.Test;

public class CpuSamplingFrameNodeTest {
  @Test
  public void testEqualityOfNodesBasedOnMethodIdAndLineNumber() {
    CpuSamplingFrameNode n1 = new CpuSamplingFrameNode(1, 10);
    CpuSamplingFrameNode n2 = new CpuSamplingFrameNode(1, 10);
    Assert.assertTrue(n1.equals(n2));

    CpuSamplingFrameNode n3 = new CpuSamplingFrameNode(1, 20);
    Assert.assertNotEquals(n1, n3);

    CpuSamplingFrameNode n4 = new CpuSamplingFrameNode(2, 10);
    Assert.assertNotEquals(n1, n4);
    Assert.assertNotEquals(n3, n4);
  }

  @Test
  public void testGetAndAddOfChildUsingMethodIdAndLineNumber() {
    CpuSamplingFrameNode n1 = new CpuSamplingFrameNode(1, 10);
    CpuSamplingFrameNode n2 = new CpuSamplingFrameNode(2, 20);
    CpuSamplingFrameNode n3 = n1.getOrAddChild(2, 20);
    Assert.assertEquals(n2, n3);
    //n2 and n3 are different objects, so == comparison should return false
    Assert.assertFalse(n2 == n3);

    CpuSamplingFrameNode n4 = n1.getOrAddChild(2, 20);
    //n4 is the same object as n3, so == comparison should return true
    Assert.assertTrue(n3 == n4);
  }

  //TODO: Tests for increment of on-stack and on-cpu samples should be added once serialization is implemented
}
