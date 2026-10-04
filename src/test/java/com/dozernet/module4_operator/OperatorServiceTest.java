package com.dozernet.module4_operator;

import com.dozernet.common.audit.AuditService;
import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.common.model.Role;
import com.dozernet.common.notification.NotificationService;
import com.dozernet.common.user.AccountService;
import com.dozernet.common.user.User;
import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module2_booking.entity.BookingStatus;
import com.dozernet.module2_booking.service.BookingService;
import com.dozernet.module3_fleet.entity.Machine;
import com.dozernet.module3_fleet.entity.MachineType;
import com.dozernet.module4_operator.entity.Assignment;
import com.dozernet.module4_operator.entity.JobStatus;
import com.dozernet.module4_operator.entity.OperatorProfile;
import com.dozernet.module4_operator.repository.AssignmentRepository;
import com.dozernet.module4_operator.repository.OperatorProfileRepository;
import com.dozernet.module4_operator.service.OperatorService;
import com.dozernet.module6_payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Operator rules: pay before assignment, verified licences only, no operator in
 * two places at once, and job status only ever moves forwards.
 */
@ExtendWith(MockitoExtension.class)
class OperatorServiceTest {

    @Mock OperatorProfileRepository profileRepository;
    @Mock AssignmentRepository assignmentRepository;
    @Mock AccountService accountService;
    @Mock BookingService bookingService;
    @Mock NotificationService notificationService;
    @Mock PaymentService paymentService;
    @Mock AuditService auditService;

    @InjectMocks OperatorService operatorService;

    private User operator;
    private OperatorProfile profile;
    private Booking booking;

    @BeforeEach
    void setUp() {
        operator = new User("Sunil", "s@x.lk", "0773333333", "hash", Role.OPERATOR);
        operator.setId(4L);

        profile = new OperatorProfile();
        profile.setId(40L);
        profile.setUser(operator);
        profile.setLicenceNumber("B1234567");
        profile.setExperienceYears(6);
        profile.setVerified(true);

        User customer = new User("Chamara", "c@x.lk", "0771111111", "hash", Role.CUSTOMER);
        customer.setId(1L);

        Machine machine = new Machine();
        machine.setId(10L);
        machine.setModel("JCB 3CX");
        machine.setType(MachineType.BACKHOE_LOADER);
        machine.setDailyRate(new BigDecimal("18500.00"));

        booking = new Booking(customer, machine, LocalDate.now().plusDays(2), LocalDate.now().plusDays(4));
        booking.setId(5L);
        booking.setStatus(BookingStatus.APPROVED);

        lenient().when(bookingService.getById(5L)).thenReturn(booking);
    }

    @Test
    void refusesToAssignBeforeTheInvoiceIsPaid() {
        when(paymentService.isInvoicePaidForBooking(booking)).thenReturn(false);

        assertThatThrownBy(() -> operatorService.assign(5L, operator))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must pay the invoice");

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void refusesToAssignToABookingThatIsNotApproved() {
        booking.setStatus(BookingStatus.PENDING);

        assertThatThrownBy(() -> operatorService.assign(5L, operator))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("approved bookings");
    }

    @Test
    void refusesToAssignAnUnverifiedOperator() {
        profile.setVerified(false);
        when(paymentService.isInvoicePaidForBooking(booking)).thenReturn(true);
        when(assignmentRepository.existsByBooking(booking)).thenReturn(false);
        when(profileRepository.findByUser(operator)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> operatorService.assign(5L, operator))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not been verified");
    }

    @Test
    void refusesToDoubleBookAnOperator() {
        when(paymentService.isInvoicePaidForBooking(booking)).thenReturn(true);
        when(assignmentRepository.existsByBooking(booking)).thenReturn(false);
        when(profileRepository.findByUser(operator)).thenReturn(Optional.of(profile));
        when(assignmentRepository.findOperatorClashes(any(), any(), any()))
                .thenReturn(List.of(new Assignment(booking, operator)));

        assertThatThrownBy(() -> operatorService.assign(5L, operator))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already assigned to another job");
    }

    @Test
    void assignsAPaidBookingToAVerifiedOperator() {
        when(paymentService.isInvoicePaidForBooking(booking)).thenReturn(true);
        when(assignmentRepository.existsByBooking(booking)).thenReturn(false);
        when(profileRepository.findByUser(operator)).thenReturn(Optional.of(profile));
        when(assignmentRepository.findOperatorClashes(any(), any(), any())).thenReturn(List.of());
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Assignment assignment = operatorService.assign(5L, operator);

        assertThat(assignment.getJobStatus()).isEqualTo(JobStatus.ASSIGNED);
        // Both sides are told: the operator gets the job, the customer gets the news.
        verify(notificationService).notify(eq(operator), eq("New job assigned"), anyString());
        verify(notificationService).notify(eq(booking.getCustomer()), eq("Operator assigned"), anyString());
    }

    @Test
    void suggestsFreeOperatorsAheadOfBusyOnes() {
        User busy = new User("Kamal", "k@x.lk", "0774444444", "hash", Role.OPERATOR);
        busy.setId(5L);
        OperatorProfile busyProfile = new OperatorProfile();
        busyProfile.setId(41L);
        busyProfile.setUser(busy);
        busyProfile.setVerified(true);

        when(profileRepository.findByVerifiedTrue()).thenReturn(List.of(busyProfile, profile));
        when(assignmentRepository.findOperatorClashes(busy, booking.getStartDate(), booking.getEndDate()))
                .thenReturn(List.of(new Assignment(booking, busy)));
        when(assignmentRepository.findOperatorClashes(operator, booking.getStartDate(), booking.getEndDate()))
                .thenReturn(List.of());

        List<OperatorService.OperatorOption> options = operatorService.suggestionsFor(booking);

        assertThat(options).hasSize(2);
        assertThat(options.get(0).available()).isTrue();
        assertThat(options.get(0).profile()).isEqualTo(profile);
        assertThat(options.get(1).available()).isFalse();
        assertThat(options.get(1).clashingJobDates()).isNotBlank();
    }

    @Test
    void jobStatusOnlyMovesForwards() {
        Assignment assignment = new Assignment(booking, operator);
        assignment.setId(50L);
        assignment.setJobStatus(JobStatus.COMPLETED);
        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> operatorService.updateJobStatus(50L, JobStatus.IN_PROGRESS, operator))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot change job status");
    }

    @Test
    void completingAJobCompletesTheBooking() {
        Assignment assignment = new Assignment(booking, operator);
        assignment.setId(50L);
        assignment.setJobStatus(JobStatus.IN_PROGRESS);
        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        operatorService.updateJobStatus(50L, JobStatus.COMPLETED, operator);

        assertThat(assignment.getJobStatus()).isEqualTo(JobStatus.COMPLETED);
        verify(bookingService).markCompleted(5L);
    }

    @Test
    void anOperatorCannotTouchSomeoneElsesAssignment() {
        User other = new User("Kamal", "k@x.lk", "0774444444", "hash", Role.OPERATOR);
        other.setId(9L);
        Assignment assignment = new Assignment(booking, operator);
        assignment.setId(50L);
        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> operatorService.updateJobStatus(50L, JobStatus.IN_PROGRESS, other))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("your own assignments");
    }
}
