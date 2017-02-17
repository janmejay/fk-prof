package fk.prof.backend.model.policy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import recording.Recorder;

/**
 * Describes what type of samples need to be collected when profiling.
 *
 * @author gaurav.ashok
 */
public class WorkDetails {

    private WorkType type;

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
            property = "type"
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = CpuSamplingAttributes.class, name = "cpu_sample"),
            @JsonSubTypes.Type(value = MonitorContentionAttributes.class, name = "monitor_contention")
    })
    private Attributes attributes;

    /**
     * Default constructor for jackson
     */
    public WorkDetails() {
    }

    public WorkDetails(WorkType type, Attributes attributes) {
        this.type = type;
        this.attributes = attributes;
    }

    public WorkDetails(Recorder.WorkType type, Attributes attributes) {
        this.type = toWorkType(type);
        if(this.type == null) {
            throw new IllegalArgumentException("invalid " + type.getDescriptorForType().getName());
        }
        this.attributes = attributes;
    }

    public WorkType getType() {
        return type;
    }

    public Attributes getAttributes() {
        return attributes;
    }

    @Override
    public int hashCode() {
        return type.hashCode() * 31 + attributes.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null || !(obj instanceof WorkDetails)) {
            return false;
        }
        WorkDetails that = (WorkDetails) obj;
        return type.equals(that.type) && attributes.equals(that.attributes);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("{\"type\":\"");
        builder.append(type);
        builder.append("\",\"attributes\":\"");
        builder.append(attributes.toString());
        builder.append("\"");
        return builder.toString();
    }

    public static WorkType toWorkType(Recorder.WorkType workType) {
        switch (workType) {
            case cpu_sample_work: return WorkType.cpu_sample;
            case monitor_contention_work: return WorkType.monitor_contention;
            case monitor_wait_work: return WorkType.monitor_wait;
            case thread_sample_work: return WorkType.thread_sample;
            default: return null;
        }
    }

    public static interface Attributes {
    }

    public static class CpuSamplingAttributes implements Attributes {
        private int frequency; // in Hz
        private int maxStackDepth;

        /**
         * Default constructor for jackson
         */
        public CpuSamplingAttributes() {
        }

        public CpuSamplingAttributes(int frequency, int maxStackDepth) {
            this.frequency = frequency;
            this.maxStackDepth = maxStackDepth;
        }

        public int getFrequency() {
            return frequency;
        }

        public int getMaxStackDepth() {
            return maxStackDepth;
        }

        @Override
        public int hashCode() {
            return (frequency << 16) + (maxStackDepth);
        }

        @Override
        public boolean equals(Object obj) {
            if(obj == null || !(obj instanceof CpuSamplingAttributes)) {
                return false;
            }
            CpuSamplingAttributes that = (CpuSamplingAttributes)obj;
            return maxStackDepth == that.maxStackDepth && frequency == that.frequency;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("{\"frequency\":");
            builder.append(frequency);
            builder.append(",\"max_stack_depth\":");
            builder.append(maxStackDepth);
            return builder.append("}").toString();
        }
    }

    public static class MonitorContentionAttributes implements Attributes {
        private int maxMonitorCount;
        private int maxStackDepth;

        /**
         * Default constructor for jackson
         */
        public MonitorContentionAttributes() {
        }

        public MonitorContentionAttributes(int maxMonitorCount, int maxStackDepth) {
            this.maxMonitorCount = maxMonitorCount;
            this.maxStackDepth = maxStackDepth;
        }

        public int getMaxMonitorCount() {
            return maxMonitorCount;
        }

        public int getMaxStackDepth() {
            return maxStackDepth;
        }

        @Override
        public int hashCode() {
            return (maxMonitorCount << 16) + maxStackDepth;
        }

        @Override
        public boolean equals(Object obj) {
            if(obj == null || !(obj instanceof MonitorContentionAttributes)) {
                return false;
            }
            MonitorContentionAttributes that = (MonitorContentionAttributes)obj;

            return maxStackDepth == that.maxStackDepth && maxMonitorCount == that.maxMonitorCount;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("{\"max_monitor_count\":");
            builder.append(maxMonitorCount);
            builder.append(",\"max_stack_depth\":");
            builder.append(maxStackDepth);
            return builder.append("}").toString();
        }
    }
}
