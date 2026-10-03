package com.dozernet.module6_payment.report;

import com.dozernet.common.exception.ResourceNotFoundException;
import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module2_booking.entity.BookingStatus;
import com.dozernet.module2_booking.service.BookingService;
import com.dozernet.module3_fleet.entity.Machine;
import com.dozernet.module3_fleet.entity.Ownership;
import com.dozernet.module3_fleet.service.FleetService;
import com.dozernet.module5_maintenance.entity.MaintenanceRecord;
import com.dozernet.module5_maintenance.service.MaintenanceService;
import com.dozernet.module6_payment.entity.Invoice;
import com.dozernet.module6_payment.entity.InvoiceStatus;
import com.dozernet.module6_payment.service.PaymentService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the administrator's reports. Every report is produced as a
 * {@link ReportTable} so the same data can be shown on screen, downloaded as
 * CSV, or downloaded as PDF without being computed three different ways.
 */
@Service
public class ReportService {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");

    private final PaymentService paymentService;
    private final BookingService bookingService;
    private final FleetService fleetService;
    private final MaintenanceService maintenanceService;

    public ReportService(PaymentService paymentService,
                         BookingService bookingService,
                         FleetService fleetService,
                         MaintenanceService maintenanceService) {
        this.paymentService = paymentService;
        this.bookingService = bookingService;
        this.fleetService = fleetService;
        this.maintenanceService = maintenanceService;
    }

    /** Every report the admin can open, in menu order. */
    public List<ReportTable> allReports() {
        return List.of(
                revenueByMonth(),
                revenueByMachineType(),
                bookingCounts(),
                fleetUtilisation(),
                maintenanceReport(),
                customerActivity(),
                outstandingPayments(),
                ownerPayouts());
    }

    public ReportTable byKey(String key) {
        return allReports().stream()
                .filter(r -> r.key().equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No report named '" + key + "'"));
    }

    // ---------- 1. Revenue by month ----------

    public ReportTable revenueByMonth() {
        Map<YearMonth, BigDecimal[]> byMonth = new LinkedHashMap<>();
        for (Invoice invoice : sortedInvoices()) {
            if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
                continue;
            }
            YearMonth month = YearMonth.from(invoice.getIssuedDate());
            BigDecimal[] cells = byMonth.computeIfAbsent(month,
                    m -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            cells[0] = cells[0].add(invoice.getAmount());
            cells[1] = cells[1].add(invoice.getAmountPaid());
            cells[2] = cells[2].add(invoice.getBalance());
        }

        List<List<String>> rows = new ArrayList<>();
        BigDecimal invoiced = BigDecimal.ZERO;
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        for (Map.Entry<YearMonth, BigDecimal[]> entry : byMonth.entrySet()) {
            BigDecimal[] cells = entry.getValue();
            rows.add(List.of(entry.getKey().format(MONTH_LABEL),
                    money(cells[0]), money(cells[1]), money(cells[2])));
            invoiced = invoiced.add(cells[0]);
            collected = collected.add(cells[1]);
            outstanding = outstanding.add(cells[2]);
        }

        return new ReportTable("revenue-by-month", "Revenue by month",
                "Invoiced, collected and outstanding amounts for each month.",
                List.of("Month", "Invoiced (Rs.)", "Collected (Rs.)", "Outstanding (Rs.)"),
                rows,
                List.of("Total", money(invoiced), money(collected), money(outstanding)));
    }

    // ---------- 2. Revenue by machine type ----------

    public ReportTable revenueByMachineType() {
        Map<String, BigDecimal[]> byType = new LinkedHashMap<>();
        for (Invoice invoice : sortedInvoices()) {
            if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
                continue;
            }
            String type = invoice.getBooking().getMachine().getType().getDisplayName();
            BigDecimal[] cells = byType.computeIfAbsent(type,
                    t -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            cells[0] = cells[0].add(BigDecimal.ONE);
            cells[1] = cells[1].add(invoice.getAmount());
            cells[2] = cells[2].add(invoice.getAmountPaid());
        }

        List<List<String>> rows = new ArrayList<>();
        BigDecimal hires = BigDecimal.ZERO;
        BigDecimal invoiced = BigDecimal.ZERO;
        BigDecimal collected = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal[]> entry : byType.entrySet()) {
            BigDecimal[] cells = entry.getValue();
            rows.add(List.of(entry.getKey(), cells[0].toBigInteger().toString(),
                    money(cells[1]), money(cells[2])));
            hires = hires.add(cells[0]);
            invoiced = invoiced.add(cells[1]);
            collected = collected.add(cells[2]);
        }

        return new ReportTable("revenue-by-machine-type", "Revenue by machine type",
                "Which categories of plant are earning the most.",
                List.of("Machine type", "Hires", "Invoiced (Rs.)", "Collected (Rs.)"),
                rows,
                List.of("Total", hires.toBigInteger().toString(), money(invoiced), money(collected)));
    }

