package com.tripshield.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class TripInvitation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public Long tripId;
    @Column(nullable = false) public String email;
    @Column(nullable = false, unique = true, length = 64) public String tokenHash;
    @Column(nullable = false) public Instant expiresAt;
    public Instant usedAt;
    @Column(nullable = false) public Long issuedByUserId;
}
