package com.dozernet.module2_booking.entity;

import com.dozernet.common.model.BaseEntity;
import com.dozernet.common.user.User;
import com.dozernet.module3_fleet.entity.Machine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A rental booking of a machine by a customer for an inclusive date range.
 */
@Entity
@Table(name = "bookings")
public class Booking extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Machine machine;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BookingStatus status = BookingStatus.PENDING;

    /** Snapshot of the total price at booking time (days * daily rate). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** Sri Lanka district where the machine will work. */
    @Column(nullable = false, length = 40)
    private String jobSiteDistrict = "";

    /** Landmark / road / site detail within the district. */
    @Column(nullable = false, length = 255)
    private String jobSiteAddress = "";

    /**
     * Groups the bookings created from a single multi-machine request, so a
     * customer who hires three machines for one job sees them together. Null
     * for a single-machine request. Each machine still has its own booking,
     * invoice and operator assignment.
     */
    @Column(length = 36)
    private String bookingGroupId;

    public Booking() {
    }

    public Booking(User customer, Machine machine, LocalDate startDate, LocalDate endDate) {
        this.customer = customer;
        this.machine = machine;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /** Combined label for tables and dashboards, e.g. "Colombo — Near Kaduwela flyover". */
    public String getJobSiteLabel() {
        if (jobSiteDistrict == null || jobSiteDistrict.isBlank()) {
            return jobSiteAddress != null ? jobSiteAddress : "";
        }
        if (jobSiteAddress == null || jobSiteAddress.isBlank()) {
            return jobSiteDistrict;
        }
        return jobSiteDistrict + " — " + jobSiteAddress;
    }

    /** Inclusive rental length in days (same start/end = 1 day). */
    public long getDays() {
        if (startDate == null || endDate == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    public User getCustomer() {
        return customer;
    }

    public void setCustomer(User customer) {
        this.customer = customer;
    }

    public Machine getMachine() {
        return machine;
    }

    public void setMachine(Machine machine) {
        this.machine = machine;
    }

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

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
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

    public String getBookingGroupId() {
        return bookingGroupId;
    }

    public void setBookingGroupId(String bookingGroupId) {
        this.bookingGroupId = bookingGroupId;
    }

    /** True when this booking was part of a multi-machine request. */
    public boolean isPartOfGroup() {
        return bookingGroupId != null && !bookingGroupId.isBlank();
    }
}
