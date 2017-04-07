package fk.prof.metrics;

import org.junit.Assert;
import org.junit.Test;

public class MetricsTest {
  @Test
  public void testLoadOfMetricNameClassToEnsureNonDuplicatesOfMetricNames() {
    // This statement will cause class loader to load metricname enum if not already loaded.
    // Static constructor is invoked which will throw error if duplicate metrics are configured
    MetricName[] values = MetricName.values();
    Assert.assertTrue(true);
  }

  @Test
  public void testTagEncodingWithCommonNonSpecialChars() {
    String actual = new ProcessGroupTag("a.b", "c_d", "e-f").toString();
    String expected = "pgt.a.2Eb_c.5Fd_e.2Df";
    Assert.assertEquals(expected, actual);
  }
}
