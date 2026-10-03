package com.dozernet.module6_payment.web;

import com.dozernet.common.audit.AuditLog;
import com.dozernet.common.audit.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Read-only view of the administrative audit trail. The trail itself is written
 * from every module through {@code common.audit.AuditService}; this screen is
 * part of the Administration module so admins can answer "who changed this?".
 */
@Controller
public class AdminAuditController {

    private static final int PAGE_SIZE = 50;

    private final AuditService auditService;

    public AdminAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/admin/audit")
    public String auditTrail(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<AuditLog> entries = auditService.recent(Math.max(page, 0), PAGE_SIZE);
        model.addAttribute("entries", entries.getContent());
        model.addAttribute("currentPage", entries.getNumber());
        model.addAttribute("totalPages", entries.getTotalPages());
        model.addAttribute("totalEntries", entries.getTotalElements());
        return "payment/admin-audit";
    }
}
