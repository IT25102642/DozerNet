package com.dozernet.module6_payment;

import com.dozernet.module2_booking.service.BookingService;
import com.dozernet.module3_fleet.service.FleetService;
import com.dozernet.module4_operator.service.OperatorService;
import com.dozernet.module5_maintenance.service.MaintenanceService;
import com.dozernet.module6_payment.entity.Invoice;
import com.dozernet.module6_payment.entity.InvoiceStatus;
import com.dozernet.module6_payment.service.AdminService;
import com.dozernet.module6_payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The revenue report and the dashboard must agree: cancelled invoices are void,
 * so they are neither "invoiced" nor "outstanding".
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceReportTest {

    @Mock BookingService bookingService;
    @Mock FleetService fleetService;
    @Mock OperatorService operatorService;
    @Mock MaintenanceService maintenanceService;
    @Mock PaymentService paymentService;

    @InjectMocks AdminService adminService;

    private static Invoice invoice(String amount, String paid, InvoiceStatus status) {
        Invoice invoice = new Invoice();
        invoice.setAmount(new BigDecimal(amount));
        invoice.setAmountPaid(new BigDecimal(paid));
        invoice.setStatus(status);
        return invoice;
    }

    @Test
    void revenueReportIgnoresCancelledInvoices() {
        when(paymentService.allInvoices()).thenReturn(List.of(
                invoice("10000.00", "10000.00", InvoiceStatus.PAID),
                invoice("5000.00", "2000.00", InvoiceStatus.PARTIALLY_PAID),
                invoice("7000.00", "0.00", InvoiceStatus.UNPAID),
                invoice("90000.00", "0.00", InvoiceStatus.CANCELLED)));

        AdminService.RevenueReport report = adminService.revenueReport();

        assertThat(report.totalInvoiced()).isEqualByComparingTo("22000.00");
        assertThat(report.totalCollected()).isEqualByComparingTo("12000.00");
        assertThat(report.outstanding()).isEqualByComparingTo("10000.00");
    }

    @Test
    void reportOutstandingMatchesDashboardOutstanding() {
        when(paymentService.allInvoices()).thenReturn(List.of(
                invoice("8000.00", "3000.00", InvoiceStatus.PARTIALLY_PAID),
                invoice("64000.00", "0.00", InvoiceStatus.CANCELLED)));

        assertThat(adminService.revenueReport().outstanding())
                .isEqualByComparingTo(adminService.dashboard().outstanding());
    }
}
