package com.dozernet.module3_fleet.dto;

import com.dozernet.common.validation.ValidationPatterns;
import com.dozernet.module3_fleet.entity.Machine;
import com.dozernet.module3_fleet.entity.MachineType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Form for creating/editing a machine (used by both admin fleet screens and
 * private owner listings). Every field is validated.
 */
public class MachineForm {

    @NotBlank(message = "Model/name is required")
    @Size(max = 120)
    private String model;

    @NotNull(message = "Select a machine type")
    private MachineType type;

    @NotBlank(message = "Registration number is required")
    @Pattern(regexp = ValidationPatterns.PLATE, message = ValidationPatterns.PLATE_MSG)
    private String registrationNumber;

    @NotBlank(message = "Location is required")
    @Size(max = 120)
    private String location;

    @NotNull(message = "Daily rate is required")
    @DecimalMin(value = "1.00", message = "Daily rate must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Enter a valid amount")
    private BigDecimal dailyRate;

    @Size(max = 1000, message = "Description is too long")
    private String description;

    /** Q12: every listing must carry a photo, whether added by an admin or an owner. */
    @NotBlank(message = "A photo is required for every listing")
    @Size(max = 300)
    private String imageUrl;

    public MachineForm() {
    }

    /** Build a form pre-filled from an existing machine (for editing). */
    public static MachineForm from(Machine m) {
        MachineForm f = new MachineForm();
        f.model = m.getModel();
        f.type = m.getType();
        f.registrationNumber = m.getRegistrationNumber();
        f.location = m.getLocation();
        f.dailyRate = m.getDailyRate();
        f.description = m.getDescription();
        f.imageUrl = m.getImageUrl();
        return f;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public MachineType getType() {
        return type;
    }

    public void setType(MachineType type) {
        this.type = type;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public BigDecimal getDailyRate() {
        return dailyRate;
    }

    public void setDailyRate(BigDecimal dailyRate) {
        this.dailyRate = dailyRate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}