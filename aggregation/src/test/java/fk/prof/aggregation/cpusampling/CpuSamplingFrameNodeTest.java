package fk.prof.aggregation.cpusampling;

import fk.prof.aggregation.cpusampling.CpuSamplingFrameNode;
import org.junit.Assert;
import org.junit.Test;

public class CpuSamplingFrameNodeTest {
  @Test
  public void testIncrementAndGetOfSamples() {
    CpuSamplingFrameNode node = new CpuSamplingFrameNode(1l, 10);

    Assert.assertEquals(0, node.getOnStackSamples());
    node.incrementOnStackSamples();
    Assert.assertEquals(1, node.getOnStackSamples());

    Assert.assertEquals(0, node.getOnCpuSamples());
    node.incrementOnCpuSamples();
    Assert.assertEquals(1, node.getOnCpuSamples());
  }

  @Test
  public void testGetOrAddBehaviorOfChildren() {
    CpuSamplingFrameNode node1 = new CpuSamplingFrameNode(1l, 10);
    Assert.assertEquals(0, node1.getChildren().size());

    CpuSamplingFrameNode node2 = node1.getOrAddChild(2l, 20);
    Assert.assertEquals(1, node1.getChildren().size());
    Assert.assertEquals(node2, node1.getChildren().get(0));

    //Different methodId, same linenumber as first child
    CpuSamplingFrameNode node3 = node1.getOrAddChild(3l, 20);
    Assert.assertEquals(2, node1.getChildren().size());
    Assert.assertEquals(node3, node1.getChildren().get(1));

    //Same methodId, different linenumber as first child
    CpuSamplingFrameNode node4 = node1.getOrAddChild(2l, 30);
    Assert.assertEquals(3, node1.getChildren().size());
    Assert.assertEquals(node4, node1.getChildren().get(2));

    //Same methodId, same linenumber as first child
    CpuSamplingFrameNode node5 = node1.getOrAddChild(2l, 20);
    Assert.assertEquals(3, node1.getChildren().size());
    Assert.assertEquals(node5, node1.getChildren().get(0));
  }
}
