package com.dozernet.module6_payment;

import com.dozernet.common.user.User;
import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module3_fleet.entity.Machine;
import com.dozernet.module3_fleet.entity.MachineType;
import com.dozernet.module3_fleet.entity.Ownership;
import com.dozernet.module6_payment.pricing.PricingBreakdown;
import com.dozernet.module6_payment.pricing.PricingSelector;
import com.dozernet.module6_payment.pricing.StandardPricingStrategy;
import com.dozernet.module6_payment.pricing.TransportSurchargeCalculator;
import com.dozernet.module6_payment.service.CardPaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pricing arithmetic and card validation - the money rules the client gave us,
 * exercised without Spring so the sums are easy to follow.
 */
class PricingTest {

    private PricingSelector selector;
    private TransportSurchargeCalculator surcharge;

    @BeforeEach
    void setUp() {
        surcharge = new TransportSurchargeCalculator();
        selector = new PricingSelector(List.of(new StandardPricingStrategy()), surcharge,
                new BigDecimal("0.18"), new BigDecimal("0.10"));
    }

    private static Booking booking(String district, String dailyRate, int days, Ownership ownership) {
        Machine machine = new Machine();
        machine.setId(1L);
        machine.setModel("JCB 3CX");
        machine.setType(MachineType.BACKHOE_LOADER);
        machine.setDailyRate(new BigDecimal(dailyRate));
        machine.setOwnership(ownership);
        if (ownership == Ownership.PRIVATE) {
            User owner = new User();
            owner.setId(7L);
            machine.setOwner(owner);
        }

        User customer = new User();
        customer.setId(2L);

        LocalDate start = LocalDate.now().plusDays(1);
        Booking booking = new Booking(customer, machine, start, start.plusDays(days - 1));
        booking.setJobSiteDistrict(district);
        booking.setJobSiteAddress("Site A");
        return booking;
    }

    @Test
    void chargesDaysTimesDailyRatePlusHaulageAndVat() {
        // 3 days x 18,500 = 55,500 hire; Jaffna haulage 10,000; VAT 18% of 65,500 = 11,790.
        PricingBreakdown breakdown = selector.priceBreakdown(
                booking("Jaffna", "18500.00", 3, Ownership.COMPANY));

        assertThat(breakdown.baseAmount()).isEqualByComparingTo("55500.00");
        assertThat(breakdown.transportSurcharge()).isEqualByComparingTo("10000.00");
        assertThat(breakdown.vatAmount()).isEqualByComparingTo("11790.00");
        assertThat(breakdown.total()).isEqualByComparingTo("77290.00");
    }

    @Test
    void chargesTheCheapestHaulageInsideColombo() {
        PricingBreakdown breakdown = selector.priceBreakdown(
                booking("Colombo", "10000.00", 1, Ownership.COMPANY));

        assertThat(breakdown.transportSurcharge()).isEqualByComparingTo("2000.00");
        // 10,000 + 2,000 = 12,000, VAT 2,160.
        assertThat(breakdown.total()).isEqualByComparingTo("14160.00");
    }

    @Test
    void everyDistrictSitsInsideTheAgreedBand() {
        surcharge.allBands().values().forEach(band ->
                assertThat(band).isBetween(new BigDecimal("2000.00"), new BigDecimal("10000.00")));
    }

    @Test
    void unknownDistrictFallsBackToTheDefaultHaulage() {
        assertThat(surcharge.surchargeFor("Atlantis"))
                .isEqualByComparingTo(TransportSurchargeCalculator.DEFAULT_SURCHARGE);
    }

    @Test
    void takesNoCommissionOnCompanyMachines() {
        PricingBreakdown breakdown = selector.priceBreakdown(
                booking("Colombo", "10000.00", 2, Ownership.COMPANY));

        assertThat(breakdown.ownerCommission()).isEqualByComparingTo("0.00");
    }

    @Test
    void takesTenPercentCommissionOnPrivateMachines() {
        // 2 days x 10,000 = 20,000 hire -> 2,000 commission, 18,000 to the owner.
        PricingBreakdown breakdown = selector.priceBreakdown(
                booking("Colombo", "10000.00", 2, Ownership.PRIVATE));

        assertThat(breakdown.ownerCommission()).isEqualByComparingTo("2000.00");
        assertThat(breakdown.ownerPayout()).isEqualByComparingTo("18000.00");
    }

    @Test
    void aSingleDayHireIsChargedForOneFullDay() {
        PricingBreakdown breakdown = selector.priceBreakdown(
                booking("Colombo", "18500.00", 1, Ownership.COMPANY));

        assertThat(breakdown.baseAmount()).isEqualByComparingTo("18500.00");
    }

    @Test
    void acceptsCardNumbersThatPassLuhn() {
        assertThat(CardPaymentRequest.passesLuhn("4242424242424242")).isTrue();
        assertThat(CardPaymentRequest.passesLuhn("5555555555554444")).isTrue();
        assertThat(CardPaymentRequest.passesLuhn("378282246310005")).isTrue();
    }

    @Test
    void rejectsCardNumbersThatFailLuhn() {
        assertThat(CardPaymentRequest.passesLuhn("4242424242424243")).isFalse();
        assertThat(CardPaymentRequest.passesLuhn("1234567812345678")).isFalse();
    }

    @Test
    void keepsOnlyTheLastFourDigitsOfACard() {
        CardPaymentRequest card = new CardPaymentRequest("Chamara", "4242 4242 4242 4242", "12/30", "123");

        assertThat(card.last4()).isEqualTo("4242");
    }
}
