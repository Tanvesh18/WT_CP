package com.tripshield.backend;

import com.tripshield.backend.model.Alternative;
import com.tripshield.backend.repository.AlternativeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeedData {
    @Bean
    CommandLineRunner seedAlternatives(AlternativeRepository repository) {
        return args -> {
            if (repository.count() > 0) return;
            repository.save(new Alternative("FLIGHT", "Later direct flight", "Same route", 180, "8500", "Arrives three hours later"));
            repository.save(new Alternative("FLIGHT", "Connecting flight", "Via hub airport", 90, "11200", "One connection"));
            repository.save(new Alternative("HOTEL", "Nearby business hotel", "City center", 0, "6200", "Alternative accommodation nearby"));
            repository.save(new Alternative("HOTEL", "Airport hotel", "Airport district", 0, "4800", "Convenient for early departures"));
            repository.save(new Alternative("TRANSPORT", "Prebooked cab", "Same route", 30, "1900", "Pickup thirty minutes later"));
            repository.save(new Alternative("TRANSPORT", "Rail transfer", "Same route", 60, "1200", "Depart one hour later"));
        };
    }
}
