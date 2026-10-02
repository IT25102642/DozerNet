package com.dozernet.module1_customer.service;

import com.dozernet.common.model.Role;
import com.dozernet.common.user.User;
import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module2_booking.entity.BookingStatus;
import com.dozernet.module2_booking.service.BookingService;
import com.dozernet.module6_payment.service.PaymentService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Rules for a customer deleting their own account.
 *
 * <p>An account can only be deleted once the customer has no business left
 * behind: no pending or approved bookings and no unpaid invoices. Otherwise
 * the customer is told exactly what to finish first.</p>
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

    private final BookingService bookingService;
    private final PaymentService paymentService;

    public AccountDeletionService(BookingService bookingService, PaymentService paymentService) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
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

    private static long countWithStatus(List<Booking> bookings, BookingStatus status) {
        return bookings.stream().filter(b -> b.getStatus() == status).count();
    }
}
