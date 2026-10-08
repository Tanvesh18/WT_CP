package com.tripshield.backend.security;
import jakarta.persistence.*;
@Entity
public class AppUser {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(nullable=false,unique=true) public String email;
 public String name;
 public String passwordHash;
 public String role = "TRAVELER";
}
