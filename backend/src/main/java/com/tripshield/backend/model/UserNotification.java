package com.tripshield.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class UserNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public Long userId;
    @Column(nullable = false) public Long tripId;
    @Column(nullable = false) public String type;
    @Column(nullable = false, length = 500) public String message;
    @Column(nullable = false) public Instant createdAt = Instant.now();
    public Instant readAt;
}
