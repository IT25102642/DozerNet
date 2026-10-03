package com.dozernet.module4_operator.web;

import com.dozernet.common.document.Document;
import com.dozernet.common.document.DocumentService;
import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.common.exception.ResourceNotFoundException;
import com.dozernet.common.user.User;
import com.dozernet.common.user.UserRepository;
import com.dozernet.module2_booking.entity.Booking;
import com.dozernet.module4_operator.dto.CompanyOperatorForm;
import com.dozernet.module4_operator.service.OperatorService;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin operator management: list operators, add company operators, verify
 * driver licences, and assign operators to approved bookings.
 */
@Controller
public class AdminOperatorController {

    private final OperatorService operatorService;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final DocumentService documentService;

    public AdminOperatorController(OperatorService operatorService,
                                   UserRepository userRepository,
                                   PaymentService paymentService,
                                   DocumentService documentService) {
        this.operatorService = operatorService;
        this.userRepository = userRepository;
        this.paymentService = paymentService;
        this.documentService = documentService;
    }

    // ---------- Operator list + add ----------

    @GetMapping("/admin/operators")
    public String list(Model model) {
        var pending = operatorService.pendingVerifications();

        // Licence and NIC uploads, so a licence is verified against a document.
        Map<Long, List<Document>> operatorDocuments = new LinkedHashMap<>();
        pending.forEach(profile -> operatorDocuments.put(profile.getId(),
                documentService.forOwner(profile.getUser())));

        model.addAttribute("operators", operatorService.allOperators());
        model.addAttribute("pending", pending);
        model.addAttribute("operatorDocuments", operatorDocuments);
        if (!model.containsAttribute("operatorForm")) {
            model.addAttribute("operatorForm", new CompanyOperatorForm());
        }
        return "operator/admin-operators";
    }

    @PostMapping("/admin/operators/new")
    public String addCompanyOperator(@Valid @ModelAttribute("operatorForm") CompanyOperatorForm form,
                                     BindingResult binding, Model model, RedirectAttributes ra) {
        if (binding.hasErrors()) {
            model.addAttribute("operators", operatorService.allOperators());
            model.addAttribute("pending", operatorService.pendingVerifications());
            return "operator/admin-operators";
        }
        try {
            operatorService.addCompanyOperator(form);
            ra.addFlashAttribute("success", "Company operator added.");
        } catch (BusinessRuleException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/operators";
    }

    @PostMapping("/admin/operators/{id}/verify")
    public String verify(@PathVariable Long id, RedirectAttributes ra) {
        operatorService.verify(id);
        ra.addFlashAttribute("success", "Operator verified.");
        return "redirect:/admin/operators";
    }

    // ---------- Assignments ----------

    @GetMapping("/admin/assignments")
    public String assignments(Model model) {
        var bookings = operatorService.paidBookingsAwaitingOperator();
        model.addAttribute("bookings", bookings);
        model.addAttribute("readyToAssign", bookings);
        model.addAttribute("operators", operatorService.verifiedOperators());
        model.addAttribute("paymentLabels", paymentService.paymentLabelsForBookings(bookings));

        // Per booking, verified operators with the genuinely free ones listed first.
        Map<Long, List<OperatorService.OperatorOption>> suggestions = new LinkedHashMap<>();
        for (Booking booking : bookings) {
            suggestions.put(booking.getId(), operatorService.suggestionsFor(booking));
        }
        model.addAttribute("suggestions", suggestions);
        return "operator/admin-assignments";
    }

    @PostMapping("/admin/assignments/assign")
    public String assign(@RequestParam Long bookingId,
                         @RequestParam Long operatorId,
                         RedirectAttributes ra) {
        try {
            User operator = userRepository.findById(operatorId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Operator", operatorId));
            operatorService.assign(bookingId, operator);
            ra.addFlashAttribute("success", "Operator assigned to the booking.");
        } catch (BusinessRuleException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/assignments";
    }
}
