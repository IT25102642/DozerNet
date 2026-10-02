package com.dozernet.common.notification;

import com.dozernet.common.user.User;
import com.dozernet.common.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Subject/publisher in the Observer pattern. Spring injects every
 * {@link NotificationObserver} bean, so publishing an event fans out to all
 * channels (in-app + email) automatically. Also exposes read helpers for the UI.
 *
 * <p>When a message cannot be delivered - the account has no usable contact
 * details, or a channel throws - the recipient is flagged as unreachable so the
 * customer is prompted to fix their details and admins can see the warning.
 * Bookings are deliberately not blocked by this flag.</p>
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** Deliberately permissive: we only need to catch clearly unusable addresses. */
    private static final String EMAIL_SHAPE = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private final List<NotificationObserver> observers;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(List<NotificationObserver> observers,
                               NotificationRepository notificationRepository,
                               UserRepository userRepository) {
        this.observers = observers;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    /** Notify a single user across all channels. */
    public void notify(User recipient, String title, String message) {
        if (recipient == null) {
            return;
        }
        if (!hasUsableContactDetails(recipient)) {
            flagUnreachable(recipient, "no usable contact details on file");
            return;
        }

        NotificationEvent event = new NotificationEvent(recipient, title, message);
        boolean delivered = true;
        for (NotificationObserver observer : observers) {
            try {
                observer.onEvent(event);
            } catch (RuntimeException ex) {
                delivered = false;
                log.warn("Notification channel {} failed for {}",
                        observer.getClass().getSimpleName(), recipient.getEmail(), ex);
            }
        }

        if (!delivered) {
            flagUnreachable(recipient, "a delivery channel rejected the message");
        } else if (recipient.isContactUnreachable()) {
            // Details evidently work again - clear the warning.
            recipient.setContactUnreachable(false);
            userRepository.save(recipient);
        }
    }

    public List<Notification> forUser(User user) {
        return notificationRepository.findByRecipientOrderByCreatedAtDesc(user);
    }

    public long unreadCount(User user) {
        return notificationRepository.countByRecipientAndReadFlagFalse(user);
    }

    public void markAllRead(User user) {
        List<Notification> items = notificationRepository.findByRecipientOrderByCreatedAtDesc(user);
        items.forEach(n -> n.setReadFlag(true));
        notificationRepository.saveAll(items);
    }

    private static boolean hasUsableContactDetails(User user) {
        String email = user.getEmail();
        return email != null && email.matches(EMAIL_SHAPE);
    }

    private void flagUnreachable(User recipient, String reason) {
        log.warn("Could not notify {}: {}", recipient.getEmail(), reason);
        if (!recipient.isContactUnreachable()) {
            recipient.setContactUnreachable(true);
            userRepository.save(recipient);
        }
    }
}
