package com.dozernet.common.web;

import com.dozernet.module3_fleet.entity.Machine;
import com.dozernet.module3_fleet.entity.MachineType;
import com.dozernet.module3_fleet.service.FleetService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Public marketing pages (landing, about, how it works). These are open to everyone.
 */
@Controller
public class HomeController {

    /**
     * Machine types shown in the landing hero's live fleet showcase. Types that
     * still borrow another machine's photo (dump truck, motor grader) are left out.
     */
    private static final List<MachineType> SHOWCASE_TYPES = List.of(
            MachineType.EXCAVATOR, MachineType.BACKHOE_LOADER, MachineType.WHEEL_LOADER,
            MachineType.BULLDOZER, MachineType.TELEHANDLER, MachineType.SKID_STEER,
            MachineType.COMPACTOR);

    private final FleetService fleetService;

    public HomeController(FleetService fleetService) {
        this.fleetService = fleetService;
    }

    @GetMapping("/")
    public String landing(Model model) {
        model.addAttribute("availableMachines", fleetService.search(null, null).size());
        model.addAttribute("jobCategories", JobCategory.ALL.stream()
                .map(cat -> cat.withCount(countFor(cat.types())))
                .toList());

        // Split the showcase across the hero's two scrolling columns.
        List<Machine> showcase = showcaseMachines();
        List<Machine> columnA = new ArrayList<>();
        List<Machine> columnB = new ArrayList<>();
        for (int i = 0; i < showcase.size(); i++) {
            (i % 2 == 0 ? columnA : columnB).add(showcase.get(i));
        }
        model.addAttribute("showcaseA", columnA);
        model.addAttribute("showcaseB", columnB);
        return "public/landing";
    }

    private long countFor(MachineType[] types) {
        return Arrays.stream(types)
                .mapToLong(t -> fleetService.search(t, null).size())
                .sum();
    }

    /** One bookable machine (with a photo) per showcase type, cheapest first. */
    private List<Machine> showcaseMachines() {
        return SHOWCASE_TYPES.stream()
                .map(type -> fleetService.search(type, null))
                .filter(machines -> !machines.isEmpty())
                .map(machines -> machines.get(0))
                .filter(m -> m.getImageUrl() != null && !m.getImageUrl().isBlank())
                .toList();
    }

    @GetMapping("/about")
    public String about() {
        return "public/about";
    }

    @GetMapping("/how-it-works")
    public String howItWorks() {
        return "public/how-it-works";
    }
}
