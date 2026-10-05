package com.tripshield.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

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

    public Alternative() {}
    public Alternative(String kind, String title, String location, int delayMinutes, String cost, String description) {
        this.kind = kind; this.title = title; this.location = location;
        this.delayMinutes = delayMinutes; this.estimatedCost = new BigDecimal(cost); this.description = description;
    }
}
