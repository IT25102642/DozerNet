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

    /**
     * Standard photo for a machine type. Used as the default when a machine has no photo and
     * as the fallback when its own photo URL is dead, so a card never shows a broken image.
     */
    public static String defaultImageFor(MachineType type) {
        if (type == null) {
            return "/images/machines/excavator.jpg";
        }
        return switch (type) {
            case BACKHOE_LOADER -> "/images/machines/backhoe-loader.jpg";
            case EXCAVATOR -> "/images/machines/excavator.jpg";
            case WHEEL_LOADER -> "/images/machines/wheel-loader.jpg";
            case SKID_STEER -> "/images/machines/skid-steer.jpg";
            case TELEHANDLER -> "/images/machines/telehandler.jpg";
            case COMPACTOR -> "/images/machines/compactor.jpg";
            case BULLDOZER -> "/images/machines/bulldozer.jpg";
            // Interim photos for the two newest categories - replace with
            // motor-grader.jpg / dump-truck.jpg once real shots are available.
            case MOTOR_GRADER -> "/images/machines/bulldozer.jpg";
            case DUMP_TRUCK -> "/images/machines/wheel-loader.jpg";
        };
    }

    /** The standard photo for this machine's type (see {@link #defaultImageFor}). */
    public String getFallbackImageUrl() {
        return defaultImageFor(type);
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
