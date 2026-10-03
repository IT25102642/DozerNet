package com.dozernet.common.web;

import com.dozernet.common.notification.NotificationService;
import com.dozernet.common.security.CurrentUserService;
import com.dozernet.common.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Injects a few attributes into every view: the current user, their unread
 * notification count, and the configured currency symbol. Keeps templates clean.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;

    @Value("${dozernet.currency-symbol:Rs.}")
    private String currencySymbol;

    public GlobalModelAdvice(CurrentUserService currentUserService,
                             NotificationService notificationService) {
        this.currentUserService = currentUserService;
        this.notificationService = notificationService;
    }

    @ModelAttribute("currentUser")
    public User currentUser() {
        return currentUserService.current().orElse(null);
    }

    @ModelAttribute("unreadNotifications")
    public long unreadNotifications() {
        return currentUserService.current()
                .map(notificationService::unreadCount)
                .orElse(0L);
    }

    @ModelAttribute("currencySymbol")
    public String currencySymbol() {
        return currencySymbol;
    }
}
