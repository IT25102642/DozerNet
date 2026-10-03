package com.dozernet.common.notification;

/**
 * Observer contract. Each concrete observer reacts to a {@link NotificationEvent}
 * in its own channel (in-app, email, ...). New channels can be added simply by
 * creating another Spring bean that implements this interface - no change to
 * the publisher or callers.
 */
public interface NotificationObserver {

    void onEvent(NotificationEvent event);
}
