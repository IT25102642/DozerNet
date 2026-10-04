package com.dozernet.module2_booking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The client's cancellation rules for a booking that has already been paid:
 *
 * <ul>
 *   <li>more than 48 hours before the hire date - full refund;</li>
 *   <li>within 48 hours of the hire date - 20% cancellation charge;</li>
 *   <li>on or after the hire start date - no refund.</li>
 * </ul>
 *
 * <p>The policy only decides <em>what fraction</em> comes back; module 6 owns
 * the money and writes the refund against the invoice.</p>
 */
public final class CancellationPolicy {

    /** Cut-off before the hire date at which the cancellation charge starts. */
    public static final int FREE_CANCELLATION_HOURS = 48;

    /** Charge applied when cancelling inside the cut-off. */
    public static final BigDecimal LATE_CANCELLATION_CHARGE_RATE = new BigDecimal("0.20");

    private CancellationPolicy() {
    }

    /** The outcome of applying the policy: how much comes back, and why. */
    public record Outcome(BigDecimal refundFraction, String description) {

        public boolean isFullRefund() {
            return refundFraction.compareTo(BigDecimal.ONE) == 0;
        }

        public boolean isNoRefund() {
            return refundFraction.compareTo(BigDecimal.ZERO) == 0;
        }
    }

    public static Outcome evaluate(LocalDate hireStartDate) {
        return evaluate(hireStartDate, LocalDateTime.now());
    }

    public static Outcome evaluate(LocalDate hireStartDate, LocalDateTime now) {
        if (hireStartDate == null) {
            throw new IllegalArgumentException("Hire start date is required to price a cancellation");
        }

        LocalDateTime hireStart = hireStartDate.atStartOfDay();
        if (!now.isBefore(hireStart)) {
            return new Outcome(BigDecimal.ZERO,
                    "Cancelled on or after the hire start date - no refund is due.");
        }

        LocalDateTime chargeCutOff = hireStart.minusHours(FREE_CANCELLATION_HOURS);
        if (now.isBefore(chargeCutOff)) {
            return new Outcome(BigDecimal.ONE,
                    "Cancelled more than " + FREE_CANCELLATION_HOURS
                            + " hours before the hire date - full refund.");
        }

        return new Outcome(BigDecimal.ONE.subtract(LATE_CANCELLATION_CHARGE_RATE),
                "Cancelled within " + FREE_CANCELLATION_HOURS
                        + " hours of the hire date - a 20% cancellation charge applies.");
    }
}
