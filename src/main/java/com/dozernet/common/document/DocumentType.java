package com.dozernet.common.document;

/**
 * The kinds of supporting document DozerNet keeps on file. Documents are held
 * against the account that uploaded them so they can be reused for later
 * registrations and listings instead of being re-uploaded each time.
 */
public enum DocumentType {

    NIC("NIC copy"),
    LICENCE("Driving licence"),
    OWNERSHIP("Machine ownership proof"),
    INSPECTION_PHOTO("Return inspection photo");

    private final String displayName;

    DocumentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
