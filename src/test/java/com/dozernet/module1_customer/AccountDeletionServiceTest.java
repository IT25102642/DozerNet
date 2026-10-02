package com.dozernet.module1_customer;

import com.dozernet.common.audit.AuditService;
import com.dozernet.common.document.DocumentService;
import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.common.model.Role;
import com.dozernet.common.notification.NotificationService;
import com.dozernet.common.user.User;
import com.dozernet.common.user.UserRepository;
import com.dozernet.module1_customer.service.AccountDeletionService;
import com.dozernet.module1_customer.service.AccountDeletionService.DeletionCheck;
import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module2_booking.entity.BookingStatus;
import com.dozernet.module2_booking.service.BookingService;
import com.dozernet.module6_payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock BookingService bookingService;
    @Mock PaymentService paymentService;
    @Mock DocumentService documentService;
    @Mock NotificationService notificationService;
    @Mock AuditService auditService;

    @InjectMocks AccountDeletionService service;

    private User customer;

    @BeforeEach
    void setUp() {
        customer = new User("Real Name", "real@example.com", "0771234567", "199512345678", "stored-hash", Role.CUSTOMER);
        customer.setId(42L);
        lenient().when(userRepository.findById(42L)).thenReturn(Optional.of(customer));
        lenient().when(passwordEncoder.matches("right-password", "stored-hash")).thenReturn(true);
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("random-hash");
        lenient().when(bookingService.forCustomer(customer)).thenReturn(List.of());
        lenient().when(paymentService.countUnpaidForCustomer(customer)).thenReturn(0L);
    }

    private static Booking booking(BookingStatus status) {
        Booking booking = mock(Booking.class);
        when(booking.getStatus()).thenReturn(status);
        return booking;
    }

    // ---------- the rule: what blocks deletion ----------

    @Test
    void aCustomerWithNoOpenBusinessMayDelete() {
        assertThat(service.check(customer).allowed()).isTrue();
        assertThat(service.check(customer).message()).isEmpty();
    }

    @Test
    void aPendingBookingBlocksDeletion() {
        doReturn(List.of(booking(BookingStatus.PENDING))).when(bookingService).forCustomer(customer);

        DeletionCheck check = service.check(customer);

        assertThat(check.allowed()).isFalse();
        assertThat(check.message()).contains("1 pending booking request (cancel it").contains("finish");
    }

    @Test
    void anApprovedBookingBlocksDeletion() {
        doReturn(List.of(booking(BookingStatus.APPROVED), booking(BookingStatus.APPROVED))).when(bookingService).forCustomer(customer);

        assertThat(service.check(customer).message())
                .contains("2 confirmed bookings that haven't been completed or cancelled");
    }

    @Test
    void anUnpaidInvoiceBlocksDeletion() {
        when(paymentService.countUnpaidForCustomer(customer)).thenReturn(3L);

        assertThat(service.check(customer).message()).contains("3 unpaid invoices");
    }

    @Test
    void finishedBookingsDoNotBlockDeletion() {
        doReturn(List.of(
                booking(BookingStatus.COMPLETED), booking(BookingStatus.CANCELLED), booking(BookingStatus.REJECTED))).when(bookingService).forCustomer(customer);

        assertThat(service.check(customer).allowed()).isTrue();
    }

    @Test
    void everyBlockerIsListedTogether() {
        doReturn(List.of(booking(BookingStatus.PENDING), booking(BookingStatus.APPROVED))).when(bookingService).forCustomer(customer);
        when(paymentService.countUnpaidForCustomer(customer)).thenReturn(1L);

        assertThat(service.check(customer).reasons()).hasSize(3);
    }

    @Test
    void anAccountWithAnotherRoleIsReferredToAnAdministrator() {
        customer.addRole(Role.OWNER);

        DeletionCheck check = service.check(customer);

        assertThat(check.allowed()).isFalse();
        assertThat(check.message()).contains("private jcb owner").contains("administrator");
    }

    // ---------- deleting ----------

    @Test
    void deletingAnonymisesTheAccountAndErasesItsData() {
        service.deleteOwnAccount(customer, "right-password", true);

        assertThat(customer.isDeleted()).isTrue();
        assertThat(customer.isEnabled()).isFalse();
        assertThat(customer.getFullName()).isEqualTo("Deleted customer");
        assertThat(customer.getEmail()).isEqualTo("deleted-42@deleted.invalid");
        assertThat(customer.getPhone()).isEqualTo("deleted-42");
        assertThat(customer.getIdentityCardNumber()).isEqualTo("DEL-42");
        assertThat(customer.getPasswordHash()).isEqualTo("random-hash");
        verify(documentService).deleteAllFor(customer);
        verify(notificationService).deleteAllFor(customer);
        verify(userRepository).save(customer);
        verify(auditService).record(eq("ACCOUNT_DELETED"), eq("User"), eq(42L), anyString());
    }

    @Test
    void deletionIsRefusedWhileBookingsAreOpenAndNothingIsTouched() {
        doReturn(List.of(booking(BookingStatus.APPROVED))).when(bookingService).forCustomer(customer);

        assertThatThrownBy(() -> service.deleteOwnAccount(customer, "right-password", true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("can't delete your account yet");

        assertThat(customer.isDeleted()).isFalse();
        assertThat(customer.getEmail()).isEqualTo("real@example.com");
        verify(documentService, never()).deleteAllFor(any());
        verify(notificationService, never()).deleteAllFor(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void aWrongPasswordIsRefused() {
        assertThatThrownBy(() -> service.deleteOwnAccount(customer, "wrong", true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("password is incorrect");
        assertThat(customer.isDeleted()).isFalse();
    }

    @Test
    void aMissingPasswordIsRefused() {
        assertThatThrownBy(() -> service.deleteOwnAccount(customer, null, true))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(customer.isDeleted()).isFalse();
    }

    @Test
    void theConfirmationBoxMustBeTicked() {
        assertThatThrownBy(() -> service.deleteOwnAccount(customer, "right-password", false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("tick the box");
        assertThat(customer.isDeleted()).isFalse();
    }

    @Test
    void anAlreadyDeletedAccountCannotBeDeletedTwice() {
        customer.setDeleted(true);

        assertThatThrownBy(() -> service.deleteOwnAccount(customer, "right-password", true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been deleted");
    }
}
