package fk.prof.userapi.model;

import fk.prof.aggregation.proto.AggregatedProfileModel;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author gaurav.ashok
 */
public class AggregatedProfileInfo {

    private final AggregatedProfileModel.Header header;
    private final AggregatedProfileModel.TraceCtxNames traceNames;
    private final AggregatedProfileModel.TraceCtxDetailList traceCtxDetailList;
    private final AggregatedProfileModel.RecorderList recorders;
    private final List<AggregatedProfileModel.ProfileWorkInfo> profiles;

    private final Map<String, AggregatedSamplesPerTraceCtx> aggregatedSamples;

    public AggregatedProfileInfo(AggregatedProfileModel.Header header, AggregatedProfileModel.TraceCtxNames traceNames,
                                 AggregatedProfileModel.TraceCtxDetailList traceCtxDetailList,
                                 AggregatedProfileModel.RecorderList recorders,
                                 List<AggregatedProfileModel.ProfileWorkInfo> profiles,
                                 Map<String, AggregatedSamplesPerTraceCtx> aggregatedSamples) {
        this.header = header;
        this.traceNames = traceNames;
        this.traceCtxDetailList = traceCtxDetailList;
        this.recorders = recorders;
        this.profiles = profiles;
        this.aggregatedSamples = aggregatedSamples;
    }

    public ZonedDateTime getStart() {
        return ZonedDateTime.parse(header.getAggregationStartTime(), DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }

    public int getDuration() {
        ZonedDateTime start = getStart();
        ZonedDateTime end = ZonedDateTime.parse(header.getAggregationEndTime(), DateTimeFormatter.ISO_ZONED_DATE_TIME);

        return (int)start.until(end, ChronoUnit.SECONDS);
    }

    public Iterable<String> getTraces() {
        return traceNames.getNameList();
    }

    public Iterable<AggregatedProfileModel.TraceCtxDetail> getTraceDetails() {
        return traceCtxDetailList.getTraceCtxList();
    }

    public Iterable<AggregatedProfileModel.RecorderInfo> getRecorders() {
        return recorders.getRecordersList();
    }

    public Iterable<AggregatedProfileModel.ProfileWorkInfo> getProfiles() {
        return profiles;
    }

    public AggregatedSamplesPerTraceCtx getAggregatedSamples(String traceCtxName) {
        return aggregatedSamples.get(traceCtxName);
    }
}
