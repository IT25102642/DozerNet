package com.dozernet.module6_payment.service;

import com.dozernet.common.audit.AuditService;
import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.common.exception.ResourceNotFoundException;
import com.dozernet.common.model.Role;
import com.dozernet.common.notification.NotificationService;
import com.dozernet.common.user.User;
import com.dozernet.common.user.UserRepository;
import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module2_booking.entity.BookingStatus;
import com.dozernet.module2_booking.service.BookingService;
import com.dozernet.module6_payment.entity.Invoice;
import com.dozernet.module6_payment.entity.InvoiceStatus;
import com.dozernet.module6_payment.entity.Payment;
import com.dozernet.module6_payment.entity.PaymentMethod;
import com.dozernet.module6_payment.pricing.PricingBreakdown;
import com.dozernet.module6_payment.pricing.PricingSelector;
import com.dozernet.module6_payment.repository.InvoiceRepository;
import com.dozernet.module6_payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Payment &amp; billing: invoices are issued when a booking is approved (pay before
 * operator assignment). Pricing uses the Strategy pattern and every invoice is
 * itemised into hire charge, district haulage and VAT. Customers can pay in full
 * or in instalments through the demo card portal, or an admin can record
 * cash/bank payments offline. Cancelling a paid booking writes a refund back
 * against the invoice.
 */
