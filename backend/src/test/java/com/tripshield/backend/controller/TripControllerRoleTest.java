package com.tripshield.backend.controller;

import com.tripshield.backend.model.Trip;
import com.tripshield.backend.repository.AlternativeRepository;
import com.tripshield.backend.repository.TripEventRepository;
import com.tripshield.backend.repository.TripRepository;
import com.tripshield.backend.security.Access;
import com.tripshield.backend.security.AppUser;
import com.tripshield.backend.service.DisruptionPlanner;
import com.tripshield.backend.service.EmailAlerts;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TripControllerRoleTest {
    private final TripRepository trips = mock(TripRepository.class);
    private final AlternativeRepository alternatives = mock(AlternativeRepository.class);
    private final TripEventRepository events = mock(TripEventRepository.class);
    private final EmailAlerts email = mock(EmailAlerts.class);
    private final Access access = mock(Access.class);
    private final DisruptionPlanner planner = mock(DisruptionPlanner.class);
    private final FlightController flights = mock(FlightController.class);
    private final TripController controller = new TripController(trips, alternatives, events, email, access, planner, flights);

    private TripController.TripInput input(String email) {
        LocalDate day = LocalDate.now().plusDays(3);
        return new TripController.TripInput("Other name", email, "Pune", "Delhi", null, null, day, day.plusDays(1),
                List.of(new TripController.ItemInput("FLIGHT", "Flight", "Pune airport", day.atTime(8, 0), day.atTime(10, 0), null, null, null, null, null)));
    }

    @Test void travelerCanRequestOnlyForOwnEmail() {
        AppUser traveler = new AppUser(); traveler.role = "TRAVELER"; traveler.email = "asha@example.com"; traveler.name = "Asha";
        when(access.current()).thenReturn(traveler);
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller.create(input("other@example.com")));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(trips);
    }

    @Test void travelerRequestIsSavedPendingReview() {
        AppUser traveler = new AppUser(); traveler.role = "TRAVELER"; traveler.email = "asha@example.com"; traveler.name = "Asha";
        when(access.current()).thenReturn(traveler);
        when(trips.saveAndFlush(any(Trip.class))).thenAnswer(call -> call.getArgument(0));
        var result = controller.create(input("asha@example.com"));
        assertEquals("REQUESTED", result.status());
        assertEquals("Asha", result.traveler());
        assertEquals("asha@example.com", result.travelerEmail());
        verify(events).save(argThat(event -> "BOOKING_REQUESTED".equals(event.type)));
    }
    @Test void pastDepartureIsRejected() {
        LocalDate yesterday = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).minusDays(1);
        var input = new TripController.TripInput("Asha", "asha@example.com", "Pune", "Delhi", null, null,
                yesterday, yesterday.plusDays(1), List.of(new TripController.ItemInput("FLIGHT", "Flight", "Airport",
                yesterday.atTime(8, 0), yesterday.atTime(10, 0), null, null, null, null, null)));
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller.create(input));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(trips);
    }
}
