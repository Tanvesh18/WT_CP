package com.tripshield.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class NotificationOutbox {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public Long tripId;
    public String recipient;
    public String subject;
    @Column(columnDefinition = "TEXT") public String body;
    @Column(nullable = false) public String status = "PENDING";
    @Column(nullable = false) public int attempts;
    @Column(nullable = false) public Instant nextAttemptAt = Instant.now();
    @Column(nullable = false) public Instant createdAt = Instant.now();
    public Instant sentAt;
    public String lastError;
}
