package com.dozernet.common.notification;

import com.dozernet.common.user.User;

/**
 * An event that observers react to (Observer pattern). Carries the recipient
 * and a human-readable title/message.
 */
public record NotificationEvent(User recipient, String title, String message) {
}
