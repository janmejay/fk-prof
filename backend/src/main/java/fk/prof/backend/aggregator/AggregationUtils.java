package fk.prof.backend.aggregator;

public class AggregationUtils {

    //TODO: Once vertx is integrated, read and validate from config
    public static int getWindowDurationInMinutes() {
        final int windowDurationInMinutes = 30;
        if (windowDurationInMinutes <= 0 || windowDurationInMinutes > 60 || ((60 % windowDurationInMinutes) != 0)) {
            throw new RuntimeException("Invalid aggregation window duration");
        }
        return windowDurationInMinutes;
    }
}
