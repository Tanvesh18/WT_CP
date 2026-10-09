package com.tripshield.backend.service;

import com.tripshield.backend.model.Alternative;
import com.tripshield.backend.model.Trip;
import com.tripshield.backend.model.TripItem;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisruptionPlannerTest {
    private final DisruptionPlanner planner = new DisruptionPlanner();

    @Test
    void flightCancellationFlagsTightTransferButNotLaterHotel() {
        Trip trip = trip();
        TripItem flight = item(trip, 1, "FLIGHT", "Flight", "2026-12-10T09:00", "2026-12-10T11:00");
        item(trip, 2, "TRANSPORT", "Transfer", "2026-12-10T12:00", "2026-12-10T13:00");
        item(trip, 3, "HOTEL", "Hotel", "2026-12-10T15:00", "2026-12-12T10:00");
        var impacts = planner.assessDisruption(trip, flight, "FLIGHT_CANCELLATION");
        assertEquals(1, impacts.size());
        assertEquals("Transfer", impacts.get(0).title());
    }

    @Test
    void dependentTransferCannotLeaveBeforeReplacementFlightArrives() {
        Trip trip = trip();
        TripItem flight = item(trip, 1, "FLIGHT", "Flight", "2026-12-10T09:00", "2026-12-10T11:00");
        flight.status = "REPLACED";
        TripItem transfer = item(trip, 2, "TRANSPORT", "Transfer", "2026-12-10T12:00", "2026-12-10T13:00");
        transfer.status = "AT_RISK"; transfer.riskSourceItemId = flight.id;
        TripItem replacement = item(trip, 4, "FLIGHT", "Later flight", "2026-12-10T13:00", "2026-12-10T15:00");
        replacement.replacesItemId = flight.id;
        assertEquals(LocalDateTime.parse("2026-12-10T15:45"), planner.earliestFeasibleStart(trip, transfer));
        var options = planner.generate(trip, transfer, "CONNECTION_RISK");
        assertFalse(options.isEmpty());
        assertTrue(options.stream().allMatch(option -> !option.startsAt.isBefore(LocalDateTime.parse("2026-12-10T15:45"))));
        Alternative impossible = new Alternative(trip.id, transfer.id, "TRANSPORT", "Too early", "Delhi",
                LocalDateTime.parse("2026-12-10T14:00"), LocalDateTime.parse("2026-12-10T15:00"), "500", "Test");
        assertFalse(planner.fits(trip, transfer, impossible));
    }

    @Test
    void recoveredTransferDoesNotFlagItsUpstreamFlight() {
        Trip trip = trip();
        TripItem flight = item(trip, 1, "FLIGHT", "Flight", "2026-12-10T09:00", "2026-12-10T11:00");
        flight.status = "REPLACED";
        TripItem transfer = item(trip, 2, "TRANSPORT", "Transfer", "2026-12-10T12:00", "2026-12-10T13:00");
        transfer.status = "AT_RISK"; transfer.riskSourceItemId = flight.id;
        item(trip, 3, "HOTEL", "Hotel", "2026-12-10T15:00", "2026-12-12T10:00");
        TripItem replacement = item(trip, 4, "FLIGHT", "Later flight", "2026-12-10T13:00", "2026-12-10T15:00");
        replacement.replacesItemId = flight.id;
        Alternative car = new Alternative(trip.id, transfer.id, "TRANSPORT", "Car", "Delhi",
                LocalDateTime.parse("2026-12-10T16:00"), LocalDateTime.parse("2026-12-10T17:00"), "2800", "Test");
        var impacted = planner.assessOption(trip, transfer, car);
        assertTrue(impacted.stream().noneMatch(segment -> segment.id().equals(replacement.id)));
    }

    private Trip trip() {
        Trip trip = new Trip(); trip.id = 10L;
        trip.startDate = LocalDate.parse("2026-12-10"); trip.endDate = LocalDate.parse("2026-12-13");
        return trip;
    }

    private TripItem item(Trip trip, long id, String kind, String title, String start, String end) {
        TripItem item = new TripItem(); item.id = id; item.trip = trip; item.kind = kind; item.title = title;
        item.location = "Delhi"; item.startsAt = LocalDateTime.parse(start); item.endsAt = LocalDateTime.parse(end);
        trip.items.add(item); return item;
    }
}
