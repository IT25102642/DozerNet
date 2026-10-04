package com.dozernet.module3_fleet.entity;

/**
 * Global availability gate for a machine. A machine is only bookable when it is
 * AVAILABLE (and verified). MAINTENANCE is set by the Maintenance module to
 * pull unsafe/overdue machines out of the booking pool.
 */
public enum MachineStatus {
    AVAILABLE("Available"),
    UNAVAILABLE("Unavailable"),
    MAINTENANCE("Under Maintenance");

    private final String displayName;

    MachineStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
