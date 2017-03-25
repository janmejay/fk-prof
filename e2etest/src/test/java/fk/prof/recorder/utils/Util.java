package fk.prof.recorder.utils;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Created by gaurav.ashok on 25/03/17.
 */
public class Util {
    public static Map<String, Object> getLatestWindow(Object response) {
        List<Map<String, Object>> aggregationWindows = asList(get(response, "succeeded"));
        if(aggregationWindows == null || aggregationWindows.size() == 0) {
            return null;
        }

        ZonedDateTime[] dateTimes = new ZonedDateTime[aggregationWindows.size()];
        for(int idx = 0; idx < dateTimes.length; ++idx) {
            dateTimes[idx] = asZDate(get(aggregationWindows.get(idx), "start"));
        }

        return aggregationWindows.get(maxIdx(dateTimes));
    }

    public static <T> T get(Object m, String... fields) {
        assert fields.length > 0;
        Map<String, Object> temp = cast(m);
        int i = 0;
        for(; i < fields.length - 1; ++i) {
            temp = cast(temp.get(fields[i]));
        }

        return cast(temp.get(fields[i]));
    }

    public static <T> List<T> asList(Object obj) {
        return (List<T>) obj;
    }

    public static <T> T cast(Object obj) {
        return (T)obj;
    }

    public static ZonedDateTime asZDate(String str) {
        return ZonedDateTime.parse(str, DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }

    public static <T extends Comparable> int maxIdx(T... items) {
        assert items != null && items.length > 0 : "cannot find index for max item in empty list";

        int maxIdx = 0;

        for(int i = 1; i < items.length; ++i) {
            if(items[maxIdx].compareTo(items[i]) < 0) {
                maxIdx = i;
            }
        }

        return maxIdx;
    }
}
