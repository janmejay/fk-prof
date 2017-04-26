package fk.prof.userapi.model;

import fk.prof.aggregation.proto.AggregatedProfileModel;
import fk.prof.userapi.util.StackLineParser;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author gaurav.ashok
 */
public class AggregatedSamplesPerTraceCtx {

    private final AggregatedSamples aggregatedSamples;
    private final List<String> methodIdLookup;

    public AggregatedSamplesPerTraceCtx(AggregatedProfileModel.MethodLookUp methodIdlookup, AggregatedSamples aggregatedSamples) {
        this.aggregatedSamples = aggregatedSamples;
        this.methodIdLookup = methodIdlookup.getFqdnList().stream().map(StackLineParser::convertSignToJava).collect(Collectors.toList());
    }

    public AggregatedSamples getAggregatedSamples() {
        return aggregatedSamples;
    }

    public List<String> getMethodIdLookup() {
        return methodIdLookup;
    }
}
