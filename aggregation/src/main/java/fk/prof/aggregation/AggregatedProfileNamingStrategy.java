package fk.prof.aggregation;

import com.amazonaws.util.StringUtils;
import com.google.common.io.BaseEncoding;
import fk.prof.aggregation.proto.AggregatedProfileModel;
import fk.prof.storage.FileNamingStrategy;

import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * @author gaurav.ashok
 */
public class AggregatedProfileNamingStrategy implements FileNamingStrategy {

    private static final String DELIMITER = "/";
    private static final String FILE_FORMAT  = "%s/v%04d/%s/%s/%s/%s/%d/%s";

    public final String baseDir;
    public final int version;
    public final String appId;
    public final String clusterId;
    public final String procId;
    public final ZonedDateTime startTime;
    public final int duration;
    public final AggregatedProfileModel.WorkType workType;

    private final String fileNamePrefix;

    public AggregatedProfileNamingStrategy(String baseDir, int version, String appId, String clusterId, String procId, ZonedDateTime startTime, int duration, AggregatedProfileModel.WorkType workType) {
        this.baseDir = baseDir;
        this.version = version;
        this.appId = appId;
        this.clusterId = clusterId;
        this.procId = procId;
        this.startTime = startTime;
        this.duration = duration;
        this.workType = workType;

        fileNamePrefix = String.format(FILE_FORMAT, baseDir, version, encode(appId), encode(clusterId),
                procId, startTime, duration, workType.name());
    }

    public AggregatedProfileNamingStrategy(String baseDir, AggregatedProfileModel.Header header) {
        this(baseDir, header.getFormatVersion(), header.getAppId(), header.getClusterId(), header.getProcId(),
                ZonedDateTime.parse(header.getAggregationStartTime(), DateTimeFormatter.ISO_ZONED_DATE_TIME),
                getDurationFromHeader(header), header.getWorkType());
    }

    @Override
    public String getFileName(int part) {
        return fileNamePrefix + String.format("/%04d", part);
    }

    public static AggregatedProfileNamingStrategy fromFileName(String path) {
        if(StringUtils.isNullOrEmpty(path)) {
            throw new IllegalArgumentException();
        }
        String[] tokens = path.split(DELIMITER);

        return new AggregatedProfileNamingStrategy(tokens[0], Integer.parseInt(tokens[1].substring(1)), tokens[2], tokens[3], tokens[4],
                ZonedDateTime.parse(tokens[5], DateTimeFormatter.ISO_ZONED_DATE_TIME), Integer.parseInt(tokens[6]),
                AggregatedProfileModel.WorkType.valueOf(tokens[7]));
    }

    private static String encode(String str) {
        return BaseEncoding.base32().encode(str.getBytes(Charset.forName("utf-8")));
    }

    private static int getDurationFromHeader(AggregatedProfileModel.Header header) {
        ZonedDateTime startDateTime = ZonedDateTime.parse(header.getAggregationStartTime(), DateTimeFormatter.ISO_ZONED_DATE_TIME);
        ZonedDateTime endDateTime = ZonedDateTime.parse(header.getAggregationEndTime(), DateTimeFormatter.ISO_ZONED_DATE_TIME);
        return (int)startDateTime.until(endDateTime, ChronoUnit.SECONDS);
    }
}
