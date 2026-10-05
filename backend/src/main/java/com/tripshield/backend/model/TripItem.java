package com.tripshield.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class TripItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "trip_id", nullable = false)
    public Trip trip;
    public String kind;
    public String title;
    public String location;
    public LocalDateTime startsAt;
    public LocalDateTime endsAt;
    public String status = "CONFIRMED";
    public String changeNote;
}
