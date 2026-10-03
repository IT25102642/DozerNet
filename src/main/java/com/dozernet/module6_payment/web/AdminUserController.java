package com.dozernet.module6_payment.web;

import com.dozernet.common.exception.ResourceNotFoundException;
import com.dozernet.common.security.CurrentUserService;
import com.dozernet.common.user.User;
import com.dozernet.common.user.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin user account management: list accounts and enable/disable them.
 */
@Controller
public class AdminUserController {

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public AdminUserController(UserRepository userRepository, CurrentUserService currentUserService) {
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/admin/users")
    public String list(Model model) {
        model.addAttribute("users", userRepository.findAll());
        return "payment/admin-users";
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
