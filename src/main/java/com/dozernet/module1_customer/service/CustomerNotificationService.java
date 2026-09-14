package com.dozernet.module1_customer.service;

import com.dozernet.common.model.Role;
import com.dozernet.common.notification.Notification;
import com.dozernet.common.notification.NotificationService;
import com.dozernet.common.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Module 1 ownership of the customer notification inbox. Publishing still goes
 * through the shared Observer-based {@link NotificationService}; this service
 * exposes customer-only read/mark-read operations.
 */
@Service
public class CustomerNotificationService {

    private final NotificationService notificationService;

    public CustomerNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public List<Notification> forCustomer(User customer) {
        requireCustomer(customer);
        return notificationService.forUser(customer);
    }

    public long unreadCount(User customer) {
        requireCustomer(customer);
        return notificationService.unreadCount(customer);
    }

    @Transactional
    public void markAllRead(User customer) {
        requireCustomer(customer);
        notificationService.markAllRead(customer);
    }

    private static void requireCustomer(User user) {
        if (user == null || !user.hasRole(Role.CUSTOMER)) {
            throw new IllegalArgumentException("Notifications inbox is for customers only.");
        }
    }
}
