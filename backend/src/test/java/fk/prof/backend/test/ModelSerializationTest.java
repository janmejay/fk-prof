package fk.prof.backend.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fk.prof.WorkDetails;
import fk.prof.WorkSchedule;
import fk.prof.WorkType;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author gaurav.ashok
 */
public class ModelSerializationTest {

    private static ObjectMapper om = new ObjectMapper();
    {
        om.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        om.registerModule(new Jdk8Module());
        om.registerModule(new JavaTimeModule());
    }

    @Test
    public void testWorkDetailsWithCpuSampleSerialize() throws Exception {
        WorkDetails workDetails = new WorkDetails(WorkType.cpu_sample, new WorkDetails.CpuSamplingAttributes(100, 10));

        String serialized = om.writeValueAsString(workDetails);

        assertEquals("{\"type\":\"cpu_sample\"," +
                "\"attributes\":{\"frequency\":100,\"max_stack_depth\":10}}", serialized);
    }

    @Test
    public void testWorkDetailsWithMonitorContentionSerialize() throws Exception {
        WorkDetails workDetails = new WorkDetails(WorkType.monitor_contention, new WorkDetails.MonitorContentionAttributes(100, 10));

        String serialized = om.writeValueAsString(workDetails);

        assertEquals("{\"type\":\"monitor_contention\"," +
                "\"attributes\":{\"max_monitor_count\":100,\"max_stack_depth\":10}}", serialized);
    }

    @Test
    public void testWorkDetailsDeserialize() throws Exception {
        WorkDetails workDetails = om.readValue("{\"type\":\"monitor_contention\"," +
                "\"attributes\":{\"max_monitor_count\":100,\"max_stack_depth\":10}}", WorkDetails.class);

        assertEquals(WorkType.monitor_contention, workDetails.getType());
        assertEquals(WorkDetails.MonitorContentionAttributes.class, workDetails.getAttributes().getClass());
        WorkDetails.MonitorContentionAttributes attribs = (WorkDetails.MonitorContentionAttributes)workDetails.getAttributes();
        assertEquals(100, attribs.getMaxMonitorCount());
        assertEquals(10, attribs.getMaxStackDepth());
    }

    @Test
    public void testWorkScheduleSerialization() throws Exception {

        WorkSchedule wsch = buildWorkSchedule();

        String serialized = om.writeValueAsString(wsch);

        // assert by converting the string to generic string -> object map. cannot count on order
        Map<String, Object> obj = new ObjectMapper().readValue(serialized, Map.class);

        assertNotNull(obj);
        assertEquals(8, obj.size());
        assertKeyValue(obj, "vms_coverage", 5);
        assertKeyValue(obj, "vms_live", 10);
        assertKeyValue(obj, "profiles_discarded", 1);
        assertKeyValue(obj, "failures_observed", 3);
        assertKeyValue(obj, "effective_vmid_coverage_pct", 10.0f);
        assertKeyValue(obj, "last_scheduled", "2017-01-10T10:20:10");

        assertTrue(obj.containsKey("scheduling"));
        Map<String, Object> scheduling = (Map<String,Object>)obj.get("scheduling");
        assertKeyValue(scheduling, "duration", 60);
        assertKeyValue(scheduling, "interval", 300);
        assertKeyValue(scheduling, "after", "2017-01-10T10:10:10");
        assertKeyValue(scheduling, "vmid_coverage_pct", 25.0f);

        assertTrue(obj.containsKey("work"));
        List<Map<String, Object>> workObj = (List<Map<String, Object>>)obj.get("work");
        assertEquals(2, workObj.size());
    }

    @Test
    public void testWorkScheduleDeserialization() throws Exception {
        WorkSchedule wsch = om.readValue(workScheduleSerialized, WorkSchedule.class);

        assertNotNull(wsch);

        // assert
        WorkSchedule exWsch = buildWorkSchedule();

        assertEquals(2, wsch.getWork().size());
        assertTrue(wsch.getWork().contains(exWsch.getWork().get(0)));
        assertTrue(wsch.getWork().contains(exWsch.getWork().get(1)));

        assertEquals(5, wsch.getVmsCoverage());
        assertEquals(10, wsch.getVmsLive());
        assertEquals(1, wsch.getProfilesDiscarded());
        assertEquals(3, wsch.getFailuresObserved());
        assertEquals(10.0f, wsch.getEffectiveVmidCoveragePct(), 0.000001f);
        assertEquals(exWsch.getLastScheduled(), wsch.getLastScheduled());

        assertEquals(exWsch.getScheduling().getAfter(), wsch.getScheduling().getAfter());
        assertEquals(60, wsch.getScheduling().getDuration());
        assertEquals(300, wsch.getScheduling().getInterval());
        assertEquals(25.0f, wsch.getScheduling().getVmidCoveragePct(), 0.000001f);
    }

    private WorkSchedule buildWorkSchedule() {
        LocalDateTime someTime = LocalDateTime.of(2017, 1, 10, 10, 10, 10);
        WorkSchedule.Schedule schedule = new WorkSchedule.Schedule(60 ,300, someTime, 25.0f);
        List<WorkDetails> work = new ArrayList<>();
        work.add(new WorkDetails(WorkType.cpu_sample, new WorkDetails.CpuSamplingAttributes(100, 10)));
        work.add(new WorkDetails(WorkType.monitor_contention, new WorkDetails.MonitorContentionAttributes(100, 10)));

        WorkSchedule wsch = new WorkSchedule(schedule, work, 5, 10, 1, 3, 10.0f, someTime.plusMinutes(10));

        return wsch;
    }

    private void assertKeyValue(Map<String, Object> map, String key, Object value) {
        assertTrue(map.containsKey(key));
        if(value instanceof Float) {
            final float epsilon = 0.0000001f;
            Double d = (Double) map.get(key);
            assertEquals(d.floatValue(), ((Float) value).floatValue(), epsilon);
        } else {
            assertEquals(value, map.get(key));
        }
    }

    final String workScheduleSerialized = "{\n" +
            "  \"scheduling\": {\n" +
            "    \"duration\": 60,\n" +
            "    \"interval\": 300,\n" +
            "    \"vmid_coverage_pct\": 25.0,\n" +
            "    \"after\": \"2017-01-10T10:10:10\"\n" +
            "  },\n" +
            "  \"work\": [\n" +
            "    {\n" +
            "      \"type\": \"cpu_sample\",\n" +
            "      \"attributes\": {\n" +
            "        \"frequency\": 100,\n" +
            "        \"max_stack_depth\": 10\n" +
            "      }\n" +
            "    },\n" +
            "    {\n" +
            "      \"type\": \"monitor_contention\",\n" +
            "      \"attributes\": {\n" +
            "        \"max_monitor_count\": 100,\n" +
            "        \"max_stack_depth\": 10\n" +
            "      }\n" +
            "    }\n" +
            "  ],\n" +
            "  \"vms_coverage\": 5,\n" +
            "  \"vms_live\": 10,\n" +
            "  \"profiles_discarded\": 1,\n" +
            "  \"failures_observed\": 3,\n" +
            "  \"effective_vmid_coverage_pct\": 10.0,\n" +
            "  \"last_scheduled\": \"2017-01-10T10:20:10\"\n" +
            "}";
}
