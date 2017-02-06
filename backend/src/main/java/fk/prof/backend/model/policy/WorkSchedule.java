package fk.prof.backend.model.policy;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Describes a scheduled policy.
 * Contains info about the
 * * rate at which samples for provided {@link WorkDetails} will be collected.
 * * coverage; % of total running process for which the recorder will collect samples.
 *
 * @author gaurav.ashok
 */
public class WorkSchedule {

    private Schedule scheduling;
    private List<WorkDetails> work;
    private int vmsCoverage;
    private int vmsLive;
    private int profilesDiscarded;
    private int failuresObserved;
    private float effectiveVmidCoveragePct;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastScheduled;

    /**
     * Default constructor for jackson
     */
    public WorkSchedule() {
    }

    public WorkSchedule(Schedule scheduling, List<WorkDetails> work, int vmsCoverage, int vmsLive, int profilesDiscarded,
                        int failuresObserved, float effectiveVmidCoveragePct, LocalDateTime lastScheduled) {
        this.scheduling = scheduling;
        this.work = work;
        this.vmsCoverage = vmsCoverage;
        this.vmsLive = vmsLive;
        this.profilesDiscarded = profilesDiscarded;
        this.failuresObserved = failuresObserved;
        this.effectiveVmidCoveragePct = effectiveVmidCoveragePct;
        this.lastScheduled = lastScheduled;
    }

    public Schedule getScheduling() {
        return scheduling;
    }

    public List<WorkDetails> getWork() {
        return work;
    }

    public int getVmsCoverage() {
        return vmsCoverage;
    }

    public int getVmsLive() {
        return vmsLive;
    }

    public int getProfilesDiscarded() {
        return profilesDiscarded;
    }

    public int getFailuresObserved() {
        return failuresObserved;
    }

    public float getEffectiveVmidCoveragePct() {
        return effectiveVmidCoveragePct;
    }

    public LocalDateTime getLastScheduled() {
        return lastScheduled;
    }

    public static class Schedule {
        private int duration;
        private int interval;
        private float vmidCoveragePct;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime after;

        /**
         * Default constructor for jackson
         */
        public Schedule() {
        }

        public Schedule(int duration, int interval, LocalDateTime after, float vmidCoveragePct) {
            this.duration = duration;
            this.interval = interval;
            this.after = after;
            this.vmidCoveragePct = vmidCoveragePct;
        }

        public int getDuration() {
            return duration;
        }

        public int getInterval() {
            return interval;
        }

        public LocalDateTime getAfter() {
            return after;
        }

        public float getVmidCoveragePct() {
            return vmidCoveragePct;
        }
    }
}
