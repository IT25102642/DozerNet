package com.dozernet.module2_booking.entity;

import com.dozernet.common.model.BaseEntity;
import com.dozernet.common.user.User;
import com.dozernet.module3_fleet.entity.Machine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * History of a machine swap on an existing booking. When the machine a customer
 * booked becomes unavailable, an admin substitutes another one; every swap is
 * kept here so the booking's history stays complete.
 */
@Entity
@Table(name = "machine_replacements")
public class MachineReplacementLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Machine previousMachine;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Machine replacementMachine;

    @ManyToOne(fetch = FetchType.EAGER)
    private User replacedBy;

    @Column(nullable = false, length = 500)
    private String reason = "";

    public MachineReplacementLog() {
    }

    public MachineReplacementLog(Booking booking, Machine previousMachine,
                                 Machine replacementMachine, User replacedBy, String reason) {
        this.booking = booking;
        this.previousMachine = previousMachine;
        this.replacementMachine = replacementMachine;
        this.replacedBy = replacedBy;
        this.reason = reason;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public Machine getPreviousMachine() {
        return previousMachine;
    }

    public void setPreviousMachine(Machine previousMachine) {
        this.previousMachine = previousMachine;
    }

    public Machine getReplacementMachine() {
        return replacementMachine;
    }

    public void setReplacementMachine(Machine replacementMachine) {
        this.replacementMachine = replacementMachine;
    }

    public User getReplacedBy() {
        return replacedBy;
    }

    public void setReplacedBy(User replacedBy) {
        this.replacedBy = replacedBy;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
