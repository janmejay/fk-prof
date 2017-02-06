package fk.prof.userapi.model.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import fk.prof.aggregation.proto.AggregatedProfileModel;

import java.io.IOException;

/**
 * @author gaurav.ashok
 */
public class ProtoSerializers {

    public static void registerSerializers(ObjectMapper om) {
        SimpleModule module = new SimpleModule("protobufSerializers", new Version(1, 0, 0, null, null, null));
        module.addSerializer(AggregatedProfileModel.FrameNode.class, new FrameNodeSerializer());
        module.addSerializer(AggregatedProfileModel.CPUSamplingNodeProps.class, new CpuSampleFrameNodePropsSerializer());
        module.addSerializer(AggregatedProfileModel.Header.class, new HeaderSerializer());
        module.addSerializer(AggregatedProfileModel.ProfileSourceInfo.class, new ProfileSourceInfoSerializer());
        module.addSerializer(AggregatedProfileModel.ProfileWorkInfo.class, new ProfileWorkInfoSerializer());

        om.registerModule(module);
    }

    static class FrameNodeSerializer extends StdSerializer<AggregatedProfileModel.FrameNode> {

        public FrameNodeSerializer() {
            super(AggregatedProfileModel.FrameNode.class);
        }

        @Override
        public void serialize(AggregatedProfileModel.FrameNode value, JsonGenerator gen, SerializerProvider serializers) throws IOException, JsonProcessingException {
            gen.writeStartArray();
            gen.writeNumber(value.getMethodId());
            gen.writeNumber(value.getLineNo());
            gen.writeNumber(value.getChildCount());
            if(value.getCpuSamplingProps() != null) {
                JsonSerializer cpuSamplesPropsSerializer = serializers.findValueSerializer(AggregatedProfileModel.CPUSamplingNodeProps.class);
                cpuSamplesPropsSerializer.serialize(value.getCpuSamplingProps(), gen, serializers);
            }
            gen.writeEndArray();
        }
    }

    static class CpuSampleFrameNodePropsSerializer extends StdSerializer<AggregatedProfileModel.CPUSamplingNodeProps> {

        public CpuSampleFrameNodePropsSerializer() {
            super(AggregatedProfileModel.CPUSamplingNodeProps.class);
        }

        @Override
        public void serialize(AggregatedProfileModel.CPUSamplingNodeProps value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartArray();
            gen.writeNumber(value.getOnStackSamples());
            gen.writeNumber(value.getOnCpuSamples());
            gen.writeEndArray();
        }
    }

    static class HeaderSerializer extends StdSerializer<AggregatedProfileModel.Header> {

        public HeaderSerializer() {
            super(AggregatedProfileModel.Header.class);
        }

        @Override
        public void serialize(AggregatedProfileModel.Header header, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("appId", header.getAppId());
            gen.writeStringField("clusterId", header.getClusterId());
            gen.writeStringField("procId", header.getProcId());
            gen.writeStringField("aggregationStartTime", header.getAggregationStartTime());
            gen.writeStringField("aggregationEndTime", header.getAggregationEndTime());
            gen.writeStringField("workType", header.getWorkType().name());
            gen.writeEndObject();
        }
    }

    static class ProfileSourceInfoSerializer extends StdSerializer<AggregatedProfileModel.ProfileSourceInfo> {
        public ProfileSourceInfoSerializer() {
            super(AggregatedProfileModel.ProfileSourceInfo.class);
        }

        @Override
        public void serialize(AggregatedProfileModel.ProfileSourceInfo value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("ip", value.getIp());
            gen.writeStringField("hostname", value.getHostname());
            gen.writeStringField("processName", value.getProcessName());
            gen.writeStringField("zone", value.getZone());
            gen.writeStringField("instanceType", value.getInstanceType());
            gen.writeEndObject();
        }
    }

    static class ProfileWorkInfoSerializer extends StdSerializer<AggregatedProfileModel.ProfileWorkInfo> {
        public ProfileWorkInfoSerializer() {
            super(AggregatedProfileModel.ProfileWorkInfo.class);
        }

        @Override
        public void serialize(AggregatedProfileModel.ProfileWorkInfo value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeNumberField("startOffset", value.getStartOffset());
            gen.writeNumberField("duration", value.getDuration());
            gen.writeNumberField("recorderVersion", value.getRecorderVersion());
            gen.writeNumberField("sampleCount", value.getSampleCount());
            gen.writeStringField("status", value.getStatus().name());
            gen.writeArrayFieldStart("traceCoverageMap");
            for(AggregatedProfileModel.TraceCtxToCoveragePctMap keyValue: value.getTraceCoverageMapList()) {
                gen.writeStartArray();
                gen.writeNumber(keyValue.getTraceCtxIdx());
                gen.writeNumber(keyValue.getCoveragePct());
                gen.writeEndArray();
            }
            gen.writeEndArray();
            gen.writeEndObject();
        }
    }
}
