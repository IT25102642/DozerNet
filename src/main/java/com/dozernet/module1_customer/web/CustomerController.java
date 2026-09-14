package com.dozernet.module1_customer.web;

import com.dozernet.common.document.DocumentType;
import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.common.security.CurrentUserService;
import com.dozernet.common.user.User;
import com.dozernet.module1_customer.dto.ProfileForm;
import com.dozernet.module1_customer.service.CustomerService;
import com.dozernet.module6_payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Customer self-service area: dashboard, profile and password management.
 * Booking history is surfaced here too (populated by the Booking module).
 */
@Controller
@RequestMapping("/customer")
public class CustomerController {

    private final CustomerService customerService;
    private final CurrentUserService currentUserService;
    private final PaymentService paymentService;

    public CustomerController(CustomerService customerService,
                              CurrentUserService currentUserService,
                              PaymentService paymentService) {
        this.customerService = customerService;
        this.currentUserService = currentUserService;
        this.paymentService = paymentService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        User user = currentUserService.require();
        model.addAttribute("user", user);
        model.addAttribute("unpaidCount", paymentService.countUnpaidForCustomer(user));
        return "customer/dashboard";
    }

    @GetMapping("/profile")
    public String profile(Model model) {
        User user = currentUserService.require();
        if (!model.containsAttribute("profileForm")) {
            model.addAttribute("profileForm", new ProfileForm(user.getFullName(), user.getPhone()));
        }
        model.addAttribute("user", user);
        model.addAttribute("documents", customerService.documentsFor(user));
        model.addAttribute("documentTypes", DocumentType.values());
        return "customer/profile";
    }

    /**
     * Adds a document to the account (NIC copy, licence, ownership proof) so it
     * can be reused rather than uploaded again for each registration.
     */
    @PostMapping("/documents")
    public String uploadDocument(@RequestParam DocumentType type,
                                 @RequestParam("file") MultipartFile file,
                                 RedirectAttributes ra) {
        try {
            customerService.uploadDocument(currentUserService.require(), type, file);
            ra.addFlashAttribute("success", type.getDisplayName() + " uploaded.");
        } catch (BusinessRuleException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/customer/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@Valid @ModelAttribute("profileForm") ProfileForm form,
                                BindingResult binding,
                                Model model,
                                RedirectAttributes ra) {
        User user = currentUserService.require();
        if (binding.hasErrors()) {
            model.addAttribute("user", user);
            return "customer/profile";
        }
        try {
            customerService.updateProfile(user, form);
            ra.addFlashAttribute("success", "Profile updated successfully.");
        } catch (BusinessRuleException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/customer/profile";
    }

    @PostMapping("/password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes ra) {
        try {
            customerService.changePassword(currentUserService.require(),
                    currentPassword, newPassword, confirmPassword);
            ra.addFlashAttribute("success", "Password changed successfully.");
        } catch (BusinessRuleException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/customer/profile";
    }
}
