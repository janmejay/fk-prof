package fk.prof.userapi.model;

import fk.prof.aggregation.proto.AggregatedProfileModel;

/**
 * @author gaurav.ashok
 */
public class ScheduledProfilesSummary {

    private AggregatedProfileModel.TraceCtxList traceCtxList;
    private AggregatedProfileModel.ProfilesSummary profileSummary;

    public ScheduledProfilesSummary(AggregatedProfileModel.TraceCtxList traceCtxList, AggregatedProfileModel.ProfilesSummary summary) {
        this.traceCtxList = traceCtxList;
        this.profileSummary = summary;
    }

    public Iterable<AggregatedProfileModel.TraceCtxDetail> getTraces() {
        return traceCtxList.getAllTraceCtxList();
    }

    public Iterable<AggregatedProfileModel.PerSourceProfileSummary> getProfilesSummary() {
        return profileSummary.getProfilesList();
    }
}