@Service
public class PaymentService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final BookingService bookingService;
    private final PricingSelector pricingSelector;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public PaymentService(InvoiceRepository invoiceRepository,
                          PaymentRepository paymentRepository,
                          BookingService bookingService,
                          PricingSelector pricingSelector,
                          NotificationService notificationService,
                          UserRepository userRepository,
                          AuditService auditService) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.bookingService = bookingService;
        this.pricingSelector = pricingSelector;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Called right after a booking is approved. Creates an UNPAID invoice, syncs
     * booking.totalAmount, and notifies the customer (in-app + email Observer)
     * that the invoice was sent to their registered email.
     */
    @Transactional
    public Invoice createInvoiceForApprovedBooking(Booking booking) {
        if (booking.getStatus() != BookingStatus.APPROVED) {
            throw new BusinessRuleException("Invoices are issued for approved bookings.");
        }
        if (invoiceRepository.existsByBooking(booking)) {
            return invoiceRepository.findByBooking(booking)
                    .orElseThrow(() -> new BusinessRuleException("An invoice already exists for this booking."));
        }

        Invoice invoice = issueInvoice(booking);

        User customer = booking.getCustomer();
        String site = booking.getJobSiteLabel();
        String message = "Your booking for " + booking.getMachine().getModel()
                + " has been approved. Invoice " + invoice.getReference()
                + " - amount due: Rs. " + invoice.getAmount() + "."
                + " (Hire Rs. " + invoice.getBaseAmount()
                + " + transport Rs. " + invoice.getTransportSurcharge()
                + " + VAT Rs. " + invoice.getVatAmount() + ")."
                + (site == null || site.isBlank() ? "" : " Job site: " + site + ".")
                + " The invoice has been sent to " + customer.getEmail() + "."
                + " Please pay online to confirm the rental (Invoices → Pay now).";
        notificationService.notify(customer, "Booking approved — payment required", message);
        return invoice;
    }

    @Transactional
    public Invoice generateInvoice(Long bookingId) {
        Booking booking = bookingService.getById(bookingId);
        if (booking.getStatus() != BookingStatus.APPROVED
                && booking.getStatus() != BookingStatus.COMPLETED) {
            throw new BusinessRuleException(
                    "An invoice can only be generated for an approved or completed booking.");
        }
        if (invoiceRepository.existsByBooking(booking)) {
            throw new BusinessRuleException("An invoice already exists for this booking.");
        }

        Invoice invoice = issueInvoice(booking);

        User customer = booking.getCustomer();
        notificationService.notify(customer, "Invoice issued",
                "Invoice " + invoice.getReference() + " for Rs. " + invoice.getAmount()
                        + " (" + booking.getMachine().getModel() + ") has been sent to "
                        + customer.getEmail() + ".");
        return invoice;
    }

    /** Builds, numbers and stores the itemised invoice for a booking. */
    private Invoice issueInvoice(Booking booking) {
        PricingBreakdown breakdown = pricingSelector.priceBreakdown(booking);

        Invoice invoice = new Invoice(booking, booking.getCustomer(), breakdown.total());
        applyBreakdown(invoice, breakdown);
        invoice.setInvoiceNumber(nextInvoiceNumber());

        // The booking snapshot mirrors the hire charge, excluding haulage/VAT.
        booking.setTotalAmount(breakdown.baseAmount());

        Invoice saved = invoiceRepository.save(invoice);
        auditService.record("INVOICE_ISSUED", "Invoice", saved.getId(),
                saved.getReference() + " for booking #" + booking.getId()
                        + " - total Rs. " + saved.getAmount());
        return saved;
    }

    private static void applyBreakdown(Invoice invoice, PricingBreakdown breakdown) {
        invoice.setBaseAmount(breakdown.baseAmount());
        invoice.setTransportSurcharge(breakdown.transportSurcharge());
        invoice.setVatAmount(breakdown.vatAmount());
        invoice.setAmount(breakdown.total());
        invoice.setOwnerCommission(breakdown.ownerCommission());
    }

    /**
     * Next reference in the INV-YYYY-00001 series. The sequence restarts each
     * calendar year, which is what the client's accounts team expects.
     */
    private String nextInvoiceNumber() {
        String year = String.valueOf(Year.now().getValue());
        long issuedThisYear = invoiceRepository.countIssuedInYear(year);
        return String.format("INV-%s-%05d", year, issuedThisYear + 1);
    }

    /**
     * Re-prices an invoice that has not been paid yet - used when the machine on
     * a booking is swapped. Paid or part-paid invoices are left alone so nobody
     * is silently re-charged.
     *
     * @return true when the invoice was re-priced
     */
    @Transactional
    public boolean repriceUnpaidInvoiceForBooking(Booking booking) {
        Optional<Invoice> found = invoiceRepository.findByBooking(booking);
        if (found.isEmpty()) {
            return false;
        }
        Invoice invoice = found.get();
        if (invoice.getStatus() != InvoiceStatus.UNPAID) {
            return false;
        }
        PricingBreakdown breakdown = pricingSelector.priceBreakdown(booking);
        applyBreakdown(invoice, breakdown);
        booking.setTotalAmount(breakdown.baseAmount());
        invoiceRepository.save(invoice);
        auditService.record("INVOICE_REPRICED", "Invoice", invoice.getId(),
                invoice.getReference() + " re-priced to Rs. " + invoice.getAmount()
                        + " after a machine change");
        return true;
    }

    /** Recovery: issue invoices for approved/completed bookings that still lack one. */
    @Transactional
    public int generateForAllCompleted() {
        int created = 0;
        for (Booking b : bookingsWithoutInvoice()) {
            generateInvoice(b.getId());
            created++;
        }
        return created;
    }

    @Transactional
    public Payment recordPayment(Long invoiceId, BigDecimal amount, PaymentMethod method, String reference) {
        return recordPayment(invoiceId, amount, method, reference, true);
    }

    @Transactional
    public Payment recordPayment(Long invoiceId, BigDecimal amount, PaymentMethod method, String reference,
                                 boolean notifyCustomer) {
        Invoice invoice = getInvoice(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new BusinessRuleException("This invoice is already fully paid.");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessRuleException("This invoice was cancelled and cannot accept payments.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("Payment amount must be greater than zero.");
        }
        if (amount.compareTo(invoice.getBalance()) > 0) {
            throw new BusinessRuleException("Payment exceeds the outstanding balance of Rs. " + invoice.getBalance() + ".");
        }

        Payment payment = paymentRepository.save(new Payment(invoice, amount, method, reference));
        invoice.setAmountPaid(invoice.getAmountPaid().add(amount));
        boolean nowPaid = invoice.getBalance().signum() <= 0;
        invoice.setStatus(nowPaid ? InvoiceStatus.PAID : InvoiceStatus.PARTIALLY_PAID);
        invoiceRepository.save(invoice);

        auditService.record("PAYMENT_RECORDED", "Invoice", invoice.getId(),
                "Rs. " + amount + " via " + method.getDisplayName()
                        + " - balance now Rs. " + invoice.getBalance());

        if (notifyCustomer) {
            notificationService.notify(invoice.getCustomer(), "Payment recorded",
                    "A payment of Rs. " + amount + " was recorded against invoice "
                            + invoice.getReference() + ". Outstanding balance: Rs. "
                            + invoice.getBalance() + ".");
        }

        if (nowPaid) {
            notifyAdminsToAssignOperator(invoice);
        } else {
            remindOfOutstandingBalance(invoice);
        }
        return payment;
    }

    /**
     * Reminds the customer what is still owed after a part payment. The client
     * asked for reminders to continue until the balance is cleared, so this also
     * runs from {@link #remindAllOutstanding()} for the admin's follow-up sweep.
     */
    private void remindOfOutstandingBalance(Invoice invoice) {
        notificationService.notify(invoice.getCustomer(), "Balance still due",
                "Invoice " + invoice.getReference() + " has an outstanding balance of Rs. "
                        + invoice.getBalance() + " of Rs. " + invoice.getAmount()
                        + ". An operator can only be assigned once the invoice is settled in full.");
    }

    /**
     * Sends a balance reminder for every unpaid or part-paid invoice. Triggered
     * by the admin from the invoices screen.
     *
     * @return how many reminders were sent
     */
    @Transactional
    public int remindAllOutstanding() {
        List<Invoice> outstanding = invoiceRepository.findOutstanding();
        outstanding.forEach(this::remindOfOutstandingBalance);
        if (!outstanding.isEmpty()) {
            auditService.record("PAYMENT_REMINDERS_SENT", "Invoice", null,
                    outstanding.size() + " outstanding balance reminder(s) sent");
        }
        return outstanding.size();
    }

    /**
     * Refunds a cancelled booking according to the fraction the booking module's
     * cancellation policy decided, and closes the invoice.
     *
     * <p>The refund is written as a negative payment so the invoice ledger still
     * adds up and the money movement stays visible.</p>
     *
     * @return the amount actually refunded (zero when the policy allows none)
     */
    @Transactional
    public BigDecimal refundForCancellation(Booking booking, BigDecimal refundFraction) {
        Invoice invoice = invoiceRepository.findByBooking(booking)
                .orElseThrow(() -> new BusinessRuleException("This booking has no invoice to refund."));

        BigDecimal paid = invoice.getAmountPaid();
        if (paid.signum() <= 0) {
            invoice.setStatus(InvoiceStatus.CANCELLED);
            invoiceRepository.save(invoice);
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal refund = paid.multiply(refundFraction).setScale(2, RoundingMode.HALF_UP);
        if (refund.compareTo(paid) > 0) {
            refund = paid;
        }

        if (refund.signum() > 0) {
            BigDecimal charge = paid.subtract(refund);
            String reference = "REFUND for " + invoice.getReference()
                    + (charge.signum() > 0 ? " (cancellation charge Rs. " + charge + " retained)" : "");
            paymentRepository.save(new Payment(invoice, refund.negate(), PaymentMethod.REFUND, reference));
            invoice.setAmountPaid(paid.subtract(refund));
        }

        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoiceRepository.save(invoice);

        auditService.record("PAYMENT_REFUNDED", "Invoice", invoice.getId(),
                "Rs. " + refund + " refunded of Rs. " + paid + " paid on " + invoice.getReference());
        return refund;
    }

    private void notifyAdminsToAssignOperator(Invoice invoice) {
        Booking booking = invoice.getBooking();
        String title = "Payment received — assign an operator";
        String message = invoice.getCustomer().getFullName() + " paid for "
                + booking.getMachine().getModel()
                + " (" + booking.getStartDate() + " to " + booking.getEndDate() + "). "
                + "Please assign an operator now.";
        for (User admin : userRepository.findByRole(Role.ADMIN)) {
            notificationService.notify(admin, title, message);
        }
    }

    /**
     * Demo customer portal. The card details are validated exactly as a real
     * checkout would (Luhn check, expiry in the future, 3-digit CVV) but no bank
     * is contacted - the payment is simply recorded.
     *
     * @param amount how much to pay; null or the full balance settles the invoice,
     *               a smaller figure is recorded as a part payment
     */
    @Transactional
    public Payment recordCustomerPayment(Long invoiceId, User customer, CardPaymentRequest card,
                                         BigDecimal amount) {
        Invoice invoice = getInvoice(invoiceId);
        if (!invoice.getCustomer().getId().equals(customer.getId())) {
            throw ResourceNotFoundException.of("Invoice", invoiceId);
        }
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new BusinessRuleException("This invoice is already fully paid.");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessRuleException("This invoice was cancelled and cannot accept payments.");
        }

        card.validate();

        BigDecimal balance = invoice.getBalance();
        BigDecimal toPay = amount == null ? balance : amount.setScale(2, RoundingMode.HALF_UP);
        if (toPay.signum() <= 0) {
            throw new BusinessRuleException("Enter an amount greater than zero.");
        }
        if (toPay.compareTo(balance) > 0) {
            throw new BusinessRuleException(
                    "That is more than the outstanding balance of Rs. " + balance + ".");
        }

        String reference = "CARD-****" + card.last4() + " / " + card.cardholderName().trim();
        Payment payment = recordPayment(invoiceId, toPay, PaymentMethod.CARD, reference, false);

        BigDecimal remaining = getInvoice(invoiceId).getBalance();
        String message = remaining.signum() > 0
                ? "Thank you. Your part payment of Rs. " + toPay + " was received against invoice "
                + invoice.getReference() + ". Rs. " + remaining + " is still outstanding."
                : "Thank you. Your payment of Rs. " + toPay + " was received and invoice "
                + invoice.getReference() + " is now settled in full. "
                + "A confirmation was sent to " + customer.getEmail() + ".";
        notificationService.notify(customer, "Payment successful", message);
        return payment;
    }

    /** Cancels an unpaid invoice when an approved booking is revoked. */
    @Transactional
    public void voidUnpaidInvoiceForBooking(Booking booking) {
        invoiceRepository.findByBooking(booking).ifPresent(inv -> {
            if (inv.getStatus() == InvoiceStatus.PAID || inv.getStatus() == InvoiceStatus.PARTIALLY_PAID) {
                throw new BusinessRuleException("Cannot cancel a booking that has already been paid.");
            }
            if (inv.getStatus() != InvoiceStatus.CANCELLED) {
                inv.setStatus(InvoiceStatus.CANCELLED);
                invoiceRepository.save(inv);
            }
        });
    }

    // ---------- Reads ----------

    public Invoice getInvoice(Long id) {
        return invoiceRepository.findByIdWithDetails(id)
                .or(() -> invoiceRepository.findById(id))
                .orElseThrow(() -> ResourceNotFoundException.of("Invoice", id));
    }

    public Optional<Invoice> findByBooking(Booking booking) {
        return invoiceRepository.findByBooking(booking);
    }

    public boolean isInvoicePaidForBooking(Booking booking) {
        return invoiceRepository.findByBooking(booking)
                .map(i -> i.getStatus() == InvoiceStatus.PAID)
                .orElse(false);
    }

    public List<Invoice> allInvoices() {
        return invoiceRepository.findAllByOrderByIssuedDateDesc();
    }

    public List<Invoice> invoicesForCustomer(User customer) {
        return invoiceRepository.findByCustomerOrderByIssuedDateDesc(customer);
    }

    public long countUnpaidForCustomer(User customer) {
        return invoicesForCustomer(customer).stream()
                .filter(i -> i.getStatus() == InvoiceStatus.UNPAID
                        || i.getStatus() == InvoiceStatus.PARTIALLY_PAID)
                .count();
    }

    /** Map bookingId → unpaid invoice id for Pay now buttons. */
    public Map<Long, Long> unpaidInvoiceIdsByBooking(User customer) {
        Map<Long, Long> map = new LinkedHashMap<>();
        for (Invoice inv : invoicesForCustomer(customer)) {
            if (inv.getStatus() == InvoiceStatus.UNPAID || inv.getStatus() == InvoiceStatus.PARTIALLY_PAID) {
                map.putIfAbsent(inv.getBooking().getId(), inv.getId());
            }
        }
        return map;
    }

    /** Payment status labels for admin assignment screen (booking id → Paid/Unpaid/No invoice). */
    public Map<Long, String> paymentLabelsForBookings(List<Booking> bookings) {
        Map<Long, String> map = new LinkedHashMap<>();
        for (Booking b : bookings) {
            map.put(b.getId(), invoiceRepository.findByBooking(b)
                    .map(i -> i.getStatus() == InvoiceStatus.PAID ? "Paid" : "Unpaid")
                    .orElse("No invoice"));
        }
        return map;
    }

    public List<Payment> paymentsFor(Invoice invoice) {
        return paymentRepository.findByInvoiceOrderByPaidDateDesc(invoice);
    }

    public List<Booking> bookingsWithoutInvoice() {
        List<Booking> pending = new ArrayList<>();
        pending.addAll(bookingService.byStatus(BookingStatus.APPROVED).stream()
                .filter(b -> !invoiceRepository.existsByBooking(b))
                .toList());
        pending.addAll(bookingService.byStatus(BookingStatus.COMPLETED).stream()
                .filter(b -> !invoiceRepository.existsByBooking(b))
                .toList());
        return pending;
    }

    /** @deprecated Prefer {@link #bookingsWithoutInvoice()}; kept for older callers. */
    public List<Booking> completedBookingsWithoutInvoice() {
        return bookingsWithoutInvoice();
    }
}
