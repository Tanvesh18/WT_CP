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
    public String disruptionType;
    public Long riskSourceItemId;
    public Long replacesItemId;
    public String flightSource;
    public String flightOfferId;
    public String flightAmount;
    public String flightCurrency;
    public String flightExpiresAt;
    public String flightOriginTimeZone;
    public String flightDestinationTimeZone;
    public Integer flightDurationMinutes;
    public Integer flightStops;
    public String flightOperatingCarriers;
    @Column(columnDefinition = "TEXT") public String flightRouteJson;
    public String recoverySearchId;
}
