package com.tripshield.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
public class Alternative {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String kind;
    public String title;
    public String location;
    public int delayMinutes;
    public BigDecimal estimatedCost;
    public String description;
    public String source;
    public String currency;
    public String flightOfferId;
    public String flightExpiresAt;
    public String flightOriginTimeZone;
    public String flightDestinationTimeZone;
    public Integer arrivalDelayMinutes;
    public BigDecimal costDifference;
    public Integer flightStops;
    public Integer flightDurationMinutes;
    public String flightOperatingCarriers;
    @Column(columnDefinition = "TEXT") public String flightRouteJson;
    public String recoverySearchId;
    public Long tripId;
    public Long itemId;
    public LocalDateTime startsAt;
    public LocalDateTime endsAt;

    public Alternative() {}
    public Alternative(Long tripId, Long itemId, String kind, String title, String location,
                       LocalDateTime startsAt, LocalDateTime endsAt, String cost, String description) {
        this.tripId = tripId; this.itemId = itemId; this.kind = kind; this.title = title;
        this.location = location; this.startsAt = startsAt; this.endsAt = endsAt;
        this.estimatedCost = new BigDecimal(cost); this.description = description;
    }
}
