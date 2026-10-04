package com.dozernet.module3_fleet.web;

import com.dozernet.common.document.Document;
import com.dozernet.common.document.DocumentService;
import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.module3_fleet.dto.MachineForm;
import com.dozernet.module3_fleet.entity.Machine;
import com.dozernet.module3_fleet.entity.MachineStatus;
import com.dozernet.module3_fleet.entity.MachineType;
import com.dozernet.module3_fleet.service.FleetService;
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
 * Admin fleet management: company machine CRUD, availability status, and
 * approval/rejection of private owner listings.
 */
@Controller
public class AdminFleetController {

    private final FleetService fleetService;
    private final DocumentService documentService;

    public AdminFleetController(FleetService fleetService, DocumentService documentService) {
        this.fleetService = fleetService;
        this.documentService = documentService;
    }

    @GetMapping("/admin/machines")
    public String list(Model model) {
        model.addAttribute("machines", fleetService.findAll());
        model.addAttribute("statuses", MachineStatus.values());
        return "fleet/admin-machines";
    }

    @GetMapping("/admin/machines/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("machineForm")) {
            model.addAttribute("machineForm", new MachineForm());
        }
        model.addAttribute("types", MachineType.values());
        model.addAttribute("mode", "new");
        return "fleet/admin-machine-form";
    }

    @PostMapping("/admin/machines/new")
    public String create(@Valid @ModelAttribute("machineForm") MachineForm form,
                         BindingResult binding, Model model, RedirectAttributes ra) {
        if (binding.hasErrors()) {
            model.addAttribute("types", MachineType.values());
            model.addAttribute("mode", "new");
            return "fleet/admin-machine-form";
        }
        try {
            fleetService.createCompanyMachine(form);
            ra.addFlashAttribute("success", "Machine added to the company fleet.");
        } catch (BusinessRuleException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/machines";
    }

    @GetMapping("/admin/machines/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Machine m = fleetService.getById(id);
        if (!model.containsAttribute("machineForm")) {
            model.addAttribute("machineForm", MachineForm.from(m));
        }
        model.addAttribute("types", MachineType.values());
        model.addAttribute("mode", "edit");
        model.addAttribute("machineId", id);
        return "fleet/admin-machine-form";
    }

    @PostMapping("/admin/machines/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("machineForm") MachineForm form,
                         BindingResult binding, Model model, RedirectAttributes ra) {
        if (binding.hasErrors()) {
            model.addAttribute("types", MachineType.values());
            model.addAttribute("mode", "edit");
            model.addAttribute("machineId", id);
            return "fleet/admin-machine-form";
        }
        try {
            fleetService.update(id, form);
            ra.addFlashAttribute("success", "Machine updated.");
        } catch (BusinessRuleException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/machines";
    }

    @PostMapping("/admin/machines/{id}/status")
    public String setStatus(@PathVariable Long id,
                            @RequestParam MachineStatus status,
                            RedirectAttributes ra) {
        fleetService.setStatus(id, status);
        ra.addFlashAttribute("success", "Machine status updated to " + status.getDisplayName() + ".");
        return "redirect:/admin/machines";
    }

    @PostMapping("/admin/machines/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            fleetService.delete(id);
            ra.addFlashAttribute("success", "Machine deleted.");
        } catch (BusinessRuleException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Could not delete: the machine may have bookings.");
        }
        return "redirect:/admin/machines";
    }

    // ---------- Listing approvals ----------

    @GetMapping("/admin/listings")
    public String pendingListings(Model model) {
        List<Machine> pending = fleetService.pendingApprovals();

        // Ownership proof and NIC copies the owner uploaded, so the admin can
        // check the paperwork before publishing a listing.
        Map<Long, List<Document>> ownerDocuments = new LinkedHashMap<>();
        for (Machine machine : pending) {
            if (machine.getOwner() != null) {
                ownerDocuments.putIfAbsent(machine.getOwner().getId(),
                        documentService.forOwner(machine.getOwner()));
            }
        }

        model.addAttribute("machines", pending);
        model.addAttribute("ownerDocuments", ownerDocuments);
        return "fleet/admin-listings";
    }

    @PostMapping("/admin/listings/{id}/approve")
    public String approve(@PathVariable Long id, RedirectAttributes ra) {
        fleetService.approveListing(id);
        ra.addFlashAttribute("success", "Listing approved and published.");
        return "redirect:/admin/listings";
    }

    @PostMapping("/admin/listings/{id}/reject")
    public String reject(@PathVariable Long id, RedirectAttributes ra) {
        fleetService.rejectListing(id);
        ra.addFlashAttribute("success", "Listing rejected.");
        return "redirect:/admin/listings";
    }
}