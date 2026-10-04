package com.dozernet.module3_fleet.entity;

import com.dozernet.common.model.BaseEntity;
import com.dozernet.common.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * A JCB / construction machine in the DozerNet inventory. Covers both company
 * fleet and admin-verified private owner listings.
 */
@Entity
@Table(name = "machines")
public class Machine extends BaseEntity {

    @Column(nullable = false)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MachineType type;

    @Column(nullable = false, unique = true)
    private String registrationNumber;

    @Column(nullable = false)
    private String location;

    /** Rental price per day, in LKR. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal dailyRate;

    @Column(length = 1000)
    private String description;

    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MachineStatus status = MachineStatus.AVAILABLE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Ownership ownership = Ownership.COMPANY;

    /** Owner for PRIVATE machines; null for COMPANY machines. */
    @ManyToOne(fetch = FetchType.LAZY)
    private User owner;

    /** Private listings start unverified until an admin approves them. */
    @Column(nullable = false)
    private boolean verified = true;

    public Machine() {
    }

    /** A machine can be booked only when available and verified. */
    public boolean isBookable() {
        return status == MachineStatus.AVAILABLE && verified;
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

    public MachineStatus getStatus() {
        return status;
    }

    public void setStatus(MachineStatus status) {
        this.status = status;
    }

    public Ownership getOwnership() {
        return ownership;
    }

    public void setOwnership(Ownership ownership) {
        this.ownership = ownership;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }
}
