package com.dozernet.module3_fleet.web;

import com.dozernet.common.web.JobCategory;
import com.dozernet.module3_fleet.entity.MachineType;
import com.dozernet.module3_fleet.service.FleetService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;

/**
 * Public machine catalogue: browse, search and view a machine.
 */
@Controller
public class MachineCatalogController {

    private final FleetService fleetService;

    public MachineCatalogController(FleetService fleetService) {
        this.fleetService = fleetService;
    }

    @GetMapping("/machines")
    public String browse(@RequestParam(required = false) MachineType type,
                         @RequestParam(required = false) String job,
                         @RequestParam(required = false) String keyword,
                         Model model) {
        JobCategory category = JobCategory.findBySlug(job).orElse(null);

        if (category != null) {
            // Job category may include several machine types — same groups as the landing page.
            model.addAttribute("machines", fleetService.searchByTypes(category.typeList(), keyword));
            model.addAttribute("selectedJob", category);
            model.addAttribute("selectedType", null);
        } else {
            model.addAttribute("machines", fleetService.search(type, keyword));
            model.addAttribute("selectedJob", null);
            model.addAttribute("selectedType", type);
        }

        model.addAttribute("jobCategories", JobCategory.ALL.stream()
                .map(cat -> cat.withCount(countFor(cat.types())))
                .toList());
        model.addAttribute("types", MachineType.values());
        model.addAttribute("keyword", keyword);
        return "fleet/catalog";
    }

    private long countFor(MachineType[] types) {
        return Arrays.stream(types)
                .mapToLong(t -> fleetService.search(t, null).size())
                .sum();
    }

    @GetMapping("/machines/view/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("machine", fleetService.getById(id));
        return "fleet/detail";
    }
}
