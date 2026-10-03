package com.dozernet.common.audit;

import com.dozernet.common.security.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;


/**
 * Writes the administrative audit trail. Every module calls
 * {@link #record(String, String, Long, String)} after an action that changes
 * money, availability or verification status, so admins can answer "who
 * changed this, and when?" long after the fact.
 *
 * <p>Auditing must never break the action being audited, so failures here are
 * logged rather than propagated.</p>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final CurrentUserService currentUserService;

    public AuditService(AuditLogRepository auditLogRepository, CurrentUserService currentUserService) {
        this.auditLogRepository = auditLogRepository;
        this.currentUserService = currentUserService;
    }

    /** Records an action performed by the currently signed-in user. */
    public void record(String action, String entityType, Long entityId, String details) {
        try {
            AuditLog entry = new AuditLog(currentUserService.current().orElse(null),
                    action, entityType, entityId, truncate(details));
            auditLogRepository.save(entry);
        } catch (RuntimeException ex) {
            log.warn("Could not write audit entry {} for {}#{}", action, entityType, entityId, ex);
        }
    }

    public Page<AuditLog> recent(int page, int size) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    private static String truncate(String details) {
        if (details == null) {
            return null;
        }
        return details.length() <= 500 ? details : details.substring(0, 497) + "...";
    }
}
