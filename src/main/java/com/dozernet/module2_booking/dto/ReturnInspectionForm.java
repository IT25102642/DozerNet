package com.dozernet.module2_booking.dto;

import com.dozernet.module2_booking.entity.ResponsibleParty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Form an administrator fills in when a machine is returned at the end of a
 * hire. Conditional rules (damage needs a description and a responsible party)
 * are enforced in {@code ReturnInspectionService}.
 */
public class ReturnInspectionForm {

    @NotBlank(message = "Enter the inspection notes")
    @Size(max = 1000, message = "Notes are too long")
    private String notes;

    private boolean damageReported;

    @Size(max = 1000, message = "Damage description is too long")
    private String damageDescription;

    private ResponsibleParty responsibleParty = ResponsibleParty.NONE;

    @DecimalMin(value = "0.00", message = "Repair cost cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Enter a valid amount")
    private BigDecimal estimatedRepairCost;

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isDamageReported() {
        return damageReported;
    }

    public void setDamageReported(boolean damageReported) {
        this.damageReported = damageReported;
    }

    public String getDamageDescription() {
        return damageDescription;
    }

    public void setDamageDescription(String damageDescription) {
        this.damageDescription = damageDescription;
    }

    public ResponsibleParty getResponsibleParty() {
        return responsibleParty;
    }

    public void setResponsibleParty(ResponsibleParty responsibleParty) {
        this.responsibleParty = responsibleParty;
    }

    public BigDecimal getEstimatedRepairCost() {
        return estimatedRepairCost;
    }

    public void setEstimatedRepairCost(BigDecimal estimatedRepairCost) {
        this.estimatedRepairCost = estimatedRepairCost;
    }
}
