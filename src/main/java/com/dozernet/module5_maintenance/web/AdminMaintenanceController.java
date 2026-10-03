package com.dozernet.module5_maintenance.web;

import com.dozernet.module3_fleet.service.FleetService;
import com.dozernet.module5_maintenance.dto.ScheduleForm;
import com.dozernet.module5_maintenance.service.MaintenanceService;
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

import java.math.BigDecimal;

/**
 * Admin maintenance screens: schedule maintenance, complete jobs (recording
 * parts/cost), and view each machine's service history.
 */
@Controller
public class AdminMaintenanceController {

    private final MaintenanceService maintenanceService;
    private final FleetService fleetService;

    public AdminMaintenanceController(MaintenanceService maintenanceService, FleetService fleetService) {
        this.maintenanceService = maintenanceService;
        this.fleetService = fleetService;
    }

    @GetMapping("/admin/maintenance")
    public String index(Model model) {
        model.addAttribute("records", maintenanceService.all());
        model.addAttribute("machines", fleetService.findAll());
        model.addAttribute("dueForService", maintenanceService.dueForService());
        model.addAttribute("serviceIntervalMonths", MaintenanceService.SERVICE_INTERVAL_MONTHS);
        if (!model.containsAttribute("scheduleForm")) {
            model.addAttribute("scheduleForm", new ScheduleForm());
        }
        return "maintenance/admin-maintenance";
    }

    /** Sends the service-interval reminders for every machine that is overdue. */
    @PostMapping("/admin/maintenance/remind")
    public String sendServiceReminders(RedirectAttributes ra) {
        int flagged = maintenanceService.sendServiceReminders();
        ra.addFlashAttribute("success", flagged == 0
                ? "No machines are currently due for service."
                : "Reminders sent for " + flagged + " machine(s) due for service.");
        return "redirect:/admin/maintenance";
    }

    @PostMapping("/admin/maintenance/schedule")
    public String schedule(@Valid @ModelAttribute("scheduleForm") ScheduleForm form,
                           BindingResult binding, Model model, RedirectAttributes ra) {
        if (binding.hasErrors()) {
            model.addAttribute("records", maintenanceService.all());
            model.addAttribute("machines", fleetService.findAll());
            return "maintenance/admin-maintenance";
        }
        maintenanceService.schedule(form);
        ra.addFlashAttribute("success", "Maintenance scheduled. The machine is now out of the booking pool.");
        return "redirect:/admin/maintenance";
    }

    @PostMapping("/admin/maintenance/{id}/complete")
    public String complete(@PathVariable Long id,
                           @RequestParam(required = false) String partsUsed,
                           @RequestParam(required = false) BigDecimal cost,
                           @RequestParam(defaultValue = "false") boolean returnToService,
                           RedirectAttributes ra) {
        maintenanceService.complete(id, partsUsed, cost, returnToService);
        ra.addFlashAttribute("success", "Maintenance recorded as completed.");
        return "redirect:/admin/maintenance";
    }

    @GetMapping("/admin/maintenance/history/{machineId}")
    public String history(@PathVariable Long machineId, Model model) {
        model.addAttribute("machine", fleetService.getById(machineId));
        model.addAttribute("records", maintenanceService.historyFor(machineId));
        return "maintenance/history";
    }
}
