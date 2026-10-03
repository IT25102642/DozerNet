package com.dozernet.module6_payment.service;

import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module2_booking.entity.BookingStatus;
import com.dozernet.module2_booking.service.BookingService;
import com.dozernet.module3_fleet.service.FleetService;
import com.dozernet.module4_operator.service.OperatorService;
import com.dozernet.module5_maintenance.service.MaintenanceService;
import com.dozernet.module6_payment.entity.Invoice;
import com.dozernet.module6_payment.entity.InvoiceStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Administration aggregation across all modules: dashboard summary and the
 * booking/revenue report.
 */
@Service
public class AdminService {

    private final BookingService bookingService;
    private final FleetService fleetService;
    private final OperatorService operatorService;
    private final MaintenanceService maintenanceService;
    private final PaymentService paymentService;

    public AdminService(BookingService bookingService,
                        FleetService fleetService,
                        OperatorService operatorService,
                        MaintenanceService maintenanceService,
                        PaymentService paymentService) {
        this.bookingService = bookingService;
        this.fleetService = fleetService;
        this.operatorService = operatorService;
        this.maintenanceService = maintenanceService;
        this.paymentService = paymentService;
    }

    public record DashboardStats(long pendingBookings, long pendingListings, long pendingOperators,
                                 long totalMachines, long scheduledMaintenance, long unpaidInvoices,
                                 long awaitingOperatorAssignment,
                                 BigDecimal revenueCollected, BigDecimal outstanding,
                                 long machinesDueForService) {
    }

    public record RevenueReport(BigDecimal totalInvoiced, BigDecimal totalCollected, BigDecimal outstanding,
                                Map<BookingStatus, Long> bookingCounts,
                                Map<InvoiceStatus, Long> invoiceCounts) {
    }

    public DashboardStats dashboard() {
        List<Invoice> invoices = paymentService.allInvoices();
        BigDecimal collected = invoices.stream().map(Invoice::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = invoices.stream()
                .filter(i -> i.getStatus() != InvoiceStatus.CANCELLED)
                .map(Invoice::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long unpaid = invoices.stream()
                .filter(i -> i.getStatus() == InvoiceStatus.UNPAID || i.getStatus() == InvoiceStatus.PARTIALLY_PAID)
                .count();

        return new DashboardStats(
                bookingService.countByStatus(BookingStatus.PENDING),
                fleetService.pendingApprovals().size(),
                operatorService.pendingVerifications().size(),
                fleetService.findAll().size(),
                maintenanceService.countScheduled(),
                unpaid,
                operatorService.countPaidAwaitingOperator(),
                collected,
                outstanding,
                maintenanceService.countDueForService());
    }

    /** Paid bookings still waiting for an operator — for the dashboard alert. */
    public List<Booking> paidAwaitingOperator() {
        return operatorService.paidBookingsAwaitingOperator();
    }

    /** Machines currently out on approved rentals that include today. */
    public List<Booking> fleetWhereabouts() {
        return bookingService.activeDeploymentsToday();
    }

    public RevenueReport revenueReport() {
        List<Invoice> invoices = paymentService.allInvoices();
        BigDecimal totalInvoiced = invoices.stream().map(Invoice::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collected = invoices.stream().map(Invoice::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = totalInvoiced.subtract(collected);

        Map<BookingStatus, Long> bookingCounts = new LinkedHashMap<>();
        for (BookingStatus s : BookingStatus.values()) {
            bookingCounts.put(s, bookingService.countByStatus(s));
        }
        Map<InvoiceStatus, Long> invoiceCounts = new LinkedHashMap<>();
        for (InvoiceStatus s : InvoiceStatus.values()) {
            invoiceCounts.put(s, invoices.stream().filter(i -> i.getStatus() == s).count());
        }

        return new RevenueReport(totalInvoiced, collected, outstanding, bookingCounts, invoiceCounts);
    }
}
