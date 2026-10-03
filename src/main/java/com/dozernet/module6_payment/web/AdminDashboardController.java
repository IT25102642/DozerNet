package com.dozernet.module6_payment.web;

import com.dozernet.module6_payment.service.AdminService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The administrator's home dashboard summarising the whole platform.
 */
@Controller
public class AdminDashboardController {

    private final AdminService adminService;

    public AdminDashboardController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/admin/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("stats", adminService.dashboard());
        model.addAttribute("deployments", adminService.fleetWhereabouts());
        model.addAttribute("awaitingOperator", adminService.paidAwaitingOperator());
        return "admin/dashboard";
    }
}
