package com.dozernet.common.model;

/**
 * The four DozerNet user roles. Spring Security expects authorities prefixed
 * with "ROLE_", which {@link #authority()} provides.
 */
public enum Role {
    CUSTOMER("Customer"),
    OWNER("Private JCB Owner"),
    OPERATOR("Operator / Driver"),
    ADMIN("Administrator");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String authority() {
        return "ROLE_" + name();
    }
}
