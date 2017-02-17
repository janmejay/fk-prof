package fk.prof.aggregation;

import fk.prof.aggregation.model.MethodIdLookup;
import org.junit.Assert;
import org.junit.Test;


public class MethodIdLookupTest {

  @Test
  public void testGetAndAdd() {
    MethodIdLookup methodIdLookup = new MethodIdLookup();
    int methodId1 = methodIdLookup.getOrAdd("alpha");
    int methodId2 = methodIdLookup.getOrAdd("beta");
    int methodId3 = methodIdLookup.getOrAdd("alpha");
    Assert.assertEquals(2, methodId1);
    Assert.assertEquals(3, methodId2);
    Assert.assertEquals(methodId1, methodId3);
  }

  @Test
  public void testReverseLookupAndReservedMethodIds() {
    MethodIdLookup methodIdLookup = new MethodIdLookup();
    int methodId1 = methodIdLookup.getOrAdd("alpha");
    int methodId2 = methodIdLookup.getOrAdd("beta");

    String[] reverseLookup = methodIdLookup.generateReverseLookup();
    Assert.assertEquals(4, reverseLookup.length);

    Assert.assertEquals("alpha", reverseLookup[methodId1]);
    Assert.assertEquals("beta", reverseLookup[methodId2]);

    Assert.assertEquals(MethodIdLookup.GLOBAL_ROOT_METHOD_SIGNATURE, reverseLookup[MethodIdLookup.GLOBAL_ROOT_METHOD_ID]);
    Assert.assertEquals(MethodIdLookup.UNCLASSIFIABLE_ROOT_METHOD_SIGNATURE, reverseLookup[MethodIdLookup.UNCLASSIFIABLE_ROOT_METHOD_ID]);
  }

}
