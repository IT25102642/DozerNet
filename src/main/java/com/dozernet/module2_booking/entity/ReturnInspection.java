package com.dozernet.module2_booking.entity;

import com.dozernet.common.document.Document;
import com.dozernet.common.model.BaseEntity;
import com.dozernet.common.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * The condition report filed when a machine comes back at the end of a rental.
 * One inspection per completed booking, recording what was found, who is
 * responsible for any damage, and an optional photo.
 */
@Entity
@Table(name = "return_inspections")
public class ReturnInspection extends BaseEntity {

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(unique = true)
    private Booking booking;

    /** Administrator who carried out the inspection. */
    @ManyToOne(fetch = FetchType.EAGER)
    private User inspectedBy;

    @Column(nullable = false, length = 1000)
    private String notes = "";

    @Column(nullable = false)
    private boolean damageReported = false;

    @Column(length = 1000)
    private String damageDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResponsibleParty responsibleParty = ResponsibleParty.NONE;

    /** Estimated repair cost, when damage was found. */
    @Column(precision = 12, scale = 2)
    private BigDecimal estimatedRepairCost;

    /** Optional photo of the machine/damage, stored via the shared document service. */
    @ManyToOne(fetch = FetchType.EAGER)
    private Document photo;

    public ReturnInspection() {
    }

    public ReturnInspection(Booking booking, User inspectedBy) {
        this.booking = booking;
        this.inspectedBy = inspectedBy;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public User getInspectedBy() {
        return inspectedBy;
    }

    public void setInspectedBy(User inspectedBy) {
        this.inspectedBy = inspectedBy;
    }

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

    public Document getPhoto() {
        return photo;
    }

    public void setPhoto(Document photo) {
        this.photo = photo;
    }
}
