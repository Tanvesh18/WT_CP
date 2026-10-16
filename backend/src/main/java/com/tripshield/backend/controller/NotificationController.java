package com.tripshield.backend.controller;

import com.tripshield.backend.model.UserNotification;
import com.tripshield.backend.repository.UserNotificationRepository;
import com.tripshield.backend.security.Access;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final Access access;
    private final UserNotificationRepository notifications;
    public NotificationController(Access access, UserNotificationRepository notifications) {
        this.access = access; this.notifications = notifications;
    }
    public record Inbox(List<UserNotification> items, long unreadCount) {}
    @GetMapping
    public Inbox list() {
        long userId = access.current().id;
        return new Inbox(notifications.findByUserIdOrderByCreatedAtDescIdDesc(userId), notifications.countByUserIdAndReadAtIsNull(userId));
    }
    @PostMapping("/{id}/read")
    @Transactional
    public UserNotification markRead(@PathVariable Long id) {
        UserNotification notification = notifications.findByIdAndUserId(id, access.current().id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (notification.readAt == null) notification.readAt = Instant.now();
        return notifications.save(notification);
    }
}
