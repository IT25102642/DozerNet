package com.dozernet.module5_maintenance.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Form for scheduling upcoming maintenance on a machine.
 */
public class ScheduleForm {

    @NotNull(message = "Select a machine")
    private Long machineId;

    @NotNull(message = "Select a date")
    @FutureOrPresent(message = "Maintenance date cannot be in the past")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate scheduledDate;

    @NotBlank(message = "Describe the maintenance")
    @Size(max = 500)
    private String description;

    public Long getMachineId() {
        return machineId;
    }

    public void setMachineId(Long machineId) {
        this.machineId = machineId;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
