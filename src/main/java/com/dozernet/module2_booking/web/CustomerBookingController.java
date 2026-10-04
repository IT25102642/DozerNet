package com.dozernet.module2_booking.web;

import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.common.security.CurrentUserService;
import com.dozernet.module2_booking.SriLankaDistricts;
import com.dozernet.module2_booking.dto.BookingForm;
import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module2_booking.service.BookingService;
import com.dozernet.module3_fleet.service.FleetService;
import com.dozernet.module6_payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * Customer-facing booking flow: request a booking, view history, cancel.
 */
@Controller
public class CustomerBookingController {

    private final BookingService bookingService;
    private final FleetService fleetService;
    private final CurrentUserService currentUserService;
    private final PaymentService paymentService;

    public CustomerBookingController(BookingService bookingService,
                                     FleetService fleetService,
                                     CurrentUserService currentUserService,
                                     PaymentService paymentService) {
        this.bookingService = bookingService;
        this.fleetService = fleetService;
        this.currentUserService = currentUserService;
        this.paymentService = paymentService;
    }

    @GetMapping("/bookings/new/{machineId}")
    public String newForm(@PathVariable Long machineId, Model model) {
        if (!model.containsAttribute("bookingForm")) {
            model.addAttribute("bookingForm", new BookingForm());
        }
        addFormOptions(model, machineId);
        return "booking/new";
    }

    @PostMapping("/bookings/new/{machineId}")
    public String create(@PathVariable Long machineId,
                         @Valid @ModelAttribute("bookingForm") BookingForm form,
                         BindingResult binding,
                         @RequestParam(value = "alsoMachineIds", required = false) List<Long> alsoMachineIds,
                         Model model, RedirectAttributes ra) {
        if (binding.hasErrors()) {
            addFormOptions(model, machineId);
            return "booking/new";
        }
        try {
            // The machine being viewed, plus any extras ticked for the same job.
            List<Long> machineIds = new ArrayList<>();
            machineIds.add(machineId);
            if (alsoMachineIds != null) {
                machineIds.addAll(alsoMachineIds);
            }

            List<Booking> created = bookingService.createForMachines(currentUserService.require(),
                    machineIds, form.getStartDate(), form.getEndDate(),
                    form.getJobSiteDistrict(), form.getJobSiteAddress());

            ra.addFlashAttribute("success", created.size() == 1
                    ? "Booking request submitted. You will be notified once it is reviewed."
                    : created.size() + " machines requested for this job. You will be notified as each is reviewed.");
            return "redirect:/customer/bookings";
        } catch (BusinessRuleException ex) {
            addFormOptions(model, machineId);
            model.addAttribute("error", ex.getMessage());
            return "booking/new";
        }
    }

    /** Machine being booked, district list, and other machines that could join the job. */
    private void addFormOptions(Model model, Long machineId) {
        model.addAttribute("machine", fleetService.getById(machineId));
        model.addAttribute("districts", SriLankaDistricts.ALL);
        model.addAttribute("otherMachines", fleetService.findAll().stream()
                .filter(m -> m.isBookable() && !m.getId().equals(machineId))
                .toList());
    }

    @GetMapping("/customer/bookings")
    public String myBookings(Model model) {
        var customer = currentUserService.require();
        model.addAttribute("bookings", bookingService.forCustomer(customer));
        model.addAttribute("unpaidInvoices", paymentService.unpaidInvoiceIdsByBooking(customer));
        return "booking/my-bookings";
    }

    @PostMapping("/bookings/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes ra) {
        try {
            bookingService.cancel(currentUserService.require(), id);
            ra.addFlashAttribute("success", "Booking cancelled.");
        } catch (BusinessRuleException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/customer/bookings";
    }
}
