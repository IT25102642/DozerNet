package com.dozernet.module4_operator.web;

import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.common.security.CurrentUserService;
import com.dozernet.module4_operator.entity.JobStatus;
import com.dozernet.module4_operator.service.OperatorService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Operator self-service: see assigned jobs and update their progress.
 */
@Controller
@RequestMapping("/operator")
public class OperatorController {

    private final OperatorService operatorService;
    private final CurrentUserService currentUserService;

    public OperatorController(OperatorService operatorService,
                              CurrentUserService currentUserService) {
        this.operatorService = operatorService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("assignments", operatorService.assignmentsFor(currentUserService.require()));
        model.addAttribute("jobStatuses", JobStatus.values());
        return "operator/dashboard";
    }

    @PostMapping("/assignments/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @RequestParam JobStatus status,
                               RedirectAttributes ra) {
        try {
            operatorService.updateJobStatus(id, status, currentUserService.require());
            ra.addFlashAttribute("success", "Job status updated to " + status.getDisplayName() + ".");
        } catch (BusinessRuleException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/operator/dashboard";
    }
}
