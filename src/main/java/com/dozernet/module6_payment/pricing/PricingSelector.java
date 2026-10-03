package com.dozernet.module6_payment.pricing;

import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module3_fleet.entity.Ownership;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Prices a booking. Spring injects every {@link PricingStrategy} in @Order
 * order, so the first that applies decides the hire charge; haulage, VAT and
 * owner commission are then layered on top to produce the invoice total.
 */
@Component
public class PricingSelector {

    private final List<PricingStrategy> strategies;
    private final TransportSurchargeCalculator transportSurcharge;
    private final BigDecimal vatRate;
    private final BigDecimal ownerCommissionRate;

    public PricingSelector(List<PricingStrategy> strategies,
                           TransportSurchargeCalculator transportSurcharge,
                           @Value("${dozernet.vat-rate:0.18}") BigDecimal vatRate,
                           @Value("${dozernet.owner-commission-rate:0.10}") BigDecimal ownerCommissionRate) {
        this.strategies = strategies;
        this.transportSurcharge = transportSurcharge;
        this.vatRate = vatRate;
        this.ownerCommissionRate = ownerCommissionRate;
    }

    public PricingStrategy select(Booking booking) {
        return strategies.stream()
                .filter(s -> s.applies(booking))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No pricing strategy available"));
    }

    /** Hire charge only, before haulage and VAT. */
    public BigDecimal price(Booking booking) {
        return money(select(booking).calculate(booking));
    }

    /** Full itemised total for the invoice. */
    public PricingBreakdown priceBreakdown(Booking booking) {
        PricingStrategy strategy = select(booking);
        BigDecimal base = money(strategy.calculate(booking));
        BigDecimal haulage = money(transportSurcharge.surchargeFor(booking));
        BigDecimal vat = money(base.add(haulage).multiply(vatRate));
        BigDecimal total = base.add(haulage).add(vat);

        BigDecimal commission = isPrivatelyOwned(booking)
                ? money(base.multiply(ownerCommissionRate))
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        return new PricingBreakdown(base, haulage, vat, total, commission, strategy.label());
    }

    private static boolean isPrivatelyOwned(Booking booking) {
        return booking.getMachine() != null
                && booking.getMachine().getOwnership() == Ownership.PRIVATE;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
