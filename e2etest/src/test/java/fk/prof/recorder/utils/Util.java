package fk.prof.recorder.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.request.HttpRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.junit.Assert;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Created by gaurav.ashok on 25/03/17.
 */
public class Util {

    public static ObjectMapper OM = new ObjectMapper();
    private final static Logger logger = LoggerFactory.getLogger(Util.class);

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

    public static Map<String, Object> toMap(String str) throws Exception {
        return OM.readValue(str, Map.class);
    }

    public static List<Object> toList(String str) throws Exception {
        return OM.readValue(str, List.class);
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

    public static HttpResponse<?> doRequest(String url, String method, byte[] payload, int expectedStatusCode, boolean isReponseInBytes) throws Exception {
        HttpRequest request;

        if(method.equals("get")) {
            request = Unirest.get(url);
        }
        else if(method.equals("post")) {
            request = Unirest.post(url).body(payload).getHttpRequest();
        }
        else {
            throw new UnsupportedOperationException("other request methods not implemented");
        }

        HttpResponse<?> resp;
        if(isReponseInBytes) {
            resp = request.asBinary();
        }
        else {
            resp = request.asString();
            logger.info(resp.getBody());
        }
        assertStatusCode(resp, expectedStatusCode);

        return resp;
    }

    private static void assertStatusCode(HttpResponse<?> resp, int expectedStatusCode) {
        Assert.assertThat(resp.getStatus(), org.hamcrest.Matchers.is(expectedStatusCode));
    }
}
