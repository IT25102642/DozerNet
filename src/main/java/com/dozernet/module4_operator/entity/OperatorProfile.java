package com.dozernet.module4_operator.entity;

import com.dozernet.common.model.BaseEntity;
import com.dozernet.common.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Extra details for a user with the OPERATOR role. Company operators are added
 * by an admin (verified immediately); independent drivers self-register and
 * must have their licence verified before they can be assigned to jobs.
 */
@Entity
@Table(name = "operator_profiles")
public class OperatorProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(nullable = false, unique = true)
    private String licenceNumber;

    @Column(nullable = false)
    private int experienceYears;

    /** true = independent driver, false = company operator. */
    @Column(nullable = false)
    private boolean independent;

    /** Licence verified by an admin. */
    @Column(nullable = false)
    private boolean verified;

    public OperatorProfile() {
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getLicenceNumber() {
        return licenceNumber;
    }

    public void setLicenceNumber(String licenceNumber) {
        this.licenceNumber = licenceNumber;
    }

    public int getExperienceYears() {
        return experienceYears;
    }

    public void setExperienceYears(int experienceYears) {
        this.experienceYears = experienceYears;
    }

    public boolean isIndependent() {
        return independent;
    }

    public void setIndependent(boolean independent) {
        this.independent = independent;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }
}
