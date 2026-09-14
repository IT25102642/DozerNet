package com.dozernet.module4_operator.entity;

/**
 * Progress of an operator's assigned job.
 */
public enum JobStatus {
    ASSIGNED("Assigned"),
    IN_PROGRESS("In progress"),
    COMPLETED("Completed");

    private final String displayName;

    JobStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
