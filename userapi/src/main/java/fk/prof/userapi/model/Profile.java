package fk.prof.userapi.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents profile to be returned in profile apis
 * Created by rohit.patiyal on 23/01/17.
 */
public class Profile {
    private String start;
    private String end;
    private Set<String> values;

    public Profile(String start, String end) {
        this.start = start;
        this.end = end;
        this.values = new HashSet<>();
    }

    public String getStart() {
        return start;
    }

    public String getEnd() {
        return end;
    }

    public Set<String> getValues() {
        return values;
    }

    public void setValues(Set<String> values) {
        this.values = values;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Profile profile = (Profile) o;

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
