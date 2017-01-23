package fk.prof.recorder.utils;

import org.hamcrest.Matcher;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

public class Matchers {
    public static Matcher<Long> approximately(long base) {
        long err = base / 100;
        return approximately(base, err);
    }

    public static Matcher<Long> approximately(long base, long err) {
        return allOf(greaterThan(base - err), lessThan(base + err));
    }

    public static Matcher<Long> approximatelyBetween(long start, long end) {
        long err = start / 100;
        return allOf(greaterThan(start - err), lessThan(end + err));
    }
}
