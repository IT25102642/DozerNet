package com.dozernet.module2_booking.entity;

/**
 * Who is answerable for damage found when a machine comes back from a job.
 * Recording this at handover is what stops the dispute later.
 */
public enum ResponsibleParty {

    NONE("No damage / not applicable"),
    CUSTOMER("Customer"),
    OPERATOR("Operator"),
    COMPANY("DozerNet / owner");

    private final String displayName;

    ResponsibleParty(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
