package com.dozernet.common.web;

import com.dozernet.common.model.Role;
import com.dozernet.common.notification.NotificationService;
import com.dozernet.common.security.CurrentUserService;
import com.dozernet.common.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Notification centre for non-customer roles (admin, owner, operator).
 * Customer alerts are owned by Module 1 at {@code /customer/notifications}.
 */
@Controller
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserService currentUserService;

    public NotificationController(NotificationService notificationService,
                                  CurrentUserService currentUserService) {
        this.notificationService = notificationService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/notifications")
    public String list(Model model) {
        User user = currentUserService.require();
        if (user.hasRole(Role.CUSTOMER)) {
            return "redirect:/customer/notifications";
        }
        model.addAttribute("notifications", notificationService.forUser(user));
        return "notifications/list";
    }

    @PostMapping("/notifications/read-all")
    public String markAllRead() {
        User user = currentUserService.require();
        notificationService.markAllRead(user);
        if (user.hasRole(Role.CUSTOMER)) {
            return "redirect:/customer/notifications";
        }
        return "redirect:/notifications";
    }
}
