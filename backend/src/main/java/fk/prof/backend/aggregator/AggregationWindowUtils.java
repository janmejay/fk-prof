package fk.prof.backend.aggregator;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import fk.prof.common.Utils;

import java.time.LocalDateTime;

public class AggregationWindowUtils {
//    public static LocalDateTime getWindowStart(String appId, String clusterId, String procId, LocalDateTime sampleTime) {
//        int minuteOffset = getMinuteOffset(appId, clusterId, procId);
//        int windowDurationInMinutes = getWindowDurationInMinutes();
//
//        int hourBucket = (sampleTime.getMinute() % windowDurationInMinutes) * windowDurationInMinutes;
//    }

    //TODO: Once vertx is integrated, read and validate from config
    public static int getWindowDurationInMinutes() {
        final int windowDurationInMinutes = 30;
        if (windowDurationInMinutes <= 0 || windowDurationInMinutes > 60 || ((60 % windowDurationInMinutes) != 0)) {
            throw new RuntimeException("Invalid aggregation window duration");
        }
        return windowDurationInMinutes;
    }

    public static int getMinuteOffset(String appId, String clusterId, String procId) {
        return Hashing.consistentHash(
                Utils.getHashFunctionForMurmur3_32().newHasher()
                        .putString(appId, Charsets.UTF_8)
                        .putString(clusterId, Charsets.UTF_8)
                        .putString(procId, Charsets.UTF_8)
                        .hash(),
                getWindowDurationInMinutes());
    }
}
