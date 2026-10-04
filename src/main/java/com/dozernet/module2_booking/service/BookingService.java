package com.dozernet.module2_booking.service;

import com.dozernet.common.audit.AuditService;
import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.common.exception.ResourceNotFoundException;
import com.dozernet.common.model.Role;
import com.dozernet.common.notification.NotificationService;
import com.dozernet.common.security.CurrentUserService;
import com.dozernet.common.user.User;
import com.dozernet.common.user.UserRepository;
import com.dozernet.module2_booking.CancellationPolicy;
import com.dozernet.module2_booking.SriLankaDistricts;
import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module2_booking.entity.BookingStatus;
import com.dozernet.module2_booking.entity.MachineReplacementLog;
import com.dozernet.module2_booking.repository.BookingRepository;
import com.dozernet.module2_booking.repository.MachineReplacementLogRepository;
import com.dozernet.module3_fleet.entity.Machine;
import com.dozernet.module3_fleet.service.FleetService;
import com.dozernet.module6_payment.service.PaymentService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Booking &amp; Rental Management business logic. Enforces the core rules that keep
 * the calendar conflict-free:
 *  - a machine must be available &amp; verified to be booked;
 *  - bookings cannot start in the past, must end on/after the start date, and
 *    cannot begin more than {@value #MAX_ADVANCE_DAYS} days ahead;
 *  - date ranges cannot overlap an existing pending/approved booking.
 * Approval also issues an unpaid invoice (pay before operator assignment), and
 * cancelling a paid booking refunds according to {@link CancellationPolicy}.
 */
@Service
public class BookingService {

    /** Client rule: same-day hires are fine, but nothing beyond six months out. */
    public static final int MAX_ADVANCE_DAYS = 180;

    private final BookingRepository bookingRepository;
    private final MachineReplacementLogRepository replacementLogRepository;
    private final FleetService fleetService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;

    public BookingService(BookingRepository bookingRepository,
                          MachineReplacementLogRepository replacementLogRepository,
                          FleetService fleetService,
                          NotificationService notificationService,
                          UserRepository userRepository,
                          @Lazy PaymentService paymentService,
                          AuditService auditService,
                          CurrentUserService currentUserService) {
        this.bookingRepository = bookingRepository;
        this.replacementLogRepository = replacementLogRepository;
        this.fleetService = fleetService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.paymentService = paymentService;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
    }

    // ---------- Create ----------

    @Transactional
    public Booking create(User customer, Long machineId, LocalDate startDate, LocalDate endDate,
                          String jobSiteDistrict, String jobSiteAddress) {
        return create(customer, machineId, startDate, endDate, jobSiteDistrict, jobSiteAddress, null);
    }

    private Booking create(User customer, Long machineId, LocalDate startDate, LocalDate endDate,
                           String jobSiteDistrict, String jobSiteAddress, String groupId) {
        Machine machine = fleetService.getById(machineId);
        validateDates(startDate, endDate);
        validateJobSite(jobSiteDistrict, jobSiteAddress);

        if (!machine.isBookable()) {
            throw new BusinessRuleException("This machine is not currently available for booking.");
        }

        List<Booking> clashes = bookingRepository.findOverlapping(machine, startDate, endDate);
        if (!clashes.isEmpty()) {
            throw new BusinessRuleException(
                    "Those dates are already booked for " + machine.getModel()
                            + ". Please choose a different range.");
        }

        Booking booking = new Booking(customer, machine, startDate, endDate);
        booking.setStatus(BookingStatus.PENDING);
        booking.setJobSiteDistrict(jobSiteDistrict.trim());
        booking.setJobSiteAddress(jobSiteAddress.trim());
        booking.setTotalAmount(machine.getDailyRate().multiply(BigDecimal.valueOf(booking.getDays())));
        booking.setBookingGroupId(groupId);
        Booking saved = bookingRepository.save(booking);

        notificationService.notify(customer, "Booking submitted",
                "Your booking request for " + machine.getModel() + " is pending admin approval.");
        notifyAdmins("New booking request",
                customer.getFullName() + " requested " + machine.getModel()
                        + " at " + saved.getJobSiteLabel()
                        + " (" + startDate + " to " + endDate + ").");
        return saved;
    }

    /**
     * Books several machines for the same job in one request. Each machine still
     * gets its own booking, invoice and operator assignment; they share a group
     * id so the customer and admin see them as one request.
     *
     * <p>All-or-nothing: if any machine in the request clashes, nothing is
     * booked, so a customer never ends up with half a job's worth of plant.</p>
     */
    @Transactional
    public List<Booking> createForMachines(User customer, List<Long> machineIds,
                                           LocalDate startDate, LocalDate endDate,
                                           String jobSiteDistrict, String jobSiteAddress) {
        if (machineIds == null || machineIds.isEmpty()) {
            throw new BusinessRuleException("Select at least one machine to book.");
        }
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(machineIds));
        if (distinctIds.size() == 1) {
            return List.of(create(customer, distinctIds.get(0), startDate, endDate,
                    jobSiteDistrict, jobSiteAddress, null));
        }

        String groupId = UUID.randomUUID().toString();
        List<Booking> created = new ArrayList<>();
        for (Long machineId : distinctIds) {
            created.add(create(customer, machineId, startDate, endDate,
                    jobSiteDistrict, jobSiteAddress, groupId));
        }
        return created;
    }

    public void validateJobSite(String district, String address) {
        if (district == null || district.isBlank()) {
            throw new BusinessRuleException("Select the district where the machine will work.");
        }
        if (!SriLankaDistricts.isValid(district.trim())) {
            throw new BusinessRuleException("Select a valid Sri Lanka district.");
        }
        if (address == null || address.isBlank()) {
            throw new BusinessRuleException("Enter the job site address or landmark.");
        }
        if (address.trim().length() > 255) {
            throw new BusinessRuleException("Job site address must be at most 255 characters.");
        }
    }

    /** Shared date-rule validation (also reused by tests). */
    public void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessRuleException("Start and end dates are required.");
        }
        if (startDate.isBefore(LocalDate.now())) {
            throw new BusinessRuleException("Start date cannot be in the past.");
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessRuleException("End date must be on or after the start date.");
        }
        if (startDate.isAfter(LocalDate.now().plusDays(MAX_ADVANCE_DAYS))) {
            throw new BusinessRuleException(
                    "Bookings can be made up to " + MAX_ADVANCE_DAYS + " days in advance.");
        }
    }

    // ---------- State transitions ----------

    @Transactional
    public void cancel(User customer, Long bookingId) {
        Booking b = getById(bookingId);
        if (!b.getCustomer().getId().equals(customer.getId())) {
            throw new BusinessRuleException("You can only cancel your own bookings.");
        }
        cancelInternal(b, "Your booking for " + b.getMachine().getModel() + " was cancelled.");
    }

    /**
     * Admin cancels an approved booking, freeing the calendar so the machine can
     * be booked again. A paid booking is refunded under {@link CancellationPolicy}.
     */
    @Transactional
    public void cancelApproved(Long bookingId) {
        Booking b = getById(bookingId);
        if (b.getStatus() != BookingStatus.APPROVED) {
            throw new BusinessRuleException("Only approved bookings can be cancelled this way.");
        }
        cancelInternal(b, "Your approved booking for " + b.getMachine().getModel()
                + " was cancelled by the administrator.");
    }

    private void cancelInternal(Booking b, String customerMessage) {
        if (b.getStatus() == BookingStatus.PENDING) {
            // Pending requests can always be withdrawn, free of charge.
            b.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(b);
            auditService.record("BOOKING_CANCELLED", "Booking", b.getId(),
                    "Pending request withdrawn - no charge");
            notificationService.notify(b.getCustomer(), "Booking cancelled", customerMessage);
            return;
        }

        if (b.getStatus() != BookingStatus.APPROVED) {
            throw new BusinessRuleException("Only pending or approved bookings can be cancelled.");
        }

        if (!paymentService.isInvoicePaidForBooking(b)) {
            // Approved but unpaid: allowed right up to the day the hire starts.
            if (!LocalDate.now().isBefore(b.getStartDate())) {
                throw new BusinessRuleException(
                        "An unpaid booking can only be cancelled before its start date. "
                                + "Please contact DozerNet support.");
            }
            paymentService.voidUnpaidInvoiceForBooking(b);
            b.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(b);
            auditService.record("BOOKING_CANCELLED", "Booking", b.getId(),
                    "Approved but unpaid - invoice voided");
            notificationService.notify(b.getCustomer(), "Booking cancelled",
                    customerMessage + " The invoice is no longer due.");
            return;
        }

        // Paid: apply the refund tiers and record the money movement in module 6.
        CancellationPolicy.Outcome outcome = CancellationPolicy.evaluate(b.getStartDate());
        BigDecimal refunded = paymentService.refundForCancellation(b, outcome.refundFraction());

        b.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(b);
        auditService.record("BOOKING_CANCELLED", "Booking", b.getId(),
                "Paid booking cancelled - " + outcome.description() + " Refunded: " + refunded);
        notificationService.notify(b.getCustomer(), "Booking cancelled",
                customerMessage + " " + outcome.description()
                        + (refunded.signum() > 0
                        ? " A refund of Rs. " + refunded + " has been recorded against your invoice."
                        : ""));
    }

    /**
     * Preview of what cancelling a paid booking would cost, so the customer can
     * be shown the charge before they confirm.
     */
    public CancellationPolicy.Outcome cancellationPreview(Booking booking) {
        return CancellationPolicy.evaluate(booking.getStartDate());
    }

    @Transactional
    public void approve(Long bookingId) {
        Booking b = getById(bookingId);
        if (b.getStatus() != BookingStatus.PENDING) {
            throw new BusinessRuleException("Only pending bookings can be approved.");
        }
        // Defensive re-check: ensure no approved booking overlaps this one.
        boolean approvedClash = bookingRepository.findOverlapping(b.getMachine(), b.getStartDate(), b.getEndDate())
                .stream()
                .anyMatch(o -> !o.getId().equals(b.getId()) && o.getStatus() == BookingStatus.APPROVED);
        if (approvedClash) {
            throw new BusinessRuleException("Another approved booking overlaps these dates.");
        }
        b.setStatus(BookingStatus.APPROVED);
        bookingRepository.save(b);
        // Issues UNPAID invoice and notifies customer (invoice emailed to their address).
        paymentService.createInvoiceForApprovedBooking(b);
        auditService.record("BOOKING_APPROVED", "Booking", b.getId(),
                b.getMachine().getModel() + " for " + b.getCustomer().getFullName()
                        + " (" + b.getStartDate() + " to " + b.getEndDate() + ")");
    }

    @Transactional
    public void reject(Long bookingId) {
        Booking b = getById(bookingId);
        if (b.getStatus() != BookingStatus.PENDING) {
            throw new BusinessRuleException("Only pending bookings can be rejected.");
        }
        b.setStatus(BookingStatus.REJECTED);
        bookingRepository.save(b);
        auditService.record("BOOKING_REJECTED", "Booking", b.getId(),
                b.getMachine().getModel() + " for " + b.getCustomer().getFullName());
        notificationService.notify(b.getCustomer(), "Booking rejected",
                "Your booking for " + b.getMachine().getModel() + " was not approved.");
    }

    /**
     * Substitutes a different machine on an existing booking when the original
     * becomes unavailable (breakdown, maintenance, damage). The swap is logged
     * and the customer is told, so the change is never silent.
     */
    @Transactional
    public MachineReplacementLog replaceMachine(Long bookingId, Long replacementMachineId, String reason) {
        Booking b = getById(bookingId);
        if (b.getStatus() != BookingStatus.PENDING && b.getStatus() != BookingStatus.APPROVED) {
            throw new BusinessRuleException("Only pending or approved bookings can have their machine changed.");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("Give a reason for the machine replacement.");
        }

        Machine previous = b.getMachine();
        Machine replacement = fleetService.getById(replacementMachineId);
        if (previous.getId().equals(replacement.getId())) {
            throw new BusinessRuleException("Select a different machine as the replacement.");
        }
        if (!replacement.isBookable()) {
            throw new BusinessRuleException("The replacement machine is not available for booking.");
        }

        boolean replacementClash = bookingRepository
                .findOverlapping(replacement, b.getStartDate(), b.getEndDate())
                .stream()
                .anyMatch(other -> !other.getId().equals(b.getId()));
        if (replacementClash) {
            throw new BusinessRuleException(
                    "The replacement machine is already booked for those dates.");
        }

        b.setMachine(replacement);
        b.setTotalAmount(replacement.getDailyRate().multiply(BigDecimal.valueOf(b.getDays())));
        bookingRepository.save(b);

        // An invoice that has not been paid yet is re-priced for the new machine;
        // an already-paid invoice is left untouched so the customer is never
        // silently re-charged after the fact.
        boolean invoiceRepriced = paymentService.repriceUnpaidInvoiceForBooking(b);

        MachineReplacementLog logEntry = replacementLogRepository.save(new MachineReplacementLog(
                b, previous, replacement, currentUserService.current().orElse(null), reason.trim()));

        auditService.record("MACHINE_REPLACED", "Booking", b.getId(),
                previous.getModel() + " replaced by " + replacement.getModel() + " - " + reason.trim());
        notificationService.notify(b.getCustomer(), "Machine changed for your booking",
                "The " + previous.getModel() + " booked for " + b.getStartDate()
                        + " has been replaced with a " + replacement.getModel()
                        + ". Reason: " + reason.trim()
                        + (invoiceRepriced
                        ? " Your invoice has been updated to match the new daily rate."
                        : " Your existing invoice is unchanged."));
        return logEntry;
    }

    public List<MachineReplacementLog> replacementHistory(Booking booking) {
        return replacementLogRepository.findByBookingOrderByCreatedAtDesc(booking);
    }

    /** Called by the Operator module when the assigned job is completed. */
    @Transactional
    public void markCompleted(Long bookingId) {
        Booking b = getById(bookingId);
        if (b.getStatus() != BookingStatus.APPROVED) {
            throw new BusinessRuleException("Only approved bookings can be completed.");
        }
        b.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(b);
        notificationService.notify(b.getCustomer(), "Job completed",
                "Your rental of " + b.getMachine().getModel() + " is complete. Thank you for using DozerNet.");
    }

    // ---------- Reads ----------

    public Booking getById(Long id) {
        return bookingRepository.findByIdWithDetails(id)
                .or(() -> bookingRepository.findById(id))
                .orElseThrow(() -> ResourceNotFoundException.of("Booking", id));
    }

    public List<Booking> forCustomer(User customer) {
        return bookingRepository.findByCustomerOrderByStartDateDesc(customer);
    }

    public List<Booking> all() {
        return bookingRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Booking> byStatus(BookingStatus status) {
        return bookingRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public List<Booking> approvedBookings() {
        return bookingRepository.findByStatusOrderByCreatedAtDesc(BookingStatus.APPROVED);
    }

    public long countByStatus(BookingStatus status) {
        return bookingRepository.countByStatus(status);
    }

    /** Approved rentals whose window includes today — for admin fleet whereabouts. */
    public List<Booking> activeDeploymentsToday() {
        return bookingRepository.findActiveDeploymentsOn(LocalDate.now());
    }

    /** Approved/completed rentals overlapping a window — used by the utilisation report. */
    public List<Booking> activeBetween(LocalDate from, LocalDate to) {
        return bookingRepository.findActiveBetween(from, to);
    }

    private void notifyAdmins(String title, String message) {
        for (User admin : userRepository.findByRole(Role.ADMIN)) {
            notificationService.notify(admin, title, message);
        }
    }
}
