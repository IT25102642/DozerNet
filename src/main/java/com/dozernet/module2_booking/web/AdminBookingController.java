package com.dozernet.module2_booking.web;

import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.module2_booking.dto.ReturnInspectionForm;
import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module2_booking.entity.BookingStatus;
import com.dozernet.module2_booking.entity.ResponsibleParty;
import com.dozernet.module2_booking.service.BookingService;
import com.dozernet.module2_booking.service.ReturnInspectionService;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin booking review: list requests, approve/reject them, swap a machine that
 * has become unavailable, and file the return inspection once a job is done.
 */
@Controller
public class AdminBookingController {

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final ReturnInspectionService returnInspectionService;
    private final FleetService fleetService;

    public AdminBookingController(BookingService bookingService,
                                  PaymentService paymentService,
                                  ReturnInspectionService returnInspectionService,
                                  FleetService fleetService) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.returnInspectionService = returnInspectionService;
        this.fleetService = fleetService;
    }

    @GetMapping("/admin/bookings")
    public String list(@RequestParam(required = false) BookingStatus status, Model model) {
        List<Booking> bookings = status == null ? bookingService.all() : bookingService.byStatus(status);
        Set<Long> cancellableUnpaid = new HashSet<>();
        Set<Long> replaceable = new HashSet<>();
        Set<Long> needsInspection = new HashSet<>();
        Map<Long, String> cancellationNotes = new LinkedHashMap<>();

        for (Booking b : bookings) {
            if (b.getStatus() == BookingStatus.APPROVED) {
                if (paymentService.isInvoicePaidForBooking(b)) {
                    // Paid bookings are still cancellable - the refund tier decides the cost.
                    cancellationNotes.put(b.getId(), bookingService.cancellationPreview(b).description());
                } else {
                    cancellableUnpaid.add(b.getId());
                    cancellationNotes.put(b.getId(), "Not paid yet - the invoice will be voided.");
                }
            }
            if (b.getStatus() == BookingStatus.PENDING || b.getStatus() == BookingStatus.APPROVED) {
                replaceable.add(b.getId());
            }
            if (b.getStatus() == BookingStatus.COMPLETED
                    && returnInspectionService.forBooking(b).isEmpty()) {
                needsInspection.add(b.getId());
            }
        }

        model.addAttribute("bookings", bookings);
        model.addAttribute("statuses", BookingStatus.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("cancellableUnpaid", cancellableUnpaid);
        model.addAttribute("cancellationNotes", cancellationNotes);
        model.addAttribute("replaceable", replaceable);
        model.addAttribute("needsInspection", needsInspection);
        return "booking/admin-bookings";
    }

    // ---------- Machine replacement ----------

    @GetMapping("/admin/bookings/{id}/replace")
    public String replaceForm(@PathVariable Long id, Model model) {
        Booking booking = bookingService.getById(id);
        model.addAttribute("booking", booking);
        model.addAttribute("machines", fleetService.findAll().stream()
                .filter(m -> m.isBookable() && !m.getId().equals(booking.getMachine().getId()))
                .toList());
        model.addAttribute("history", bookingService.replacementHistory(booking));
        return "booking/admin-replace-machine";
    }

    @PostMapping("/admin/bookings/{id}/replace")
    public String replace(@PathVariable Long id,
                          @RequestParam Long replacementMachineId,
                          @RequestParam String reason,
                          RedirectAttributes ra) {
        try {
            bookingService.replaceMachine(id, replacementMachineId, reason);
            ra.addFlashAttribute("success", "Machine replaced. The customer has been notified.");
            return "redirect:/admin/bookings";
        } catch (BusinessRuleException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/bookings/" + id + "/replace";
        }
    }

    // ---------- Return inspection ----------

    @GetMapping("/admin/bookings/{id}/inspection")
    public String inspectionForm(@PathVariable Long id, Model model) {
        Booking booking = bookingService.getById(id);
        model.addAttribute("booking", booking);
        model.addAttribute("existing", returnInspectionService.forBooking(booking).orElse(null));
        model.addAttribute("responsibleParties", ResponsibleParty.values());
        if (!model.containsAttribute("inspectionForm")) {
            model.addAttribute("inspectionForm", new ReturnInspectionForm());
        }
        return "booking/admin-return-inspection";
    }

    @PostMapping("/admin/bookings/{id}/inspection")
    public String recordInspection(@PathVariable Long id,
                                   @Valid @ModelAttribute("inspectionForm") ReturnInspectionForm form,
                                   BindingResult binding,
                                   @RequestParam(value = "photo", required = false) MultipartFile photo,
                                   Model model,
                                   RedirectAttributes ra) {
        Booking booking = bookingService.getById(id);
        if (binding.hasErrors()) {
            model.addAttribute("booking", booking);
            model.addAttribute("existing", returnInspectionService.forBooking(booking).orElse(null));
            model.addAttribute("responsibleParties", ResponsibleParty.values());
            return "booking/admin-return-inspection";
        }
        try {
            returnInspectionService.record(id, form, photo);
            ra.addFlashAttribute("success", "Return inspection filed.");
            return "redirect:/admin/bookings?status=COMPLETED";
        } catch (BusinessRuleException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/bookings/" + id + "/inspection";
        }
    }

    /** Damage-tracking view: every inspection where something was found. */
    @GetMapping("/admin/inspections")
    public String inspections(Model model) {
        model.addAttribute("inspections", returnInspectionService.all());
        model.addAttribute("damaged", returnInspectionService.withDamage());
        return "booking/admin-inspections";
    }

    @PostMapping("/admin/bookings/{id}/approve")
    public String approve(@PathVariable Long id, RedirectAttributes ra) {
        try {
            bookingService.approve(id);
            ra.addFlashAttribute("success", "Booking approved. Invoice issued — customer notified to pay.");
        } catch (BusinessRuleException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/bookings";
    }

    @PostMapping("/admin/bookings/{id}/reject")
    public String reject(@PathVariable Long id, RedirectAttributes ra) {
        try {
            bookingService.reject(id);
            ra.addFlashAttribute("success", "Booking rejected.");
        } catch (BusinessRuleException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/bookings";
    }

    @PostMapping("/admin/bookings/{id}/cancel")
    public String cancelApproved(@PathVariable Long id, RedirectAttributes ra) {
        try {
            bookingService.cancelApproved(id);
            ra.addFlashAttribute("success",
                    "Booking cancelled. Machine dates are free again, and any refund due has been recorded.");
        } catch (BusinessRuleException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/bookings";
    }
}
