package com.dozernet.module3_fleet.entity;

/**
 * Whether a machine belongs to the company fleet or a verified private owner.
 */
public enum Ownership {
    COMPANY("Company-owned"),
    PRIVATE("Private owner");

    private final String displayName;

    Ownership(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
