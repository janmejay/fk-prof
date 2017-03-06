package fk.prof.recorder.utils;

import org.hamcrest.Matcher;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.Matchers.*;

public class Matchers {
    public static Matcher<Long> approximately(long base) {
        long err = base / 100;
        if (err == 0) err = 1;
        return approximately(base, err);
    }

    public static Matcher<Long> approximately(long base, long err) {
        return allOf(greaterThanOrEqualTo(base - err), lessThanOrEqualTo(base + err));
    }

    public static Matcher<Long> approximatelyBetween(long start, long end) {
        long err = start / 100;
        return allOf(greaterThanOrEqualTo(start - err), lessThanOrEqualTo(end + err));
    }
}
