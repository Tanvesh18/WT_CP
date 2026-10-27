package com.tripshield.backend.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DemoFlightsTest {
    private final DemoFlights flights = new DemoFlights();

    @Test
    void londonConnectionUsesDubaiLegsAndRemainsClearlySimulated() {
        LocalDate date = LocalDate.now().plusDays(8);
        var offer = flights.search("BOM", "LHR", date).stream().filter(value -> value.stops() == 1).findFirst().orElseThrow();
        assertEquals("DEMO", offer.source());
        assertEquals(2, offer.legs().size());
        assertEquals("BOM", offer.legs().getFirst().origin());
        assertEquals("DXB", offer.legs().getFirst().destination());
        assertEquals("DXB", offer.legs().getLast().origin());
        assertEquals("LHR", offer.legs().getLast().destination());
        assertEquals(760, offer.durationMinutes());
        assertEquals(offer, flights.find(offer.id()));
    }
}
