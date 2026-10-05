package com.tripshield.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class TripEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long tripId;
    public Long itemId;
    public String type;
    @Column(length = 1000)
    public String details;
    public LocalDateTime occurredAt;

    public TripEvent() {}
    public TripEvent(Long tripId, Long itemId, String type, String details) {
        this.tripId = tripId; this.itemId = itemId; this.type = type;
        this.details = details; this.occurredAt = LocalDateTime.now();
    }
}
