package com.tripshield.backend.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Trip {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String traveler;
    public String travelerEmail;
    public Long ownerUserId;
    public BigDecimal estimatedBudget;
    public String budgetCurrency;
    public String reviewComment;
    public String lifecycle = "ACTIVE";
    public String origin;
    public String destination;
    public String originAirportCode;
    public String destinationAirportCode;
    public LocalDate startDate;
    public LocalDate endDate;
    public String checkInStatus = "PENDING";
    public LocalDateTime checkedInAt;
    public String checkInNote;
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startsAt ASC")
    public List<TripItem> items = new ArrayList<>();
}
