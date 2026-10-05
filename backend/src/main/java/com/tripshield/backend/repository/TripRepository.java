package com.tripshield.backend.repository;

import com.tripshield.backend.model.Trip;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {
    @Override @EntityGraph(attributePaths = "items")
    List<Trip> findAll();
    @Override @EntityGraph(attributePaths = "items")
    Optional<Trip> findById(Long id);
}
