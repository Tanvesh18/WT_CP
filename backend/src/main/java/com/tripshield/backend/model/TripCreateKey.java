package com.tripshield.backend.model;

import jakarta.persistence.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"actor_user_id", "request_key"}))
public class TripCreateKey {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public Long actorUserId;
    @Column(nullable = false, length = 100) public String requestKey;
    @Column(nullable = false, length = 64) public String requestHash;
    @Column(nullable = false) public Long tripId;
}
