package com.dozernet.module1_customer.web;

import com.dozernet.common.security.CurrentUserService;
import com.dozernet.common.user.User;
import com.dozernet.module1_customer.service.CustomerNotificationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Customer Management (Module 1) notification centre — in-app alerts for the
 * logged-in customer (booking status, invoices, operator assignment, etc.).
 */
@Controller
@RequestMapping("/customer/notifications")
public class CustomerNotificationController {

    private final CustomerNotificationService customerNotificationService;
    private final CurrentUserService currentUserService;

    public CustomerNotificationController(CustomerNotificationService customerNotificationService,
                                          CurrentUserService currentUserService) {
        this.customerNotificationService = customerNotificationService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public String list(Model model) {
        User customer = currentUserService.require();
        model.addAttribute("notifications", customerNotificationService.forCustomer(customer));
        return "customer/notifications";
    }

    @PostMapping("/read-all")
    public String markAllRead() {
        customerNotificationService.markAllRead(currentUserService.require());
        return "redirect:/customer/notifications";
    }
}
