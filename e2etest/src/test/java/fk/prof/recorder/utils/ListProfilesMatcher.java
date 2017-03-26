package fk.prof.recorder.utils;

import org.hamcrest.*;
import org.hamcrest.Matchers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import static fk.prof.recorder.utils.Util.*;

/**
 * Created by gaurav.ashok on 25/03/17.
 */
public class ListProfilesMatcher extends BaseMatcher<Map<String, Object>> {

    List<Matcher> matchers = new ArrayList<>();
    List<Matcher> failed = new ArrayList<>();

    public ListProfilesMatcher() {
        matchers.add(new MapFieldsMatcher("response", Function.identity(), "failed", "succeeded"));
    }

    @Override
    public boolean matches(Object item) {
        for (final Matcher matcher : matchers) {
            if (!matcher.matches(item)) {
                failed.add(matcher);
                return false;
            }
        }
        return true;
    }

    @Override
    public void describeTo(Description description) {
        description.appendList("(", " " + "and" + " ", ")", matchers);
    }

    @Override
    public void describeMismatch(final Object item, final Description description) {
        for (final Matcher matcher : failed) {
            description.appendDescriptionOf(matcher).appendText(" but ");
            matcher.describeMismatch(item, description);
        }
    }

    public ListProfilesMatcher hasAggrWindows(int count) {
        matchers.add(new ProfileCountMatcher(count, false));
        if(count > 0) {
            matchers.add(new MapFieldsMatcher("aggregation_window", (i) -> asList(get(i, "succeeded")).get(0), "profiles", "ws_summary", "traces", "start", "duration"));
        }
        return this;
    }

    public ListProfilesMatcher latestAggrWindowHasTraces(String... traces) {
        Matcher sizeMatcher = Matchers.hasItems(traces);
        Matcher itemsMatcher = Matchers.hasSize(traces.length);

        matchers.add(new GenericMatcher("latest aggrWindow trace list", res -> asList(get(getLatestWindow(res), "traces")),
                Matchers.allOf(sizeMatcher, itemsMatcher)));
        return this;
    }

    public ListProfilesMatcher latestAggrWindowHasWorkCount(int count) {
        matchers.add(new GenericMatcher("latest aggrWindow work count", res -> get(getLatestWindow(res), "profiles"),
                Matchers.hasSize(count)));
        return this;
    }

    private static class ProfileCountMatcher extends BaseMatcher<Object>{
        int len;
        boolean failed;
        String tag;

        public ProfileCountMatcher(int len, boolean failed) {
            this.len = len;
            this.failed = failed;
            this.tag = failed ? "failed" : "succeeded";
        }

        @Override
        public boolean matches(Object item) {
            return asList(get(item, tag)).size() == len;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("count of profiles in " + tag + " should be " + len);
        }
    }

    private static class MapFieldsMatcher extends BaseMatcher<Object> {
        String[] expectedFields;
        String objectTag;
        Function<Object, Object> transformer;

        public MapFieldsMatcher(String tag, Function<Object, Object> transformer, String... fields) {
            assert fields != null : "cannot create a matcher with empty fields list";

            this.expectedFields = fields;
            this.objectTag = tag;
            this.transformer = transformer;
        }

        @Override
        public boolean matches(Object item) {
            Map<String, Object> map = cast(transformer.apply(item));

            for(int i = 0; i < expectedFields.length; ++i) {
                if(!map.containsKey(expectedFields[i])) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("map \"" + objectTag + "\" should contain ");
            description.appendValueList("\"", ",", "\" fields", expectedFields);
        }
    }

    private static class GenericMatcher<T> extends BaseMatcher<T> {
        String objectTag;
        Function<Object, T> transformer;
        Matcher matcher;

        public GenericMatcher(String tag, Function<Object, T> transformer, Matcher<T> matcher) {
            this.objectTag = tag;
            this.matcher = matcher;
            this.transformer = transformer;
        }

        @Override
        public boolean matches(Object item) {
            return matcher.matches(transformer.apply(item));
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("matching \"" + objectTag + "\" with matcher describing: ");
            matcher.describeTo(description);
        }
    }
}
