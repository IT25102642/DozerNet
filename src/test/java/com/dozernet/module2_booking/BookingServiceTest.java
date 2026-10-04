package com.dozernet.module2_booking;

import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.common.model.Role;
import com.dozernet.common.notification.NotificationService;
import com.dozernet.common.user.User;
import com.dozernet.common.user.UserRepository;
import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module2_booking.entity.BookingStatus;
import com.dozernet.module2_booking.repository.BookingRepository;
import com.dozernet.module2_booking.service.BookingService;
import com.dozernet.module3_fleet.entity.Machine;
import com.dozernet.module3_fleet.entity.MachineStatus;
import com.dozernet.module3_fleet.entity.MachineType;
import com.dozernet.module3_fleet.service.FleetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the trickiest business rules in the system: booking dates and
 * double-booking prevention.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock FleetService fleetService;
    @Mock NotificationService notificationService;
    @Mock UserRepository userRepository;
    @Mock com.dozernet.module6_payment.service.PaymentService paymentService;
    @Mock com.dozernet.module2_booking.repository.MachineReplacementLogRepository replacementLogRepository;
    @Mock com.dozernet.common.audit.AuditService auditService;
    @Mock com.dozernet.common.security.CurrentUserService currentUserService;

    @InjectMocks BookingService bookingService;

    private User customer;
    private Machine machine;

    @BeforeEach
    void setUp() {
        customer = new User("Chamara", "c@x.lk", "0771111111", "hash", Role.CUSTOMER);
        customer.setId(1L);

        machine = new Machine();
        machine.setId(10L);
        machine.setModel("JCB 3CX");
        machine.setType(MachineType.BACKHOE_LOADER);
        machine.setDailyRate(new BigDecimal("10000.00"));
        machine.setStatus(MachineStatus.AVAILABLE);
        machine.setVerified(true);

        lenient().when(fleetService.getById(10L)).thenReturn(machine);
        lenient().when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of());
        lenient().when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void rejectsStartDateInThePast() {
        assertThatThrownBy(() -> bookingService.validateDates(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(2)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("past");
    }

    @Test
    void rejectsEndBeforeStart() {
        assertThatThrownBy(() -> bookingService.validateDates(
                LocalDate.now().plusDays(3), LocalDate.now().plusDays(1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("on or after");
    }

    @Test
    void acceptsValidSameDayRange() {
        LocalDate today = LocalDate.now();
        bookingService.validateDates(today, today); // no exception
    }

    @Test
    void rejectsBookingOfUnavailableMachine() {
        machine.setStatus(MachineStatus.MAINTENANCE);
        assertThatThrownBy(() -> bookingService.create(customer, 10L,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(2),
                "Colombo", "Near Kaduwela flyover"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not currently available");
    }

    @Test
    void rejectsOverlappingBooking() {
        when(bookingRepository.findOverlapping(any(), any(), any()))
                .thenReturn(List.of(new Booking()));
        assertThatThrownBy(() -> bookingService.create(customer, 10L,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                "Gampaha", "Site gate A"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already booked");
    }

    @Test
    void createsBookingWithCorrectTotalAndPendingStatus() {
        when(bookingRepository.findOverlapping(any(), any(), any())).thenReturn(List.of());
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = start.plusDays(2); // 3 inclusive days

        Booking b = bookingService.create(customer, 10L, start, end,
                "Colombo", "Near Kaduwela flyover");

        assertThat(b.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(b.getDays()).isEqualTo(3);
        assertThat(b.getTotalAmount()).isEqualByComparingTo("30000.00");
        assertThat(b.getJobSiteDistrict()).isEqualTo("Colombo");
        assertThat(b.getJobSiteAddress()).isEqualTo("Near Kaduwela flyover");
        assertThat(b.getJobSiteLabel()).isEqualTo("Colombo — Near Kaduwela flyover");
    }

    @Test
    void rejectsInvalidJobSiteDistrict() {
        assertThatThrownBy(() -> bookingService.create(customer, 10L,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(2),
                "Atlantis", "Somewhere"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("district");
    }

    @Test
    void rejectsBookingBeyondTheAdvanceWindow() {
        LocalDate tooFar = LocalDate.now().plusDays(BookingService.MAX_ADVANCE_DAYS + 1);

        assertThatThrownBy(() -> bookingService.create(customer, 10L, tooFar, tooFar,
                "Colombo", "Site A"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("180 days in advance");
    }

    @Test
    void acceptsABookingOnTheLastAllowedDay() {
        LocalDate lastAllowed = LocalDate.now().plusDays(BookingService.MAX_ADVANCE_DAYS);
        when(bookingRepository.findOverlapping(any(), any(), any())).thenReturn(List.of());

        Booking b = bookingService.create(customer, 10L, lastAllowed, lastAllowed, "Colombo", "Site A");

        assertThat(b.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void booksEveryMachineInAMultiMachineRequestUnderOneGroup() {
        Machine second = new Machine();
        second.setId(11L);
        second.setModel("JCB JS205");
        second.setType(MachineType.EXCAVATOR);
        second.setDailyRate(new BigDecimal("20000.00"));
        second.setStatus(MachineStatus.AVAILABLE);
        second.setVerified(true);
        lenient().when(fleetService.getById(11L)).thenReturn(second);
        when(bookingRepository.findOverlapping(any(), any(), any())).thenReturn(List.of());

        List<Booking> created = bookingService.createForMachines(customer, List.of(10L, 11L),
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(2), "Colombo", "Site A");

        assertThat(created).hasSize(2);
        assertThat(created).allMatch(Booking::isPartOfGroup);
        assertThat(created.get(0).getBookingGroupId()).isEqualTo(created.get(1).getBookingGroupId());
    }

    @Test
    void singleMachineRequestIsNotGrouped() {
        when(bookingRepository.findOverlapping(any(), any(), any())).thenReturn(List.of());

        List<Booking> created = bookingService.createForMachines(customer, List.of(10L),
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(2), "Colombo", "Site A");

        assertThat(created).hasSize(1);
        assertThat(created.get(0).isPartOfGroup()).isFalse();
    }

    @Test
    void cannotCancelAnUnpaidBookingOnceItsHireHasStarted() {
        Booking b = new Booking(customer, machine, LocalDate.now(), LocalDate.now().plusDays(2));
        b.setId(7L);
        b.setStatus(BookingStatus.APPROVED);
        when(bookingRepository.findById(7L)).thenReturn(java.util.Optional.of(b));
        when(paymentService.isInvoicePaidForBooking(b)).thenReturn(false);

        assertThatThrownBy(() -> bookingService.cancelApproved(7L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("before its start date");
    }

    @Test
    void cancellingAPaidBookingRefundsThroughThePaymentModule() {
        Booking b = new Booking(customer, machine, LocalDate.now().plusDays(10), LocalDate.now().plusDays(12));
        b.setId(8L);
        b.setStatus(BookingStatus.APPROVED);
        when(bookingRepository.findById(8L)).thenReturn(java.util.Optional.of(b));
        when(paymentService.isInvoicePaidForBooking(b)).thenReturn(true);
        // Cancelled well ahead of the hire date, so the whole amount comes back.
        when(paymentService.refundForCancellation(eq(b), eq(BigDecimal.ONE)))
                .thenReturn(new BigDecimal("30000.00"));

        bookingService.cancelApproved(8L);

        assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(paymentService).refundForCancellation(b, BigDecimal.ONE);
    }

    @Test
    void replacingAMachineLogsTheSwapAndNotifiesTheCustomer() {
        Booking b = new Booking(customer, machine, LocalDate.now().plusDays(3), LocalDate.now().plusDays(5));
        b.setId(9L);
        b.setStatus(BookingStatus.APPROVED);
        when(bookingRepository.findById(9L)).thenReturn(java.util.Optional.of(b));

        Machine replacement = new Machine();
        replacement.setId(12L);
        replacement.setModel("JCB 4CX");
        replacement.setType(MachineType.BACKHOE_LOADER);
        replacement.setDailyRate(new BigDecimal("12000.00"));
        replacement.setStatus(MachineStatus.AVAILABLE);
        replacement.setVerified(true);
        when(fleetService.getById(12L)).thenReturn(replacement);
        when(bookingRepository.findOverlapping(eq(replacement), any(), any())).thenReturn(List.of());
        when(replacementLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        bookingService.replaceMachine(9L, 12L, "Hydraulic fault");

        assertThat(b.getMachine()).isEqualTo(replacement);
        // Re-priced at the replacement's daily rate over three days.
        assertThat(b.getTotalAmount()).isEqualByComparingTo("36000.00");
        verify(replacementLogRepository).save(any());
        verify(notificationService).notify(eq(customer), eq("Machine changed for your booking"), anyString());
    }

    @Test
    void cannotReplaceWithAMachineThatIsAlreadyBooked() {
        Booking b = new Booking(customer, machine, LocalDate.now().plusDays(3), LocalDate.now().plusDays(5));
        b.setId(9L);
        b.setStatus(BookingStatus.APPROVED);
        when(bookingRepository.findById(9L)).thenReturn(java.util.Optional.of(b));

        Machine replacement = new Machine();
        replacement.setId(12L);
        replacement.setModel("JCB 4CX");
        replacement.setDailyRate(new BigDecimal("12000.00"));
        replacement.setStatus(MachineStatus.AVAILABLE);
        replacement.setVerified(true);
        when(fleetService.getById(12L)).thenReturn(replacement);

        Booking clash = new Booking(customer, replacement, LocalDate.now().plusDays(4), LocalDate.now().plusDays(6));
        clash.setId(99L);
        when(bookingRepository.findOverlapping(eq(replacement), any(), any())).thenReturn(List.of(clash));

        assertThatThrownBy(() -> bookingService.replaceMachine(9L, 12L, "Breakdown"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already booked");
    }

    @Test
    void cannotApproveNonPendingBooking() {
        Booking b = new Booking(customer, machine, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));
        b.setId(5L);
        b.setStatus(BookingStatus.APPROVED);
        when(bookingRepository.findById(5L)).thenReturn(java.util.Optional.of(b));

        assertThatThrownBy(() -> bookingService.approve(5L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("pending");
    }

    @Test
    void customerCannotCancelSomeoneElsesBooking() {
        User other = new User("Other", "o@x.lk", "0779999999", "hash", Role.CUSTOMER);
        other.setId(2L);
        Booking b = new Booking(other, machine, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));
        b.setId(7L);
        b.setStatus(BookingStatus.PENDING);
        when(bookingRepository.findById(7L)).thenReturn(java.util.Optional.of(b));

        assertThatThrownBy(() -> bookingService.cancel(customer, 7L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("your own");
    }
}
