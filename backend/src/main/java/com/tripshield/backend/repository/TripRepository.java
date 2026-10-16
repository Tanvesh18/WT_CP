package com.tripshield.backend.repository;

import com.tripshield.backend.model.Trip;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {
    @Override @EntityGraph(attributePaths = "items")
    List<Trip> findAll();
    @Override @EntityGraph(attributePaths = "items")
    Optional<Trip> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :id")
    Optional<Trip> findByIdForUpdate(Long id);
}
