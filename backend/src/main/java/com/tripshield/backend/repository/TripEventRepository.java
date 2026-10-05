package com.tripshield.backend.repository;

import com.tripshield.backend.model.TripEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TripEventRepository extends JpaRepository<TripEvent, Long> {
    List<TripEvent> findByTripIdOrderByOccurredAtDescIdDesc(Long tripId);
}
