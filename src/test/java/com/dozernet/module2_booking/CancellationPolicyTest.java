package com.dozernet.module2_booking;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The client's refund bands, pinned to a fixed "now" so the tests do not drift
 * with the clock.
 */
class CancellationPolicyTest {

    private static final LocalDate HIRE_START = LocalDate.of(2026, 6, 10);

    @Test
    void refundsInFullWellBeforeTheHireDate() {
        // Five days out.
        CancellationPolicy.Outcome outcome =
                CancellationPolicy.evaluate(HIRE_START, LocalDateTime.of(2026, 6, 5, 9, 0));

        assertThat(outcome.refundFraction()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(outcome.isFullRefund()).isTrue();
        assertThat(outcome.description()).contains("full refund");
    }

    @Test
    void refundsInFullJustOutsideTheCutOff() {
        // 48 hours and one minute before the hire starts.
        CancellationPolicy.Outcome outcome =
                CancellationPolicy.evaluate(HIRE_START, LocalDateTime.of(2026, 6, 7, 23, 59));

        assertThat(outcome.isFullRefund()).isTrue();
    }

    @Test
    void chargesTwentyPercentInsideTheCutOff() {
        // Exactly 48 hours before: the charge applies from here on.
        CancellationPolicy.Outcome outcome =
                CancellationPolicy.evaluate(HIRE_START, LocalDateTime.of(2026, 6, 8, 0, 0));

        assertThat(outcome.refundFraction()).isEqualByComparingTo(new BigDecimal("0.80"));
        assertThat(outcome.description()).contains("20% cancellation charge");
    }

    @Test
    void chargesTwentyPercentTheDayBefore() {
        CancellationPolicy.Outcome outcome =
                CancellationPolicy.evaluate(HIRE_START, LocalDateTime.of(2026, 6, 9, 18, 0));

        assertThat(outcome.refundFraction()).isEqualByComparingTo(new BigDecimal("0.80"));
    }

    @Test
    void refundsNothingOnceTheHireHasStarted() {
        CancellationPolicy.Outcome outcome =
                CancellationPolicy.evaluate(HIRE_START, LocalDateTime.of(2026, 6, 10, 0, 0));

        assertThat(outcome.isNoRefund()).isTrue();
        assertThat(outcome.description()).contains("no refund");
    }

    @Test
    void refundsNothingAfterTheHireHasStarted() {
        CancellationPolicy.Outcome outcome =
                CancellationPolicy.evaluate(HIRE_START, LocalDateTime.of(2026, 6, 11, 8, 0));

        assertThat(outcome.isNoRefund()).isTrue();
    }

    @Test
    void requiresAHireDate() {
        assertThatThrownBy(() -> CancellationPolicy.evaluate(null, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
