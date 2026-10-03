package com.dozernet.module1_customer.service;

import com.dozernet.common.audit.AuditService;
import com.dozernet.common.document.DocumentService;
import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.common.exception.ResourceNotFoundException;
import com.dozernet.common.model.Role;
import com.dozernet.common.notification.NotificationService;
import com.dozernet.common.user.User;
import com.dozernet.common.user.UserRepository;
import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module2_booking.entity.BookingStatus;
import com.dozernet.module2_booking.service.BookingService;
import com.dozernet.module6_payment.service.PaymentService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lets a customer delete their own account.
 *
 * <p>An account can only be deleted once the customer has no business left
 * behind: no pending or approved bookings and no unpaid invoices. Otherwise
 * the customer is told exactly what to finish first.</p>
 *
 * <p>Deleting <em>anonymises</em> the account instead of removing the row.
 * Past bookings, invoices and the audit trail reference the user, and
 * financial records must stay intact, so the personal details (name, email,
 * phone, NIC), uploaded documents and notifications are erased and the
 * account can never sign in again. The email, phone and NIC become free to
 * register again.</p>
 */
@Service
public class AccountDeletionService {

    /** What stands between a customer and deleting their account. */
    public record DeletionCheck(List<String> reasons) {

        public boolean allowed() {
            return reasons.isEmpty();
        }

        /** Customer-facing explanation, empty when deletion is allowed. */
        public String message() {
            if (allowed()) {
                return "";
            }
            return "You can't delete your account yet. Please finish the business you have left behind first: "
                    + String.join("; ", reasons) + ". Once these are done you can delete your account.";
        }
    }

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final DocumentService documentService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public AccountDeletionService(UserRepository userRepository,
                                  PasswordEncoder passwordEncoder,
                                  BookingService bookingService,
                                  PaymentService paymentService,
                                  DocumentService documentService,
                                  NotificationService notificationService,
                                  AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.documentService = documentService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    /** Works out whether the account can be deleted right now, and why not if it can't. */
    public DeletionCheck check(User user) {
        List<String> reasons = new ArrayList<>();

        if (user.getRoles().size() != 1 || !user.hasRole(Role.CUSTOMER)) {
            reasons.add("this account is also registered as "
                    + user.getRoles().stream()
                    .filter(role -> role != Role.CUSTOMER)
                    .map(role -> role.getDisplayName().toLowerCase())
                    .sorted()
                    .reduce((a, b) -> a + " and " + b)
                    .orElse("another role")
                    + ", so please ask an administrator to help you close it");
        }

        List<Booking> bookings = bookingService.forCustomer(user);
        long pending = countWithStatus(bookings, BookingStatus.PENDING);
        long approved = countWithStatus(bookings, BookingStatus.APPROVED);
        if (pending > 0) {
            reasons.add(pending == 1
                    ? "1 pending booking request (cancel it or wait for a decision)"
                    : pending + " pending booking requests (cancel them or wait for a decision)");
        }
        if (approved > 0) {
            reasons.add(approved == 1
                    ? "1 confirmed booking that hasn't been completed or cancelled"
                    : approved + " confirmed bookings that haven't been completed or cancelled");
        }

        long unpaid = paymentService.countUnpaidForCustomer(user);
        if (unpaid > 0) {
            reasons.add(unpaid + (unpaid == 1 ? " unpaid invoice" : " unpaid invoices") + " to settle");
        }
        return new DeletionCheck(List.copyOf(reasons));
    }

    /**
     * Deletes (anonymises) the customer's own account.
     *
     * @param confirmed whether the customer ticked the "I understand" box
     * @throws BusinessRuleException if the password is wrong, the box wasn't
     *                               ticked, or bookings/invoices are still open
     */
    @Transactional
    public void deleteOwnAccount(User current, String rawPassword, boolean confirmed) {
        User user = userRepository.findById(current.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("User", current.getId()));
        if (user.isDeleted()) {
            throw new BusinessRuleException("This account has already been deleted.");
        }
        if (!confirmed) {
            throw new BusinessRuleException("Please tick the box to confirm you want to delete your account.");
        }
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("Your password is incorrect. Your account was not deleted.");
        }

        DeletionCheck check = check(user);
        if (!check.allowed()) {
            throw new BusinessRuleException(check.message());
        }

        // Audit first, while the signed-in user is still the actor. Only the id is logged, no personal data.
        auditService.record("ACCOUNT_DELETED", "User", user.getId(), "Customer deleted their own account");

        documentService.deleteAllFor(user);
        notificationService.deleteAllFor(user);
        anonymise(user);
        userRepository.save(user);
    }

    private static long countWithStatus(List<Booking> bookings, BookingStatus status) {
        return bookings.stream().filter(b -> b.getStatus() == status).count();
    }

    /** Erases personal data and locks the account; unique fields get unique placeholders. */
    private void anonymise(User user) {
        String suffix = String.valueOf(user.getId());
        user.setFullName("Deleted customer");
        user.setEmail("deleted-" + suffix + "@deleted.invalid");
        user.setPhone("deleted-" + suffix);
        user.setIdentityCardNumber("DEL-" + suffix);
        // A random hash nobody knows, so the old password can never work again.
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setEnabled(false);
        user.setVerified(false);
        user.setContactUnreachable(false);
        user.setDeleted(true);
    }
}
