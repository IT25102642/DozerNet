package com.dozernet.module6_payment.pricing;

import com.dozernet.module2_booking.entity.Booking;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Cost of floating a machine out to the job site and back. This is an additive
 * charge rather than a {@link PricingStrategy}: the strategy decides the hire
 * price, this decides the haulage on top of it.
 *
 * <p>Bands are set by distance from the Colombo yard, from Rs. 2,000 within the
 * Western Province up to Rs. 10,000 for the far north.</p>
 */
@Component
public class TransportSurchargeCalculator {

    /** Charged when a job site district is not recognised. */
    public static final BigDecimal DEFAULT_SURCHARGE = new BigDecimal("6000.00");

    private static final Map<String, BigDecimal> BY_DISTRICT = Map.ofEntries(
            // Western province - the yard is in Colombo
            Map.entry("Colombo", new BigDecimal("2000.00")),
            Map.entry("Gampaha", new BigDecimal("2500.00")),
            Map.entry("Kalutara", new BigDecimal("3000.00")),
            // Central / Sabaragamuwa / North Western
            Map.entry("Kegalle", new BigDecimal("3500.00")),
            Map.entry("Ratnapura", new BigDecimal("4000.00")),
            Map.entry("Kurunegala", new BigDecimal("4000.00")),
            Map.entry("Kandy", new BigDecimal("4500.00")),
            Map.entry("Puttalam", new BigDecimal("4500.00")),
            Map.entry("Matale", new BigDecimal("5000.00")),
            Map.entry("Nuwara Eliya", new BigDecimal("5500.00")),
            // Southern
            Map.entry("Galle", new BigDecimal("4500.00")),
            Map.entry("Matara", new BigDecimal("5000.00")),
            Map.entry("Hambantota", new BigDecimal("6000.00")),
            // North Central / Uva
            Map.entry("Anuradhapura", new BigDecimal("6000.00")),
            Map.entry("Polonnaruwa", new BigDecimal("6500.00")),
            Map.entry("Badulla", new BigDecimal("6500.00")),
            Map.entry("Monaragala", new BigDecimal("7000.00")),
            // Eastern
            Map.entry("Trincomalee", new BigDecimal("7500.00")),
            Map.entry("Batticaloa", new BigDecimal("8000.00")),
            Map.entry("Ampara", new BigDecimal("8000.00")),
            // Northern
            Map.entry("Vavuniya", new BigDecimal("8000.00")),
            Map.entry("Mannar", new BigDecimal("9000.00")),
            Map.entry("Mullaitivu", new BigDecimal("9000.00")),
            Map.entry("Kilinochchi", new BigDecimal("9500.00")),
            Map.entry("Jaffna", new BigDecimal("10000.00"))
    );

    public BigDecimal surchargeFor(Booking booking) {
        return booking == null ? DEFAULT_SURCHARGE : surchargeFor(booking.getJobSiteDistrict());
    }

    public BigDecimal surchargeFor(String district) {
        if (district == null || district.isBlank()) {
            return DEFAULT_SURCHARGE;
        }
        return BY_DISTRICT.getOrDefault(district.trim(), DEFAULT_SURCHARGE);
    }

    /** Exposed for the pricing information shown on the booking form. */
    public Map<String, BigDecimal> allBands() {
        return BY_DISTRICT;
    }
}
