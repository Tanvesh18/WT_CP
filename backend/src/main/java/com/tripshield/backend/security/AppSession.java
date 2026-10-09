package com.tripshield.backend.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class AppSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false, unique = true, length = 64)
    public String tokenHash;
    @Column(nullable = false)
    public Long userId;
    @Column(nullable = false)
    public Instant expiresAt;
}
