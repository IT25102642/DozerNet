package com.dozernet.module6_payment.pricing;

import com.dozernet.module2_booking.entity.Booking;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Default pricing: days * daily rate. Applies to any booking.
 */
@Component
@Order(100)
public class StandardPricingStrategy implements PricingStrategy {

    @Override
    public boolean applies(Booking booking) {
        return true;
    }

    @Override
    public BigDecimal calculate(Booking booking) {
        return booking.getMachine().getDailyRate().multiply(BigDecimal.valueOf(booking.getDays()));
    }

    @Override
    public String label() {
        return "Standard";
    }
}
