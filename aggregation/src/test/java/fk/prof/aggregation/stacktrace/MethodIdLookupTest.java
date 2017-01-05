package fk.prof.aggregation.stacktrace;

import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class MethodIdLookupTest {

  @Test
  public void testGetAndAdd() {
    MethodIdLookup methodIdLookup = new MethodIdLookup();
    long methodId1 = methodIdLookup.getOrAdd("alpha");
    long methodId2 = methodIdLookup.getOrAdd("beta");
    long methodId3 = methodIdLookup.getOrAdd("alpha");
    Assert.assertEquals(1, methodId1);
    Assert.assertEquals(2, methodId2);
    Assert.assertEquals(methodId1, methodId3);
  }

  @Test
  public void testReverseLookupAndReservedMethodIds() {
    MethodIdLookup methodIdLookup = new MethodIdLookup();
    long methodId1 = methodIdLookup.getOrAdd("alpha");
    long methodId2 = methodIdLookup.getOrAdd("beta");
    Map<Long, String> reverseLookup = methodIdLookup.generateReverseLookup();
    Assert.assertEquals("alpha", reverseLookup.get(methodId1));
    Assert.assertEquals("beta", reverseLookup.get(methodId2));
    Assert.assertEquals(4, reverseLookup.size());
    Assert.assertEquals(MethodIdLookup.GLOBAL_ROOT_METHOD_SIGNATURE, reverseLookup.get(MethodIdLookup.GLOBAL_ROOT_METHOD_ID));
    Assert.assertEquals(MethodIdLookup.UNCLASSIFIABLE_ROOT_METHOD_SIGNATURE, reverseLookup.get(MethodIdLookup.UNCLASSIFIABLE_ROOT_METHOD_ID));
    Assert.assertFalse(reverseLookup.containsKey(0l));
  }

}
