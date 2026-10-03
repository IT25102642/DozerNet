package com.dozernet.module6_payment.entity;

/**
 * Payment methods. CARD is a demo online portal method (no real gateway).
 * REFUND marks money going back to the customer after a cancellation, and is
 * recorded as a negative amount so the invoice ledger still balances.
 */
public enum PaymentMethod {
    CASH("Cash"),
    BANK_TRANSFER("Bank transfer"),
    CARD("Card (demo)"),
    REFUND("Refund");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
