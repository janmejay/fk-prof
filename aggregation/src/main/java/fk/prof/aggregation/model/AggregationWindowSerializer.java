package fk.prof.aggregation.model;

import fk.prof.aggregation.Constants;
import fk.prof.aggregation.proto.AggregatedProfileModel;
import fk.prof.aggregation.serialize.SerializationException;
import fk.prof.aggregation.serialize.Serializer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Adler32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Checksum;

/**
 * @author gaurav.ashok
 */
public class AggregationWindowSerializer implements Serializer {

    private static final int VERSION = 1;
    private AggregatedProfileModel.WorkType workType;
    private FinalizedAggregationWindow aggregation;

    public AggregationWindowSerializer(FinalizedAggregationWindow aggregation, AggregatedProfileModel.WorkType workType) {
        this.aggregation = aggregation;
        this.workType = workType;
    }

    @Override
    public void serialize(OutputStream out) throws IOException {
        Checksum checksum = new Adler32();
        CheckedOutputStream cout = new CheckedOutputStream(out, checksum);

        Serializer.writeFixedWidthInt32(Constants.AGGREGATION_FILE_MAGIC_NUM, cout);

        // header
        Serializer.writeCheckedDelimited(aggregation.buildHeaderProto(VERSION, AggregatedProfileModel.WorkType.cpu_sample_work), cout);

        AggregatedProfileModel.TraceCtxList traces;
        switch (workType) {
            case cpu_sample_work:
                traces = aggregation.cpuSamplingAggregationBucket.buildTraceCtxListProto();
                break;
            default:
                throw new IllegalArgumentException(workType.name() + " not supported");
        }

        // traces
        Serializer.writeCheckedDelimited(traces, cout);

        // profiles summary
        Serializer.writeCheckedDelimited(aggregation.buildProfileSummaryProto(workType, traces), cout);

        switch (workType) {
            case cpu_sample_work:
                new CpuSamplingAggregatedSamplesSerializer(aggregation.cpuSamplingAggregationBucket, traces).serialize(out);
        }
    }

    private static class CpuSamplingAggregatedSamplesSerializer implements Serializer {

        private FinalizedCpuSamplingAggregationBucket cpuSamplingAggregation;
        private AggregatedProfileModel.TraceCtxList traces;

        public CpuSamplingAggregatedSamplesSerializer(FinalizedCpuSamplingAggregationBucket cpuSamplingAggregation, AggregatedProfileModel.TraceCtxList traces) {
            this.cpuSamplingAggregation = cpuSamplingAggregation;
            this.traces = traces;
        }

        @Override
        public void serialize(OutputStream out) throws IOException {

            Checksum checksum = new Adler32();
            CheckedOutputStream cout = new CheckedOutputStream(out, checksum);

            // method lookup
            Serializer.writeCheckedDelimited(cpuSamplingAggregation.methodIdLookup.buildMethodIdLookupProto(), cout);

            // stacktrace tree
            checksum.reset();
            int index = 0;
            for(AggregatedProfileModel.TraceCtxDetail trace: traces.getAllTraceCtxList()) {
                FinalizedCpuSamplingAggregationBucket.NodeVisitor visitor =
                        new FinalizedCpuSamplingAggregationBucket.NodeVisitor(cout, Constants.STACKTRACETREE_SERIAL_BATCHSIZE, index);

                try {
                    cpuSamplingAggregation.traceDetailLookup.get(trace.getName()).getGlobalRoot().traverse(visitor);
                } catch (IOException e) {
                    throw e;
                } catch (Exception e) {
                    throw new SerializationException("Unexpected error while traversing stacktrace tree", e);
                }
                visitor.end();
                ++index;
            }
            Serializer.writeFixedWidthInt32((int) checksum.getValue(), cout);
        }
    }
}
