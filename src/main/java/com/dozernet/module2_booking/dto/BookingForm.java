package com.dozernet.module2_booking.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Form-backing object for creating a booking. Cross-field rules (end &gt;= start,
 * no overlap) are enforced in {@code BookingService}.
 */
public class BookingForm {

    @NotNull(message = "Select a start date")
    @FutureOrPresent(message = "Start date cannot be in the past")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @NotNull(message = "Select an end date")
    @FutureOrPresent(message = "End date cannot be in the past")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @NotBlank(message = "Select the district where the machine will work")
    @Size(max = 40)
    private String jobSiteDistrict;

    @NotBlank(message = "Enter the job site address or landmark")
    @Size(max = 255, message = "Address must be at most 255 characters")
    private String jobSiteAddress;

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getJobSiteDistrict() {
        return jobSiteDistrict;
    }

    public void setJobSiteDistrict(String jobSiteDistrict) {
        this.jobSiteDistrict = jobSiteDistrict;
    }

    public String getJobSiteAddress() {
        return jobSiteAddress;
    }

    public void setJobSiteAddress(String jobSiteAddress) {
        this.jobSiteAddress = jobSiteAddress;
    }
}