    // ---------- 3. Booking counts ----------

    public ReportTable bookingCounts() {
        List<List<String>> rows = new ArrayList<>();
        long total = 0;
        for (BookingStatus status : BookingStatus.values()) {
            long count = bookingService.countByStatus(status);
            rows.add(List.of(status.getDisplayName(), String.valueOf(count)));
            total += count;
        }
        return new ReportTable("booking-counts", "Booking counts",
                "How many booking requests are sitting in each state.",
                List.of("Status", "Bookings"), rows,
                List.of("Total", String.valueOf(total)));
    }

    // ---------- 4. Fleet utilisation ----------

    /**
     * Utilisation over the last 90 days: hired days as a percentage of the
     * window, so an idle machine is obvious at a glance.
     */
    public ReportTable fleetUtilisation() {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(89);
        long windowDays = ChronoUnit.DAYS.between(from, to) + 1;

        Map<Long, long[]> hiredDaysByMachine = new LinkedHashMap<>();
        for (Booking booking : bookingService.activeBetween(from, to)) {
            LocalDate overlapStart = booking.getStartDate().isBefore(from) ? from : booking.getStartDate();
            LocalDate overlapEnd = booking.getEndDate().isAfter(to) ? to : booking.getEndDate();
            long days = ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1;
            long[] cells = hiredDaysByMachine.computeIfAbsent(booking.getMachine().getId(), id -> new long[]{0, 0});
            cells[0] += Math.max(days, 0);
            cells[1] += 1;
        }

        List<List<String>> rows = new ArrayList<>();
        for (Machine machine : fleetService.findAll()) {
            long[] cells = hiredDaysByMachine.getOrDefault(machine.getId(), new long[]{0, 0});
            double utilisation = windowDays == 0 ? 0 : (cells[0] * 100.0) / windowDays;
            rows.add(List.of(
                    machine.getModel(),
                    machine.getRegistrationNumber(),
                    machine.getStatus().getDisplayName(),
                    String.valueOf(cells[1]),
                    String.valueOf(cells[0]),
                    String.format("%.1f%%", utilisation)));
        }
        rows.sort(Comparator.comparing((List<String> r) -> r.get(5)).reversed());

        return new ReportTable("fleet-utilisation", "Fleet utilisation",
                "Hired days per machine over the last " + windowDays + " days.",
                List.of("Machine", "Registration", "Status", "Hires", "Hired days", "Utilisation"),
                rows, List.of());
    }

    // ---------- 5. Maintenance ----------

    public ReportTable maintenanceReport() {
        List<List<String>> rows = new ArrayList<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        for (MaintenanceRecord record : maintenanceService.all()) {
            BigDecimal cost = record.getCost() == null ? BigDecimal.ZERO : record.getCost();
            totalCost = totalCost.add(cost);
            rows.add(List.of(
                    record.getMachine().getModel(),
                    record.getMachine().getRegistrationNumber(),
                    String.valueOf(record.getScheduledDate()),
                    record.getCompletedDate() == null ? "-" : String.valueOf(record.getCompletedDate()),
                    record.getStatus().getDisplayName(),
                    blankToDash(record.getPartsUsed()),
                    money(cost)));
        }
        return new ReportTable("maintenance", "Maintenance report",
                "Every scheduled and completed service, with parts and cost.",
                List.of("Machine", "Registration", "Scheduled", "Completed", "Status", "Parts", "Cost (Rs.)"),
                rows,
                List.of("Total", "", "", "", "", "", money(totalCost)));
    }

    // ---------- 6. Customer activity ----------

    public ReportTable customerActivity() {
        record Activity(String name, String email, int bookings, BigDecimal invoiced, BigDecimal paid) {
        }
        Map<Long, Activity> byCustomer = new LinkedHashMap<>();

        for (Invoice invoice : sortedInvoices()) {
            if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
                continue;
            }
            Long id = invoice.getCustomer().getId();
            Activity current = byCustomer.get(id);
            if (current == null) {
                byCustomer.put(id, new Activity(invoice.getCustomer().getFullName(),
                        invoice.getCustomer().getEmail(), 1, invoice.getAmount(), invoice.getAmountPaid()));
            } else {
                byCustomer.put(id, new Activity(current.name(), current.email(),
                        current.bookings() + 1,
                        current.invoiced().add(invoice.getAmount()),
                        current.paid().add(invoice.getAmountPaid())));
            }
        }

