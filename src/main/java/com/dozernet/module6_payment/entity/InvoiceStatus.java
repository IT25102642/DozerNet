package com.dozernet.module6_payment.entity;

public enum InvoiceStatus {
    UNPAID("Unpaid"),
    PARTIALLY_PAID("Partially paid"),
    PAID("Paid"),
    CANCELLED("Cancelled");

    private final String displayName;

    InvoiceStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
