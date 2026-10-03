package com.dozernet.common.notification;

import org.springframework.stereotype.Component;

/**
 * Observer that persists an in-app notification the user sees in the UI.
 */
@Component
public class InAppNotificationObserver implements NotificationObserver {

    private final NotificationRepository notificationRepository;

    public InAppNotificationObserver(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public void onEvent(NotificationEvent event) {
        notificationRepository.save(
                new Notification(event.recipient(), event.title(), event.message()));
    }
}