        List<List<String>> rows = byCustomer.values().stream()
                .sorted(Comparator.comparing(Activity::invoiced).reversed())
                .map(a -> List.of(a.name(), a.email(), String.valueOf(a.bookings()),
                        money(a.invoiced()), money(a.paid()),
                        money(a.invoiced().subtract(a.paid()))))
                .toList();

        return new ReportTable("customer-activity", "Customer activity",
                "Bookings and spend per customer, biggest first.",
                List.of("Customer", "Email", "Bookings", "Invoiced (Rs.)", "Paid (Rs.)", "Owing (Rs.)"),
                rows, List.of());
    }

    // ---------- 7. Outstanding payments ----------

    public ReportTable outstandingPayments() {
        List<List<String>> rows = new ArrayList<>();
        BigDecimal owing = BigDecimal.ZERO;
        for (Invoice invoice : sortedInvoices()) {
            if (invoice.getStatus() != InvoiceStatus.UNPAID
                    && invoice.getStatus() != InvoiceStatus.PARTIALLY_PAID) {
                continue;
            }
            long ageDays = ChronoUnit.DAYS.between(invoice.getIssuedDate(), LocalDate.now());
            owing = owing.add(invoice.getBalance());
            rows.add(List.of(
                    invoice.getReference(),
                    invoice.getCustomer().getFullName(),
                    invoice.getBooking().getMachine().getModel(),
                    String.valueOf(invoice.getIssuedDate()),
                    ageDays + " days",
                    money(invoice.getAmount()),
                    money(invoice.getAmountPaid()),
                    money(invoice.getBalance())));
        }
        return new ReportTable("outstanding-payments", "Outstanding payments",
                "Invoices still unpaid or part paid, oldest first.",
                List.of("Invoice", "Customer", "Machine", "Issued", "Age", "Total (Rs.)", "Paid (Rs.)", "Balance (Rs.)"),
                rows,
                List.of("Total owing", "", "", "", "", "", "", money(owing)));
    }

    // ---------- 8. Owner payouts / commission ----------

    public ReportTable ownerPayouts() {
        record Payout(String owner, int hires, BigDecimal hireCharges, BigDecimal commission) {
        }
        Map<Long, Payout> byOwner = new LinkedHashMap<>();

        for (Invoice invoice : sortedInvoices()) {
            if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
                continue;
            }
            Machine machine = invoice.getBooking().getMachine();
            if (machine.getOwnership() != Ownership.PRIVATE || machine.getOwner() == null) {
                continue;
            }
            Long ownerId = machine.getOwner().getId();
            Payout current = byOwner.get(ownerId);
            if (current == null) {
                byOwner.put(ownerId, new Payout(machine.getOwner().getFullName(), 1,
                        invoice.getBaseAmount(), invoice.getOwnerCommission()));
            } else {
                byOwner.put(ownerId, new Payout(current.owner(), current.hires() + 1,
                        current.hireCharges().add(invoice.getBaseAmount()),
                        current.commission().add(invoice.getOwnerCommission())));
            }
        }

        List<List<String>> rows = new ArrayList<>();
        BigDecimal charges = BigDecimal.ZERO;
        BigDecimal commission = BigDecimal.ZERO;
        for (Payout payout : byOwner.values()) {
            rows.add(List.of(payout.owner(), String.valueOf(payout.hires()),
                    money(payout.hireCharges()), money(payout.commission()),
                    money(payout.hireCharges().subtract(payout.commission()))));
            charges = charges.add(payout.hireCharges());
            commission = commission.add(payout.commission());
        }

        return new ReportTable("owner-payouts", "Private owner payouts",
                "Hire charges earned on private machines, DozerNet's 10% commission, and what is owed to each owner.",
                List.of("Owner", "Hires", "Hire charges (Rs.)", "Commission (Rs.)", "Payout due (Rs.)"),
                rows,
                List.of("Total", "", money(charges), money(commission), money(charges.subtract(commission))));
    }

    // ---------- helpers ----------

    private List<Invoice> sortedInvoices() {
        return paymentService.allInvoices().stream()
                .sorted(Comparator.comparing(Invoice::getIssuedDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static String money(BigDecimal value) {
        return Optional.ofNullable(value).orElse(BigDecimal.ZERO).toPlainString();
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
