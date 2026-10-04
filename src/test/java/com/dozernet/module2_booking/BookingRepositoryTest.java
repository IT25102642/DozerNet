package com.dozernet.module2_booking;

import com.dozernet.common.model.Role;
import com.dozernet.common.user.User;
import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module2_booking.entity.BookingStatus;
import com.dozernet.module2_booking.repository.BookingRepository;
import com.dozernet.module3_fleet.entity.Machine;
import com.dozernet.module3_fleet.entity.MachineType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the date-overlap query that prevents double-booking, directly
 * against the database.
 */
@DataJpaTest
class BookingRepositoryTest {

    @Autowired BookingRepository bookingRepository;
    @Autowired TestEntityManager em;

    private Machine machine;
    private final LocalDate base = LocalDate.now().plusDays(10);

    @BeforeEach
    void setUp() {
        User customer = new User("Chamara", "c@x.lk", "0771111111", "hash", Role.CUSTOMER);
        em.persist(customer);

        machine = new Machine();
        machine.setModel("JCB 3CX");
        machine.setType(MachineType.BACKHOE_LOADER);
        machine.setRegistrationNumber("WP-TEST-1");
        machine.setLocation("Colombo");
        machine.setDailyRate(new BigDecimal("10000.00"));
        em.persist(machine);

        // Existing APPROVED booking occupying base .. base+5
        Booking existing = new Booking(customer, machine, base, base.plusDays(5));
        existing.setStatus(BookingStatus.APPROVED);
        existing.setTotalAmount(new BigDecimal("60000.00"));
        existing.setJobSiteDistrict("Colombo");
        existing.setJobSiteAddress("Test site A");
        em.persist(existing);

        // A cancelled booking that must NOT block
        Booking cancelled = new Booking(customer, machine, base.plusDays(20), base.plusDays(22));
        cancelled.setStatus(BookingStatus.CANCELLED);
        cancelled.setTotalAmount(new BigDecimal("30000.00"));
        cancelled.setJobSiteDistrict("Gampaha");
        cancelled.setJobSiteAddress("Test site B");
        em.persist(cancelled);

        em.flush();
    }

    @Test
    void detectsRangeInsideExisting() {
        assertThat(bookingRepository.findOverlapping(machine, base.plusDays(1), base.plusDays(3)))
                .hasSize(1);
    }

    @Test
    void detectsTouchingEndDate() {
        // New range starts exactly on the existing end date -> still an overlap
        assertThat(bookingRepository.findOverlapping(machine, base.plusDays(5), base.plusDays(7)))
                .hasSize(1);
    }

    @Test
    void allowsNonOverlappingRange() {
        assertThat(bookingRepository.findOverlapping(machine, base.plusDays(6), base.plusDays(9)))
                .isEmpty();
    }

    @Test
    void ignoresCancelledBookings() {
        assertThat(bookingRepository.findOverlapping(machine, base.plusDays(20), base.plusDays(22)))
                .isEmpty();
    }
}
