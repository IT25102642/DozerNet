package com.dozernet.common.audit;

import com.dozernet.common.model.BaseEntity;
import com.dozernet.common.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One recorded action in the administrative audit trail. Written by
 * {@link AuditService} from every module whenever an administrator changes
 * something that affects money, availability or verification status.
 *
 * <p>Records are append-only: nothing in the application updates or deletes an
 * audit row, so the trail stays trustworthy.</p>
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseEntity {

    /** Who performed the action. Null only for system-initiated events. */
    @ManyToOne(fetch = FetchType.EAGER)
    private User actor;

    /** What happened, e.g. "BOOKING_APPROVED". */
    @Column(nullable = false, length = 60)
    private String action;

    /** Which kind of record was affected, e.g. "Booking". */
    @Column(nullable = false, length = 40)
    private String entityType;

    /** Identifier of the affected record. */
    private Long entityId;

    /** Human-readable context shown in the admin audit table. */
    @Column(length = 500)
    private String details;

    public AuditLog() {
    }

    public AuditLog(User actor, String action, String entityType, Long entityId, String details) {
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
    }

    public User getActor() {
        return actor;
    }

    public void setActor(User actor) {
        this.actor = actor;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    /** Actor name for the audit table, falling back to "System". */
    public String getActorLabel() {
        return actor == null ? "System" : actor.getFullName();
    }
}
