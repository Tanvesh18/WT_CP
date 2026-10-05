package com.tripshield.backend.repository;

import com.tripshield.backend.model.Alternative;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AlternativeRepository extends JpaRepository<Alternative, Long> {
    List<Alternative> findByKindIgnoreCase(String kind);
}
