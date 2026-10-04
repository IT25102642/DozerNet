package com.dozernet.module6_payment.web;

import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.common.exception.ResourceNotFoundException;
import com.dozernet.common.security.CurrentUserService;
import com.dozernet.common.user.User;
import com.dozernet.common.user.UserRepository;
import com.dozernet.module6_payment.dto.StaffAccountForm;
import com.dozernet.module6_payment.service.StaffAccountService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin user account management: list accounts, enable/disable them, and
 * create new staff (administrator) accounts.
 */
@Controller
public class AdminUserController {

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final StaffAccountService staffAccountService;

    public AdminUserController(UserRepository userRepository,
                               CurrentUserService currentUserService,
                               StaffAccountService staffAccountService) {
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.staffAccountService = staffAccountService;
    }

    @GetMapping("/admin/users")
    public String list(Model model) {
        // Accounts their owners deleted are anonymised husks - nothing for an admin to manage.
        model.addAttribute("users", userRepository.findAll().stream()
                .filter(u -> !u.isDeleted())
                .toList());
        return "payment/admin-users";
    }

    @GetMapping("/admin/users/new")
    public String newAdminForm(Model model) {
        if (!model.containsAttribute("staffForm")) {
            model.addAttribute("staffForm", new StaffAccountForm());
        }
        return "payment/admin-user-form";
    }

    @PostMapping("/admin/users/new")
    public String createAdmin(@Valid @ModelAttribute("staffForm") StaffAccountForm form,
                              BindingResult binding,
                              RedirectAttributes ra) {
        if (binding.hasErrors()) {
            return "payment/admin-user-form";
        }
        try {
            User created = staffAccountService.createAdmin(form);
            ra.addFlashAttribute("success", "Administrator account created for " + created.getFullName()
                    + ". Share the password with them securely - they can sign in straight away.");
        } catch (BusinessRuleException ex) {
            binding.reject("staff", ex.getMessage());
            return "payment/admin-user-form";
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/users/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));
        if (currentUserService.current().map(u -> u.getId().equals(id)).orElse(false)) {
            ra.addFlashAttribute("error", "You cannot disable your own account.");
            return "redirect:/admin/users";
        }
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
        ra.addFlashAttribute("success", "Account " + (user.isEnabled() ? "enabled" : "disabled") + ".");
        return "redirect:/admin/users";
    }
}
