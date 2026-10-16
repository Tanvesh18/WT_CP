package com.tripshield.backend.service;

import com.tripshield.backend.model.Trip;
import com.tripshield.backend.model.UserNotification;
import com.tripshield.backend.repository.UserNotificationRepository;
import org.springframework.stereotype.Service;

@Service
public class TravelerNotifications {
    private final UserNotificationRepository repository;
    public TravelerNotifications(UserNotificationRepository repository) { this.repository = repository; }
    public void add(Trip trip, String type, String message) {
        if (trip.ownerUserId == null) return;
        UserNotification notification = new UserNotification();
        notification.userId = trip.ownerUserId; notification.tripId = trip.id;
        notification.type = type; notification.message = message;
        repository.save(notification);
    }
}
