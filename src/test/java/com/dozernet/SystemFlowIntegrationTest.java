package com.dozernet;

import com.dozernet.common.audit.AuditLogRepository;
import com.dozernet.common.model.Role;
import com.dozernet.common.user.User;
import com.dozernet.common.user.UserRepository;
import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module2_booking.entity.BookingStatus;
import com.dozernet.module2_booking.repository.BookingRepository;
import com.dozernet.module3_fleet.entity.Machine;
import com.dozernet.module3_fleet.repository.MachineRepository;
import com.dozernet.module6_payment.entity.Invoice;
import com.dozernet.module6_payment.entity.InvoiceStatus;
import com.dozernet.module6_payment.repository.InvoiceRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end walk through the whole system on the H2 profile, driving the real
 * HTTP endpoints rather than the services directly: a customer books two
 * machines for one job, an admin approves and invoices them, the customer pays
 * (part, then the rest), an operator is assigned and runs the job to completion,
 * and the return inspection is filed - with the audit trail checked at the end.
 *
 * <p>Ordered because each step builds on the state the previous one left.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SystemFlowIntegrationTest {

    private static final String ADMIN = "admin@dozernet.lk";
    private static final String CUSTOMER = "customer@dozernet.lk";
    private static final String OPERATOR = "operator@dozernet.lk";

    @Autowired MockMvc mvc;
    @Autowired BookingRepository bookingRepository;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired MachineRepository machineRepository;
    @Autowired UserRepository userRepository;
    @Autowired AuditLogRepository auditLogRepository;

    private static Long firstBookingId;
    private static Long secondBookingId;
    private static Long invoiceId;

    private static final LocalDate START = LocalDate.now().plusDays(20);
    private static final LocalDate END = LocalDate.now().plusDays(22);

    @Test
    @Order(1)
    void publicCatalogueShowsTheNewestCategories() throws Exception {
        mvc.perform(get("/machines"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hauling")));

        mvc.perform(get("/machines").param("type", "MOTOR_GRADER"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Motor Grader")));
    }

    @Test
    @Order(2)
    void customerBooksTwoMachinesForOneJob() throws Exception {
        List<Machine> machines = machineRepository.findAll().stream()
                .filter(Machine::isBookable)
                .limit(2)
                .toList();
        assertThat(machines).hasSize(2);

        mvc.perform(post("/bookings/new/" + machines.get(0).getId())
                        .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf())
                        .param("startDate", START.toString())
                        .param("endDate", END.toString())
                        .param("jobSiteDistrict", "Jaffna")
                        .param("jobSiteAddress", "Site gate B, Palaly Road")
                        .param("alsoMachineIds", String.valueOf(machines.get(1).getId())))
                .andExpect(status().is3xxRedirection());

        List<Booking> created = bookingRepository.findAll();
        assertThat(created).hasSize(2);
        // Both bookings belong to the same request.
        assertThat(created.get(0).getBookingGroupId())
                .isNotNull()
                .isEqualTo(created.get(1).getBookingGroupId());

        firstBookingId = created.get(0).getId();
        secondBookingId = created.get(1).getId();
    }

    @Test
    @Order(3)
    void bookingsBeyondTheAdvanceWindowAreRefused() throws Exception {
        Machine machine = machineRepository.findAll().stream().filter(Machine::isBookable).findFirst().orElseThrow();
        LocalDate tooFar = LocalDate.now().plusDays(200);

        mvc.perform(post("/bookings/new/" + machine.getId())
                        .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf())
                        .param("startDate", tooFar.toString())
                        .param("endDate", tooFar.toString())
                        .param("jobSiteDistrict", "Colombo")
                        .param("jobSiteAddress", "Too far ahead"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("180 days in advance")));

        assertThat(bookingRepository.findAll()).hasSize(2);
    }

    @Test
    @Order(4)
    void adminApprovalRaisesAnItemisedNumberedInvoice() throws Exception {
        mvc.perform(post("/admin/bookings/" + firstBookingId + "/approve")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        Booking booking = bookingRepository.findById(firstBookingId).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.APPROVED);

        Invoice invoice = invoiceRepository.findByBooking(booking).orElseThrow();
        invoiceId = invoice.getId();

        assertThat(invoice.getInvoiceNumber())
                .isEqualTo("INV-" + java.time.Year.now().getValue() + "-00001");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
        // Jaffna is the farthest haulage band.
        assertThat(invoice.getTransportSurcharge()).isEqualByComparingTo("10000.00");
        // VAT is 18% of hire + haulage, and the total is the sum of all three.
        assertThat(invoice.getVatAmount())
                .isEqualByComparingTo(invoice.getSubTotal().multiply(new BigDecimal("0.18")));
        assertThat(invoice.getAmount()).isEqualByComparingTo(
                invoice.getBaseAmount().add(invoice.getTransportSurcharge()).add(invoice.getVatAmount()));
    }

    @Test
    @Order(5)
    void operatorCannotBeAssignedBeforeTheInvoiceIsPaid() throws Exception {
        User operator = userRepository.findByEmail(OPERATOR).orElseThrow();

        mvc.perform(post("/admin/assignments/assign")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf())
                        .param("bookingId", String.valueOf(firstBookingId))
                        .param("operatorId", String.valueOf(operator.getId())))
                .andExpect(status().is3xxRedirection());

        // Nothing was assigned: the booking is still waiting on payment.
        mvc.perform(get("/admin/assignments").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Operator assigned to the booking"))));
    }

    @Test
    @Order(6)
    void cardDetailsAreValidatedBeforeAnyMoneyIsRecorded() throws Exception {
        // Fails the Luhn check.
        mvc.perform(post("/customer/invoices/" + invoiceId + "/pay")
                        .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf())
                        .param("cardholderName", "Chamara Perera")
                        .param("cardNumber", "1234567812345678")
                        .param("expiry", "12/30")
                        .param("cvc", "123"))
                .andExpect(status().is3xxRedirection());

        assertThat(invoiceRepository.findById(invoiceId).orElseThrow().getAmountPaid())
                .isEqualByComparingTo("0.00");
    }

    @Test
    @Order(7)
    void customerPaysInPartThenInFull() throws Exception {
        String expiry = "12/" + String.valueOf(LocalDate.now().plusYears(2).getYear()).substring(2);

        mvc.perform(post("/customer/invoices/" + invoiceId + "/pay")
                        .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf())
                        .param("cardholderName", "Chamara Perera")
                        .param("cardNumber", "4242424242424242")
                        .param("expiry", expiry)
                        .param("cvc", "123")
                        .param("amount", "5000.00"))
                .andExpect(status().is3xxRedirection());

        Invoice partPaid = invoiceRepository.findById(invoiceId).orElseThrow();
        assertThat(partPaid.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
        assertThat(partPaid.getAmountPaid()).isEqualByComparingTo("5000.00");

        mvc.perform(post("/customer/invoices/" + invoiceId + "/pay")
                        .with(user(CUSTOMER).roles("CUSTOMER")).with(csrf())
                        .param("cardholderName", "Chamara Perera")
                        .param("cardNumber", "4242424242424242")
                        .param("expiry", expiry)
                        .param("cvc", "123"))
                .andExpect(status().is3xxRedirection());

        Invoice settled = invoiceRepository.findById(invoiceId).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(settled.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @Order(8)
    void operatorIsAssignedAndRunsTheJobToCompletion() throws Exception {
        User operator = userRepository.findByEmail(OPERATOR).orElseThrow();

        mvc.perform(post("/admin/assignments/assign")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf())
                        .param("bookingId", String.valueOf(firstBookingId))
                        .param("operatorId", String.valueOf(operator.getId())))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/operator/dashboard").with(user(OPERATOR).roles("OPERATOR")))
                .andExpect(status().isOk());

        // The assignment id is the only one in play at this point.
        long assignmentId = 1L;
        mvc.perform(post("/operator/assignments/" + assignmentId + "/status")
                        .with(user(OPERATOR).roles("OPERATOR")).with(csrf())
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/operator/assignments/" + assignmentId + "/status")
                        .with(user(OPERATOR).roles("OPERATOR")).with(csrf())
                        .param("status", "COMPLETED"))
                .andExpect(status().is3xxRedirection());

        assertThat(bookingRepository.findById(firstBookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    @Order(9)
    void returnInspectionRecordsTheDamageFound() throws Exception {
        mvc.perform(post("/admin/bookings/" + firstBookingId + "/inspection")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf())
                        .param("notes", "Returned with a full tank, 14 hours logged")
                        .param("damageReported", "true")
                        .param("damageDescription", "Rear left mudguard cracked")
                        .param("responsibleParty", "CUSTOMER")
                        .param("estimatedRepairCost", "8500"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/admin/inspections").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Rear left mudguard cracked")));
    }

    @Test
    @Order(10)
    void adminSwapsTheMachineOnTheRemainingBooking() throws Exception {
        mvc.perform(post("/admin/bookings/" + secondBookingId + "/approve")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        Booking booking = bookingRepository.findById(secondBookingId).orElseThrow();
        Long originalMachineId = booking.getMachine().getId();

        Machine replacement = machineRepository.findAll().stream()
                .filter(Machine::isBookable)
                .filter(m -> !m.getId().equals(originalMachineId))
                .filter(m -> bookingRepository.findOverlapping(m, START, END).isEmpty())
                .findFirst()
                .orElseThrow();

        mvc.perform(post("/admin/bookings/" + secondBookingId + "/replace")
                        .with(user(ADMIN).roles("ADMIN")).with(csrf())
                        .param("replacementMachineId", String.valueOf(replacement.getId()))
                        .param("reason", "Hydraulic fault found at the pre-hire check"))
                .andExpect(status().is3xxRedirection());

        Booking swapped = bookingRepository.findById(secondBookingId).orElseThrow();
        assertThat(swapped.getMachine().getId()).isEqualTo(replacement.getId());
        // The unpaid invoice follows the new machine's rate.
        Invoice reissued = invoiceRepository.findByBooking(swapped).orElseThrow();
        assertThat(reissued.getBaseAmount())
                .isEqualByComparingTo(replacement.getDailyRate().multiply(BigDecimal.valueOf(swapped.getDays())));
    }

    @Test
    @Order(11)
    void reportsExportAsCsvAndPdf() throws Exception {
        for (String key : List.of("revenue-by-month", "revenue-by-machine-type", "booking-counts",
                "fleet-utilisation", "maintenance", "customer-activity",
                "outstanding-payments", "owner-payouts")) {

            byte[] csv = mvc.perform(get("/admin/reports/" + key + ".csv").with(user(ADMIN).roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(csv).isNotEmpty();

            byte[] pdf = mvc.perform(get("/admin/reports/" + key + ".pdf").with(user(ADMIN).roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            // Every PDF starts with the %PDF- magic bytes.
            assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        }
    }

    @Test
    @Order(12)
    void everyAdminActionLandedInTheAuditTrail() throws Exception {
        List<String> actions = auditLogRepository.findAll().stream()
                .map(a -> a.getAction())
                .distinct()
                .toList();

        assertThat(actions).contains(
                "BOOKING_APPROVED", "INVOICE_ISSUED", "PAYMENT_RECORDED",
                "OPERATOR_ASSIGNED", "RETURN_INSPECTION_FILED", "MACHINE_REPLACED");

        mvc.perform(get("/admin/audit").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("BOOKING_APPROVED")));
    }

    @Test
    @Order(13)
    void rolesAreEnforcedAcrossTheSystem() throws Exception {
        // A customer cannot reach admin screens.
        mvc.perform(get("/admin/dashboard").with(user(CUSTOMER).roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        // An operator cannot approve bookings.
        mvc.perform(post("/admin/bookings/" + secondBookingId + "/approve")
                        .with(user(OPERATOR).roles("OPERATOR")).with(csrf()))
                .andExpect(status().isForbidden());
        // Signed-out visitors are sent to the login page.
        mvc.perform(get("/customer/dashboard"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @Order(14)
    void seededAccountsCarryTheirRole() {
        User admin = userRepository.findByEmail(ADMIN).orElseThrow();

        assertThat(admin.hasRole(Role.ADMIN)).isTrue();
        assertThat(admin.getPrimaryRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getRolesLabel()).isEqualTo("Administrator");
    }
}
