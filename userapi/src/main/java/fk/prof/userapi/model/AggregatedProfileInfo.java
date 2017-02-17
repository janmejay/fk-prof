package fk.prof.userapi.model;

import fk.prof.aggregation.proto.AggregatedProfileModel;

import java.util.Map;

/**
 * @author gaurav.ashok
 */
public class AggregatedProfileInfo {

    private AggregatedProfileModel.Header header;
    private ScheduledProfilesSummary summary;
    private Map<String, AggregatedSamplesPerTraceCtx> aggregatedSamples;

    public AggregatedProfileInfo(AggregatedProfileModel.Header header, ScheduledProfilesSummary summary,
                                 Map<String, AggregatedSamplesPerTraceCtx> aggregatedSamples) {
        this.header = header;
        this.summary = summary;
        this.aggregatedSamples = aggregatedSamples;
    }

    public AggregatedProfileModel.Header getHeader() {
        return header;
    }

    public ScheduledProfilesSummary getProfileSummary() {
        return summary;
    }

    public AggregatedSamplesPerTraceCtx getAggregatedSamples(String traceCtxName) {
        return aggregatedSamples.get(traceCtxName);
    }
}
