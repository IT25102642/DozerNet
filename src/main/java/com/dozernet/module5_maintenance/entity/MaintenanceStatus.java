package com.dozernet.module5_maintenance.entity;

/**
 * Whether a maintenance job is upcoming or finished.
 */
public enum MaintenanceStatus {
    SCHEDULED("Scheduled"),
    COMPLETED("Completed");

    private final String displayName;

    MaintenanceStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
