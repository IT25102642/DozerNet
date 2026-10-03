package com.dozernet.module5_maintenance.entity;

/**
 * Whether a maintenance job is upcoming or finished.
 */
public enum MaintenanceStatus {
    SCHEDULED("Scheduled"), //Possible enum value
    COMPLETED("Completed"); //Possible enum value

    private final String displayName; //'final' indicates that the value cannot be changed after assignment

    MaintenanceStatus(String displayName) { //Assigns the constructor value to the class variable.
        this.displayName = displayName;
    }

    public String getDisplayName() { //(Getter) Allow other classes to read the display name
        return displayName;
    }
}
