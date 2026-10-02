package com.dozernet.common.web;

import com.dozernet.module3_fleet.entity.MachineType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A job-oriented rental category shown on the landing page
 * (e.g. Digging, Earthmoving) — maps to one or more machine types.
 */
public record JobCategory(
        String name,
        String slug,
        String imagePath,
        MachineType primaryType,
        MachineType[] types,
        long machineCount
) {
    /** Categories shown on the landing page and used for catalogue job filters. */
    public static final List<JobCategory> ALL = List.of(
            new JobCategory("Digging", "digging", "/images/machines/backhoe-loader.jpg",
                    MachineType.EXCAVATOR,
                    new MachineType[]{MachineType.EXCAVATOR, MachineType.BACKHOE_LOADER}, 0),
            new JobCategory("Earthmoving", "earthmoving", "/images/machines/bulldozer.jpg",
                    MachineType.BULLDOZER,
                    new MachineType[]{MachineType.BULLDOZER, MachineType.MOTOR_GRADER}, 0),
            new JobCategory("Loading", "loading", "/images/machines/wheel-loader.jpg",
                    MachineType.WHEEL_LOADER,
                    new MachineType[]{MachineType.WHEEL_LOADER, MachineType.SKID_STEER}, 0),
            new JobCategory("Lifting", "lifting", "/images/machines/telehandler.jpg",
                    MachineType.TELEHANDLER,
                    new MachineType[]{MachineType.TELEHANDLER}, 0),
            new JobCategory("Compacting", "compacting", "/images/machines/compactor.jpg",
                    MachineType.COMPACTOR,
                    new MachineType[]{MachineType.COMPACTOR}, 0),
            // Interim photo: drop a real dump-truck shot at
            // /images/machines/dump-truck.jpg and point this here.
            new JobCategory("Hauling", "hauling", "/images/machines/wheel-loader.jpg",
                    MachineType.DUMP_TRUCK,
                    new MachineType[]{MachineType.DUMP_TRUCK}, 0)
    );

    public JobCategory withCount(long count) {
        return new JobCategory(name, slug, imagePath, primaryType, types, count);
    }

    public static Optional<JobCategory> findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return ALL.stream()
                .filter(c -> c.slug().equalsIgnoreCase(slug.trim()))
                .findFirst();
    }

    public List<MachineType> typeList() {
        return Arrays.asList(types);
    }
}
