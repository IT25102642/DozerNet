package com.dozernet.module6_payment.pricing;

import com.dozernet.module2_booking.entity.Booking;

import java.math.BigDecimal;

/**
 * Strategy pattern: an interchangeable rule for calculating the invoice amount
 * for a booking. New pricing rules can be added by creating another Spring bean
 * that implements this interface - no change to the payment service.
 */
public interface PricingStrategy {

    /** Whether this strategy applies to the given booking. */
    boolean applies(Booking booking);

    /** The invoice amount for the booking. */
    BigDecimal calculate(Booking booking);

    /** Short label for reporting/UI. */
    String label();
}
