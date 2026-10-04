package com.dozernet.module2_booking;

import java.util.List;

/**
 * Administrative districts of Sri Lanka for job-site selection on bookings.
 */
public final class SriLankaDistricts {

    public static final List<String> ALL = List.of(
            "Ampara", "Anuradhapura", "Badulla", "Batticaloa", "Colombo",
            "Galle", "Gampaha", "Hambantota", "Jaffna", "Kalutara",
            "Kandy", "Kegalle", "Kilinochchi", "Kurunegala", "Mannar",
            "Matale", "Matara", "Monaragala", "Mullaitivu", "Nuwara Eliya",
            "Polonnaruwa", "Puttalam", "Ratnapura", "Trincomalee", "Vavuniya"
    );

    private SriLankaDistricts() {
    }

    public static boolean isValid(String district) {
        return district != null && ALL.contains(district);
    }
}
