package fk.prof.userapi.model;

import fk.prof.aggregation.proto.AggregatedProfileModel;

import java.util.List;

/**
 * @author gaurav.ashok
 */
public class AggregatedSamplesPerTraceCtx {

    private final AggregatedProfileModel.MethodLookUp methodIdlookup;
    private final AggregatedSamples aggregatedSamples;

    public AggregatedSamplesPerTraceCtx(AggregatedProfileModel.MethodLookUp methodIdlookup, AggregatedSamples aggregatedSamples) {
        this.methodIdlookup = methodIdlookup;
        this.aggregatedSamples = aggregatedSamples;
    }

    public List<String> getMethodLookup() {
        return methodIdlookup.getFqdnList();
    }

    public AggregatedSamples getAggregatedSamples() {
        return aggregatedSamples;
    }
}
