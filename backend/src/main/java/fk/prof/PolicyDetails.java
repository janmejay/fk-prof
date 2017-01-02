package fk.prof;

import java.time.LocalDateTime;

/**
 * Policy describing scheduling details.
 *
 * @author gaurav.ashok
 */
public class PolicyDetails {

    private WorkSchedule policy;
    private String administrator;
    private LocalDateTime modifiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime lastScheduled;

    /**
     * Default constructor for jackson
     */
    public PolicyDetails() {
    }

    public PolicyDetails(WorkSchedule policy, String administrator, LocalDateTime modifiedAt, LocalDateTime createdAt, LocalDateTime lastScheduled) {
        this.policy = policy;
        this.administrator = administrator;
        this.modifiedAt = modifiedAt;
        this.createdAt = createdAt;
        this.lastScheduled = lastScheduled;
    }

    public WorkSchedule getPolicy() {
        return policy;
    }

    public String getAdministrator() {
        return administrator;
    }

    public LocalDateTime getModifiedAt() {
        return modifiedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastScheduled() {
        return lastScheduled;
    }
}
