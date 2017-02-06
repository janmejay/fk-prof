package fk.prof.userapi.model;

import java.time.ZonedDateTime;
import java.util.Set;

/**
 * Represents profile to be returned in profile apis
 * Created by rohit.patiyal on 23/01/17.
 */
public class FilteredProfiles {
    private ZonedDateTime start;
    private ZonedDateTime end;
    private Set<String> values;

    public FilteredProfiles(ZonedDateTime start, ZonedDateTime end, Set<String> values) {
        this.start = start;
        this.end = end;
        this.values = values;
    }

    public ZonedDateTime getStart() {
        return start;
    }

    public ZonedDateTime getEnd() {
        return end;
    }

    public Set<String> getValues() {
        return values;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FilteredProfiles profile = (FilteredProfiles) o;

        if (!getStart().equals(profile.getStart())) return false;
        if (!getEnd().equals(profile.getEnd())) return false;
        return getValues().equals(profile.getValues());
    }

    @Override
    public int hashCode() {
        int result = getStart().hashCode();
        result = 31 * result + getEnd().hashCode();
        result = 31 * result + getValues().hashCode();
        return result;
    }
}
