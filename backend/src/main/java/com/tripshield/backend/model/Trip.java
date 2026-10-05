package com.tripshield.backend.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Trip {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String traveler;
    public String origin;
    public String destination;
    public LocalDate startDate;
    public LocalDate endDate;
    public String checkInStatus = "PENDING";
    public LocalDateTime checkedInAt;
    public String checkInNote;
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startsAt ASC")
    public List<TripItem> items = new ArrayList<>();
}
