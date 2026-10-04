package com.dozernet.module2_booking.entity;

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
