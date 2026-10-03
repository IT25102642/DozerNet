package com.dozernet.module6_payment.pricing;

import java.math.BigDecimal;

/**
 * The itemised total for a booking: hire charge, haulage, VAT, and - where the
 * machine belongs to a private owner - the commission DozerNet keeps from the
 * hire charge. Shown line by line on the invoice.
 *
 * @param baseAmount        hire charge from the applicable pricing strategy
 * @param transportSurcharge district-based haulage charge
 * @param vatAmount         VAT on (hire + haulage)
 * @param total             what the customer owes
 * @param ownerCommission   DozerNet's cut of the hire charge (zero for company machines)
 * @param strategyLabel     which pricing strategy produced the hire charge
 */
public record PricingBreakdown(
        BigDecimal baseAmount,
        BigDecimal transportSurcharge,
        BigDecimal vatAmount,
        BigDecimal total,
        BigDecimal ownerCommission,
        String strategyLabel
) {
    /** What the private owner receives once commission is deducted. */
    public BigDecimal ownerPayout() {
        return baseAmount.subtract(ownerCommission);
    }
}
