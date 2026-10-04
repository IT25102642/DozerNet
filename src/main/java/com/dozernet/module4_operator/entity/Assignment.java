package com.dozernet.module4_operator.entity;

import com.dozernet.common.model.BaseEntity;
import com.dozernet.common.user.User;
import com.dozernet.module2_booking.entity.Booking;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Links an approved booking to the operator carrying out the job, and tracks
 * the job's progress. One assignment per booking.
 */
@Entity
@Table(name = "assignments")
public class Assignment extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User operator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus jobStatus = JobStatus.ASSIGNED;

    public Assignment() {
    }

    public Assignment(Booking booking, User operator) {
        this.booking = booking;
        this.operator = operator;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public User getOperator() {
        return operator;
    }

    public void setOperator(User operator) {
        this.operator = operator;
    }

    public JobStatus getJobStatus() {
        return jobStatus;
    }

    public void setJobStatus(JobStatus jobStatus) {
        this.jobStatus = jobStatus;
    }
}
