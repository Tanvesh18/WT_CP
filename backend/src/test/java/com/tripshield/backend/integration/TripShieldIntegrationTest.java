package com.tripshield.backend.integration;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripshield.backend.controller.FlightController;
import com.tripshield.backend.controller.DemoFlights;
import com.tripshield.backend.model.Alternative;
import com.tripshield.backend.repository.AlternativeRepository;
import com.tripshield.backend.repository.TripEventRepository;
import com.tripshield.backend.repository.TripRepository;
import com.tripshield.backend.repository.TripCreateKeyRepository;
import com.tripshield.backend.repository.TripInvitationRepository;
import com.tripshield.backend.repository.UserNotificationRepository;
import com.tripshield.backend.repository.NotificationOutboxRepository;
import com.tripshield.backend.service.EmailAlerts;
import com.tripshield.backend.security.AppSession;
import com.tripshield.backend.security.AppSessionRepository;
import com.tripshield.backend.security.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseGuard.class)
class TripShieldIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired AppSessionRepository sessions;
    @Autowired TripRepository trips;
    @Autowired AlternativeRepository alternatives;
    @Autowired TripEventRepository events;
    @Autowired TripCreateKeyRepository createKeys;
    @Autowired TripInvitationRepository invitations;
    @Autowired UserNotificationRepository notifications;
    @Autowired NotificationOutboxRepository outbox;
    @Autowired EmailAlerts emailAlerts;
    @Autowired DemoFlights demoFlights;

    @MockitoSpyBean FlightController flights;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void cleanTestRows() {
        outbox.deleteAll();
        notifications.deleteAll();
        invitations.deleteAll();
        createKeys.deleteAll();
        events.deleteAll();
        alternatives.deleteAll();
        trips.deleteAll();
        sessions.deleteAll();
        users.deleteAll();
    }

    @Test
    void travelerCanRegisterLoginAndLogout() throws Exception {
        String email = email();
        MvcResult registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Test Traveler", "email", email, "password", "correct-horse-42"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("TRAVELER")).andReturn();
        String token = body(registered).path("token").asText();
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));

        MvcResult loggedIn = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "correct-horse-42"))))
                .andExpect(status().isOk()).andReturn();
        String loginToken = body(loggedIn).path("token").asText();
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(loginToken))).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(loginToken))).andExpect(status().isUnauthorized());
    }

    @Test
    void browserCookieSessionHasCsrfRotationAndLogoutProtection() throws Exception {
        String email = email();
        MvcResult registered = mvc.perform(post("/api/auth/register").header("X-TripShield-Browser", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Cookie Traveler", "email", email, "password", "correct-horse-42"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").value("")).andReturn();
        String sessionHeader = registered.getResponse().getHeaders("Set-Cookie").stream().filter(value -> value.startsWith("TRIPSHIELD_SESSION=")).findFirst().orElseThrow();
        String csrfHeader = registered.getResponse().getHeaders("Set-Cookie").stream().filter(value -> value.startsWith("TRIPSHIELD_CSRF=")).findFirst().orElseThrow();
        assertTrue(sessionHeader.contains("HttpOnly")); assertTrue(sessionHeader.contains("SameSite=Lax"));
        assertTrue(sessionHeader.contains("Path=/api")); assertTrue(sessionHeader.contains("Max-Age=43200"));
        assertTrue(!sessionHeader.contains("Secure"));
        assertTrue(!csrfHeader.contains("HttpOnly")); assertTrue(csrfHeader.contains("SameSite=Lax"));
        Cookie sessionCookie = new Cookie("TRIPSHIELD_SESSION", sessionHeader.split(";", 2)[0].split("=", 2)[1]);
        Cookie csrfCookie = new Cookie("TRIPSHIELD_CSRF", csrfHeader.split(";", 2)[0].split("=", 2)[1]);
        mvc.perform(get("/api/auth/me").cookie(sessionCookie, csrfCookie)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        mvc.perform(get("/api/trips")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/trips/999999")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/trips/1/cancel").cookie(sessionCookie, csrfCookie)).andExpect(status().isForbidden());
        mvc.perform(post("/api/trips/1/cancel").cookie(sessionCookie, csrfCookie).header("X-TripShield-CSRF", "wrong"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/trips/1/cancel").cookie(sessionCookie, csrfCookie).header("X-TripShield-CSRF", csrfCookie.getValue())).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").cookie(sessionCookie, csrfCookie)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").cookie(sessionCookie, csrfCookie).header("X-TripShield-CSRF", csrfCookie.getValue())).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").cookie(sessionCookie, csrfCookie)).andExpect(status().isUnauthorized());
        assertTrue(sessions.findAll().isEmpty());
    }

    @Test
    void browserLoginRotatesSessionAndSecureRequestSetsSecureCookie() throws Exception {
        String email = email();
        String firstToken = register(email);
        MvcResult login = mvc.perform(post("/api/auth/login").secure(true).header("X-TripShield-Browser", "1")
                        .header("Authorization", bearer(firstToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "correct-horse-42"))))
                .andExpect(status().isOk()).andReturn();
        String sessionHeader = login.getResponse().getHeaders("Set-Cookie").stream().filter(value -> value.startsWith("TRIPSHIELD_SESSION=")).findFirst().orElseThrow();
        assertTrue(sessionHeader.contains("Secure"));
        assertEquals(1, sessions.count());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(firstToken))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", "wrong")))).andExpect(status().isUnauthorized());
        Cookie newCookie = new Cookie("TRIPSHIELD_SESSION", sessionHeader.split(";", 2)[0].split("=", 2)[1]);
        AppSession expiring = sessions.findAll().getFirst();
        expiring.expiresAt = Instant.now().minusSeconds(1);
        sessions.saveAndFlush(expiring);
        mvc.perform(get("/api/auth/me").cookie(newCookie)).andExpect(status().isUnauthorized());
        assertTrue(sessions.findAll().isEmpty());
    }

    @Test
    void expiredSessionIsRejectedAndRemoved() throws Exception {
        String token = register(email());
        AppSession session = sessions.findAll().getFirst();
        session.expiresAt = Instant.now().minusSeconds(5);
        sessions.saveAndFlush(session);
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token))).andExpect(status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertTrue(sessions.findAll().isEmpty());
    }

    @Test
    void coordinatorAndTravelerAccessIsScopedToTripOwnership() throws Exception {
        String coordinator = coordinatorToken();
        String ownerEmail = email();
        register(ownerEmail);
        long tripId = createTrip(coordinator, ownerEmail, "Owner itinerary");
        String stranger = register(email());

        mvc.perform(get("/api/trips/{id}", tripId).header("Authorization", bearer(stranger)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/trips/{id}", tripId).header("Authorization", bearer(coordinator)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/trips/{id}", tripId).header("Authorization", bearer(tokenFor(ownerEmail))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/travelers").header("Authorization", bearer(stranger))).andExpect(status().isForbidden());
        mvc.perform(get("/api/travelers").header("Authorization", bearer(coordinator))).andExpect(status().isOk());
    }

    @Test
    void coordinatorAndTravelerEndpointAuthorizationMatrix() throws Exception {
        String coordinator = coordinatorToken();
        String ownerEmail = email();
        String owner = register(ownerEmail);
        String stranger = register(email());
        long ownedTrip = createTrip(coordinator, ownerEmail, "Role matrix itinerary");
        long requestedTrip = body(mvc.perform(post("/api/trips").header("Idempotency-Key", UUID.randomUUID().toString()).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(tripJson(ownerEmail, "Requested role matrix", null, null)))
                .andExpect(status().isCreated()).andReturn()).path("id").asLong();
        long itemId = trips.findById(ownedTrip).orElseThrow().items.getFirst().id;

        mvc.perform(get("/api/trips").header("Authorization", bearer(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) ownedTrip)));
        mvc.perform(get("/api/trips/{id}", ownedTrip).header("Authorization", bearer(stranger))).andExpect(status().isForbidden());
        mvc.perform(get("/api/trips/{id}/history", ownedTrip).header("Authorization", bearer(stranger))).andExpect(status().isForbidden());
        mvc.perform(get("/api/trips/{id}", ownedTrip).header("Authorization", bearer(coordinator))).andExpect(status().isOk());
        mvc.perform(get("/api/trips/{id}/history", ownedTrip).header("Authorization", bearer(coordinator))).andExpect(status().isOk());
        mvc.perform(get("/api/travelers").header("Authorization", bearer(owner))).andExpect(status().isForbidden());
        mvc.perform(get("/api/travelers").header("Authorization", bearer(coordinator))).andExpect(status().isOk());

        String update = editTripJson(ownedTrip, ownerEmail, "Edited by coordinator");
        mvc.perform(put("/api/trips/{id}", ownedTrip).header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isForbidden());
        mvc.perform(post("/api/trips/{id}/approve", requestedTrip).header("Authorization", bearer(owner))).andExpect(status().isForbidden());
        mvc.perform(post("/api/trips/{id}/cancel", ownedTrip).header("Authorization", bearer(owner))).andExpect(status().isForbidden());
        mvc.perform(post("/api/trips/{id}/check-in", ownedTrip).header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "SAFE")))).andExpect(status().isOk());
        mvc.perform(post("/api/trips/{id}/disruptions", ownedTrip).header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("type", "HOTEL_UNAVAILABLE", "itemId", itemId)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/trips/{id}/items/{itemId}/alternatives", ownedTrip, itemId).header("Authorization", bearer(owner))).andExpect(status().isForbidden());
        mvc.perform(post("/api/trips/{id}/items/{itemId}/alternatives/refresh", ownedTrip, itemId).header("Authorization", bearer(owner))).andExpect(status().isForbidden());
        mvc.perform(post("/api/trips/{id}/items/{itemId}/alternatives/{alternativeId}/apply", ownedTrip, itemId, 999999)
                .header("Authorization", bearer(owner))).andExpect(status().isForbidden());
        mvc.perform(get("/api/trips")).andExpect(status().isUnauthorized());

        mvc.perform(put("/api/trips/{id}", ownedTrip).header("Authorization", bearer(coordinator)).contentType(MediaType.APPLICATION_JSON)
                .content(editTripJson(ownedTrip, ownerEmail, "Edited by coordinator"))).andExpect(status().isOk());
        mvc.perform(post("/api/trips/{id}/approve", requestedTrip).header("Authorization", bearer(coordinator))).andExpect(status().isOk());
        mvc.perform(post("/api/trips/{id}/check-in", ownedTrip).header("Authorization", bearer(coordinator)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "SAFE")))).andExpect(status().isOk());
        MvcResult disrupted = mvc.perform(post("/api/trips/{id}/disruptions", ownedTrip).header("Authorization", bearer(coordinator)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("type", "HOTEL_UNAVAILABLE", "itemId", itemId)))).andExpect(status().isOk()).andReturn();
        long alternativeId = body(disrupted).path("alternatives").get(0).path("id").asLong();
        mvc.perform(get("/api/trips/{id}/items/{itemId}/alternatives", ownedTrip, itemId).header("Authorization", bearer(coordinator))).andExpect(status().isOk());
        mvc.perform(post("/api/trips/{id}/items/{itemId}/alternatives/{alternativeId}/apply", ownedTrip, itemId, alternativeId)
                .header("Authorization", bearer(coordinator))).andExpect(status().isOk());
        mvc.perform(post("/api/trips/{id}/cancel", ownedTrip).header("Authorization", bearer(coordinator))).andExpect(status().isOk());
    }

    @Test
    void tripCreationPersistsAndRejectsInvalidDateRange() throws Exception {
        String coordinator = coordinatorToken();
        String traveler = email();
        long tripId = createTrip(coordinator, traveler, "Persisted itinerary");
        org.junit.jupiter.api.Assertions.assertTrue(trips.findById(tripId).isPresent());

        LocalDate start = LocalDate.now().plusDays(5);
        mvc.perform(post("/api/trips").header("Idempotency-Key", UUID.randomUUID().toString()).header("Authorization", bearer(coordinator)).contentType(MediaType.APPLICATION_JSON)
                        .content(tripJson(traveler, "Invalid itinerary", start, start.minusDays(1))))
                .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertEquals(1, trips.count());
    }

    @Test
    void travelerRequestCanBeApprovedCancelledAndAudited() throws Exception {
        String coordinator = coordinatorToken();
        String email = email();
        String traveler = register(email);
        MvcResult request = mvc.perform(post("/api/trips").header("Idempotency-Key", UUID.randomUUID().toString()).header("Authorization", bearer(traveler))
                        .contentType(MediaType.APPLICATION_JSON).content(tripJson(email, "Requested itinerary", null, null)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("REQUESTED")).andReturn();
        long id = body(request).path("id").asLong();
        mvc.perform(post("/api/trips/{id}/approve", id).header("Authorization", bearer(coordinator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ON_TRACK"));
        mvc.perform(post("/api/trips/{id}/cancel", id).header("Authorization", bearer(coordinator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/trips/{id}/history", id).header("Authorization", bearer(coordinator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].type", hasItem("BOOKING_REQUESTED")))
                .andExpect(jsonPath("$[*].type", hasItem("REQUEST_APPROVED")))
                .andExpect(jsonPath("$[*].type", hasItem("TRIP_CANCELLED")));
        org.junit.jupiter.api.Assertions.assertEquals(3, events.findByTripIdOrderByOccurredAtDescIdDesc(id).size());
    }

    @Test
    void invalidTravelerBookingIsRejectedAndCannotBeAssignedToAnotherEmail() throws Exception {
        String traveler = register(email());
        mvc.perform(post("/api/trips").header("Idempotency-Key", UUID.randomUUID().toString()).header("Authorization", bearer(traveler)).contentType(MediaType.APPLICATION_JSON)
                        .content(tripJson(email(), "Impersonated request", null, null)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/trips").header("Idempotency-Key", UUID.randomUUID().toString()).header("Authorization", bearer(traveler)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"traveler\":\"x\",\"items\":[]}"))
                .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertEquals(0, trips.count());
    }

    @Test
    void coordinatorCanSimulateHotelDisruptionAndApplyRecovery() throws Exception {
        String coordinator = coordinatorToken();
        long id = createTrip(coordinator, email(), "Recovery itinerary");
        long itemId = trips.findById(id).orElseThrow().items.getFirst().id;
        MvcResult disrupted = mvc.perform(post("/api/trips/{id}/disruptions", id)
                        .header("Authorization", bearer(coordinator)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "HOTEL_UNAVAILABLE", "itemId", itemId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.affectedItem.status").value("AFFECTED"))
                .andExpect(jsonPath("$.alternatives", hasSize(3))).andReturn();
        long alternativeId = body(disrupted).path("alternatives").get(0).path("id").asLong();
        mvc.perform(post("/api/trips/{id}/items/{itemId}/alternatives/{alternativeId}/apply", id, itemId, alternativeId)
                        .header("Authorization", bearer(coordinator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(2)));
        org.junit.jupiter.api.Assertions.assertTrue(events.findByTripIdOrderByOccurredAtDescIdDesc(id).stream()
                .anyMatch(event -> "ALTERNATIVE_APPLIED".equals(event.type)));
    }

    @Test
    void coordinatorCanRefreshAndApplyMockedDuffelReplacement() throws Exception {
        String coordinator = coordinatorToken();
        LocalDate departure = LocalDate.now().plusDays(3);
        var original = flightOffer("off_original123", departure.atTime(8, 0), departure.atTime(10, 0), "5000.00");
        var replacement = new FlightController.FlightOffer("off_replace123", "DUFFEL_TEST", "Offset Mock Air", "PNQ", "DEL",
                departure + "T12:00:00+05:30", departure + "T14:00:00+05:30", "Asia/Kolkata", "Asia/Kolkata",
                "6500.00", "INR", "2030-01-04T00:00:00Z", 0, 120, "Mock Air", List.of());
        doReturn(original).when(flights).verifiedOffer("off_original123");
        doReturn(replacement).when(flights).verifiedOffer("off_replace123");
        doReturn(new FlightController.SearchResult("TEST", List.of(replacement)))
                .when(flights).searchOffers(eq("PNQ"), eq("DEL"), eq(departure));

        Map<String, Object> flightItem = Map.of("kind", "FLIGHT", "title", "Ignored by verified offer", "location", "PNQ → DEL",
                "startsAt", departure + "T08:00:00", "endsAt", departure + "T10:00:00",
                "flightSource", "DUFFEL_TEST", "flightOfferId", "off_original123");
        String trip = json(Map.of("traveler", "Test Traveler", "travelerEmail", email(), "origin", "Pune", "destination", "Delhi",
                "originAirportCode", "PNQ", "destinationAirportCode", "DEL", "startDate", departure.toString(),
                "endDate", departure.plusDays(2).toString(), "items", List.of(flightItem)));
        long tripId = body(mvc.perform(post("/api/trips").header("Idempotency-Key", UUID.randomUUID().toString()).header("Authorization", bearer(coordinator))
                        .contentType(MediaType.APPLICATION_JSON).content(trip)).andExpect(status().isCreated()).andReturn()).path("id").asLong();
        long itemId = trips.findById(tripId).orElseThrow().items.getFirst().id;
        mvc.perform(post("/api/trips/{id}/disruptions", tripId).header("Authorization", bearer(coordinator))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("type", "FLIGHT_CANCELLATION", "itemId", itemId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alternatives", hasSize(1)));
        mvc.perform(post("/api/trips/{id}/items/{itemId}/alternatives/refresh", tripId, itemId)
                        .header("Authorization", bearer(coordinator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alternatives[0].flightOfferId").value("off_replace123"));
        long alternativeId = alternatives.findByTripIdAndItemId(tripId, itemId).stream().filter(option -> "off_replace123".equals(option.flightOfferId))
                .findFirst().orElseThrow().id;
        mvc.perform(post("/api/trips/{id}/items/{itemId}/alternatives/{alternativeId}/apply", tripId, itemId, alternativeId)
                        .header("Authorization", bearer(coordinator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[1].startsAt").value(departure + "T12:00:00"))
                .andExpect(jsonPath("$.items[1].endsAt").value(departure + "T14:00:00"));
    }

    @Test
    void offlineWeatherFlightRecoveryUpdatesTravelerAndRejectsDuplicateApply() throws Exception {
        String coordinator = coordinatorToken();
        String travelerEmail = email();
        String traveler = register(travelerEmail);
        LocalDate departure = LocalDate.now().plusDays(3);
        List<FlightController.FlightOffer> demo = demoFlights.search("PNQ", "DEL", departure);
        assertEquals(5, demo.size());
        var original = demo.getFirst();
        Map<String, Object> flight = Map.of("kind", "FLIGHT", "title", original.title(), "location", "PNQ → DEL",
                "startsAt", OffsetDateTime.parse(original.departingAt()).toLocalDateTime().toString(),
                "endsAt", OffsetDateTime.parse(original.arrivingAt()).toLocalDateTime().toString(),
                "flightSource", "DEMO", "flightOfferId", original.id());
        String trip = json(Map.of("traveler", "Test Traveler", "travelerEmail", travelerEmail,
                "origin", "Pune", "destination", "Delhi", "originAirportCode", "PNQ", "destinationAirportCode", "DEL",
                "startDate", departure.toString(), "endDate", departure.plusDays(1).toString(), "items", List.of(flight)));
        long tripId = body(mvc.perform(post("/api/trips").header("Idempotency-Key", UUID.randomUUID().toString())
                .header("Authorization", bearer(traveler)).contentType(MediaType.APPLICATION_JSON).content(trip))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.items[0].flightSource").value("DEMO")).andReturn()).path("id").asLong();
        mvc.perform(post("/api/trips/{id}/approve", tripId).header("Authorization", bearer(coordinator)))
                .andExpect(status().isOk());
        long itemId = trips.findById(tripId).orElseThrow().items.getFirst().id;
        mvc.perform(post("/api/trips/{id}/disruptions", tripId).header("Authorization", bearer(traveler))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("type", "SEVERE_WEATHER", "itemId", itemId))))
                .andExpect(status().isForbidden());
        MvcResult weather = mvc.perform(post("/api/trips/{id}/disruptions", tripId)
                .header("Authorization", bearer(coordinator)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("type", "SEVERE_WEATHER", "itemId", itemId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.affectedItem.status").value("AFFECTED"))
                .andExpect(jsonPath("$.alternatives[0].source").value("DEMO")).andReturn();
        long alternativeId = body(weather).path("alternatives").get(0).path("id").asLong();
        assertTrue(body(weather).path("alternatives").get(0).path("startsAt").asText().compareTo(departure + "T10:15:00") >= 0);
        int initialOptions = alternatives.findByTripIdAndItemId(tripId, itemId).size();
        mvc.perform(post("/api/trips/{id}/items/{itemId}/alternatives/refresh", tripId, itemId)
                .header("Authorization", bearer(coordinator))).andExpect(status().isOk());
        assertEquals(initialOptions, alternatives.findByTripIdAndItemId(tripId, itemId).size());
        mvc.perform(post("/api/trips/{id}/items/{itemId}/alternatives/{alternativeId}/apply", tripId, itemId, alternativeId)
                .header("Authorization", bearer(coordinator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[1].flightSource").value("DEMO"));
        mvc.perform(post("/api/trips/{id}/items/{itemId}/alternatives/{alternativeId}/apply", tripId, itemId, alternativeId)
                .header("Authorization", bearer(coordinator))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/notifications").header("Authorization", bearer(traveler)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[*].type", hasItem("DISRUPTION")))
                .andExpect(jsonPath("$.items[*].type", hasItem("ITINERARY_CHANGED")));
    }

    @Test
    void transportAndHotelWeatherDisruptionsProduceSimulatedRecovery() throws Exception {
        String coordinator = coordinatorToken();
        LocalDate departure = LocalDate.now().plusDays(3);
        for (String kind : List.of("TRANSPORT", "HOTEL")) {
            Map<String, Object> segment = Map.of("kind", kind, "title", kind + " planning segment", "location", "Delhi",
                    "startsAt", departure + "T09:00:00", "endsAt", kind.equals("HOTEL")
                            ? departure.plusDays(1) + "T11:00:00" : departure + "T10:00:00");
            String trip = json(Map.of("traveler", "Test Traveler", "travelerEmail", email(), "origin", "Pune",
                    "destination", "Delhi", "startDate", departure.toString(), "endDate", departure.plusDays(2).toString(),
                    "items", List.of(segment)));
            long tripId = body(mvc.perform(post("/api/trips").header("Idempotency-Key", UUID.randomUUID().toString())
                    .header("Authorization", bearer(coordinator)).contentType(MediaType.APPLICATION_JSON).content(trip))
                    .andExpect(status().isCreated()).andReturn()).path("id").asLong();
            long itemId = trips.findById(tripId).orElseThrow().items.getFirst().id;
            String type = kind.equals("HOTEL") ? "HOTEL_UNAVAILABLE" : "TRANSPORT_DISRUPTION";
            MvcResult issue = mvc.perform(post("/api/trips/{id}/disruptions", tripId)
                    .header("Authorization", bearer(coordinator)).contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("type", type, "itemId", itemId))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.alternatives[0].kind").value(kind)).andReturn();
            long choice = body(issue).path("alternatives").get(0).path("id").asLong();
            mvc.perform(post("/api/trips/{id}/items/{itemId}/alternatives/{alternativeId}/apply", tripId, itemId, choice)
                    .header("Authorization", bearer(coordinator))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(2)));
        }
        long weatherTrip = createTrip(coordinator, email(), "Weather hotel");
        long weatherItem = trips.findById(weatherTrip).orElseThrow().items.getFirst().id;
        mvc.perform(post("/api/trips/{id}/disruptions", weatherTrip)
                .header("Authorization", bearer(coordinator)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("type", "SEVERE_WEATHER", "itemId", weatherItem))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alternatives[0].kind").value("HOTEL"));
    }

    @Test
    void lateDemoFlightCanRecoverOnNextDayWithinTripWindow() throws Exception {
        String coordinator = coordinatorToken();
        LocalDate departure = LocalDate.now().plusDays(3);
        var late = demoFlights.search("PNQ", "DEL", departure).getLast();
        Map<String, Object> flight = Map.of("kind", "FLIGHT", "title", late.title(), "location", "PNQ → DEL",
                "startsAt", OffsetDateTime.parse(late.departingAt()).toLocalDateTime().toString(),
                "endsAt", OffsetDateTime.parse(late.arrivingAt()).toLocalDateTime().toString(),
                "flightSource", "DEMO", "flightOfferId", late.id());
        String trip = json(Map.of("traveler", "Test Traveler", "travelerEmail", email(), "origin", "Pune",
                "destination", "Delhi", "originAirportCode", "PNQ", "destinationAirportCode", "DEL",
                "startDate", departure.toString(), "endDate", departure.plusDays(1).toString(), "items", List.of(flight)));
        long tripId = body(mvc.perform(post("/api/trips").header("Idempotency-Key", UUID.randomUUID().toString())
                .header("Authorization", bearer(coordinator)).contentType(MediaType.APPLICATION_JSON).content(trip))
                .andExpect(status().isCreated()).andReturn()).path("id").asLong();
        long itemId = trips.findById(tripId).orElseThrow().items.getFirst().id;
        mvc.perform(post("/api/trips/{id}/disruptions", tripId).header("Authorization", bearer(coordinator))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("type", "SEVERE_WEATHER", "itemId", itemId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alternatives[0].source").value("DEMO"))
                .andExpect(jsonPath("$.alternatives[0].startsAt").value(departure.plusDays(1) + "T06:15:00"));
    }

    @Test
    void offsetBearingDuffelTimestampsPersistAsCorrectAirportLocalTimes() throws Exception {
        String coordinator = coordinatorToken();
        LocalDate departure = LocalDate.now().plusDays(3);
        var offer = new FlightController.FlightOffer("off_offset123", "DUFFEL_TEST", "Offset Air", "PNQ", "LHR",
                departure + "T08:00:00+05:30", departure.atTime(7, 30).atZone(ZoneId.of("Europe/London")).toOffsetDateTime().toString(), "Asia/Kolkata", "Europe/London",
                "5000.00", "INR", "2030-01-04T00:00:00Z", 0, 240, "Offset Air", List.of());
        doReturn(offer).when(flights).verifiedOffer("off_offset123");
        Map<String, Object> flightItem = Map.of("kind", "FLIGHT", "title", "Offset flight", "location", "PNQ → LHR",
                "startsAt", departure + "T08:00:00", "endsAt", departure + "T10:00:00",
                "flightSource", "DUFFEL_TEST", "flightOfferId", "off_offset123");
        String trip = json(Map.of("traveler", "Test Traveler", "travelerEmail", email(), "origin", "Pune", "destination", "London",
                "originAirportCode", "PNQ", "destinationAirportCode", "LHR", "startDate", departure.toString(),
                "endDate", departure.plusDays(2).toString(), "items", List.of(flightItem)));
        MvcResult response = mvc.perform(post("/api/trips").header("Idempotency-Key", UUID.randomUUID().toString()).header("Authorization", bearer(coordinator))
                        .contentType(MediaType.APPLICATION_JSON).content(trip))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.items[0].startsAt").value(departure + "T08:00:00"))
                .andExpect(jsonPath("$.items[0].endsAt").value(departure + "T07:30:00"))
                .andExpect(jsonPath("$.items[0].flightOriginTimeZone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.items[0].flightDestinationTimeZone").value("Europe/London")).andReturn();
        long id = body(response).path("id").asLong();
        var stored = trips.findById(id).orElseThrow().items.getFirst();
        assertEquals(departure.atTime(8, 0), stored.startsAt);
        assertEquals(departure.atTime(7, 30), stored.endsAt);
        assertEquals(OffsetDateTime.parse(offer.departingAt()).toInstant(), stored.startsAt.atZone(ZoneId.of(stored.flightOriginTimeZone)).toInstant());
        assertEquals(OffsetDateTime.parse(offer.arrivingAt()).toInstant(), stored.endsAt.atZone(ZoneId.of(stored.flightDestinationTimeZone)).toInstant());
    }

    @Test
    void mismatchedDuffelOfferIdIsRejectedBeforeSavingTrip() throws Exception {
        String coordinator = coordinatorToken();
        LocalDate departure = LocalDate.now().plusDays(3);
        doReturn(flightOffer("off_other123", departure.atTime(8, 0), departure.atTime(10, 0), "5000.00"))
                .when(flights).verifiedOffer("off_requested123");
        Map<String, Object> flightItem = Map.of("kind", "FLIGHT", "title", "Mock Air", "location", "PNQ → DEL",
                "startsAt", departure + "T08:00:00", "endsAt", departure + "T10:00:00",
                "flightSource", "DUFFEL_TEST", "flightOfferId", "off_requested123");
        String payload = json(Map.of("traveler", "Test Traveler", "travelerEmail", email(), "origin", "Pune", "destination", "Delhi",
                "originAirportCode", "PNQ", "destinationAirportCode", "DEL", "startDate", departure.toString(),
                "endDate", departure.plusDays(2).toString(), "items", List.of(flightItem)));
        mvc.perform(post("/api/trips").header("Idempotency-Key", UUID.randomUUID().toString()).header("Authorization", bearer(coordinator))
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isConflict());
        assertEquals(0, trips.count());
    }

    @Test
    void duplicateApprovalIsRejectedAndCreatesOnlyOneApprovalEvent() throws Exception {
        String coordinator = coordinatorToken();
        String email = email();
        String traveler = register(email);
        MvcResult request = mvc.perform(post("/api/trips").header("Idempotency-Key", UUID.randomUUID().toString()).header("Authorization", bearer(traveler))
                        .contentType(MediaType.APPLICATION_JSON).content(tripJson(email, "Duplicate approval", null, null)))
                .andExpect(status().isCreated()).andReturn();
        long id = body(request).path("id").asLong();
        mvc.perform(post("/api/trips/{id}/approve", id).header("Authorization", bearer(coordinator))).andExpect(status().isOk());
        mvc.perform(post("/api/trips/{id}/approve", id).header("Authorization", bearer(coordinator))).andExpect(status().isBadRequest());
        long approvals = events.findByTripIdOrderByOccurredAtDescIdDesc(id).stream().filter(event -> "REQUEST_APPROVED".equals(event.type)).count();
        org.junit.jupiter.api.Assertions.assertEquals(1, approvals);
    }

    @Test
    void duplicateCancellationIsRejectedAndAuditedOnce() throws Exception {
        String coordinator = coordinatorToken();
        long id = createTrip(coordinator, email(), "Duplicate cancellation");
        mvc.perform(post("/api/trips/{id}/cancel", id).header("Authorization", bearer(coordinator))).andExpect(status().isOk());
        mvc.perform(post("/api/trips/{id}/cancel", id).header("Authorization", bearer(coordinator))).andExpect(status().isBadRequest());
        assertEquals(1, events.findByTripIdOrderByOccurredAtDescIdDesc(id).stream().filter(e -> "TRIP_CANCELLED".equals(e.type)).count());
    }

    @Test
    void guestTripRequiresSingleUseInvitationAndMatchingAccount() throws Exception {
        String coordinator = coordinatorToken();
        String guestEmail = email();
        long id = createTrip(coordinator, guestEmail, "Invited itinerary");
        String guest = register(guestEmail);
        String stranger = register(email());
        mvc.perform(get("/api/trips/{id}", id).header("Authorization", bearer(guest))).andExpect(status().isForbidden());
        MvcResult issued = mvc.perform(post("/api/trips/{id}/invitation", id).header("Authorization", bearer(coordinator)))
                .andExpect(status().isOk()).andReturn();
        String code = body(issued).path("code").asText();
        mvc.perform(post("/api/auth/claim-trip").header("Authorization", bearer(stranger)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("code", code)))).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/claim-trip").header("Authorization", bearer(guest)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("code", code)))).andExpect(status().isOk());
        mvc.perform(get("/api/trips/{id}", id).header("Authorization", bearer(guest))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/claim-trip").header("Authorization", bearer(guest)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("code", code)))).andExpect(status().isConflict());
        assertEquals(1, events.findByTripIdOrderByOccurredAtDescIdDesc(id).stream().filter(e -> "TRIP_CLAIMED".equals(e.type)).count());
    }

    @Test
    void idempotencyKeyDeduplicatesConcurrentTripCreationAndRejectsChangedPayload() throws Exception {
        String coordinator = coordinatorToken();
        String key = UUID.randomUUID().toString();
        String email = email();
        String payload = tripJson(email, "Exactly once", null, null);
        List<Integer> responses = parallelRequests(20, () -> mvc.perform(post("/api/trips").header("Idempotency-Key", key)
                .header("Authorization", bearer(coordinator)).contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn().getResponse().getStatus());
        assertEquals(20, responses.stream().filter(code -> code == 201).count());
        assertEquals(1, trips.count());
        mvc.perform(post("/api/trips").header("Idempotency-Key", key).header("Authorization", bearer(coordinator))
                .contentType(MediaType.APPLICATION_JSON).content(tripJson(email, "Different payload", null, null))).andExpect(status().isConflict());
        assertEquals(1, trips.count());
    }

    @Test
    void rejectionAndNotificationsAreScopedAndAudited() throws Exception {
        String coordinator = coordinatorToken();
        String owner = register(email());
        String stranger = register(email());
        String ownerEmail = body(mvc.perform(get("/api/auth/me").header("Authorization", bearer(owner))).andExpect(status().isOk()).andReturn()).path("email").asText();
        long id = body(mvc.perform(post("/api/trips").header("Idempotency-Key", UUID.randomUUID().toString()).header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content(tripJson(ownerEmail, "Review request", null, null))).andExpect(status().isCreated()).andReturn()).path("id").asLong();
        mvc.perform(post("/api/trips/{id}/reject", id).header("Authorization", bearer(stranger)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("comment", "No")))).andExpect(status().isForbidden());
        mvc.perform(post("/api/trips/{id}/reject", id).header("Authorization", bearer(coordinator)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("comment", "Travel window changed")))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(post("/api/trips/{id}/reject", id).header("Authorization", bearer(coordinator)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("comment", "Again")))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/notifications").header("Authorization", bearer(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1)).andExpect(jsonPath("$.items[0].type").value("REJECTED"));
        mvc.perform(get("/api/notifications").header("Authorization", bearer(stranger))).andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
        var event = events.findByTripIdOrderByOccurredAtDescIdDesc(id).getFirst();
        assertEquals("REQUEST_REJECTED", event.type);
        assertEquals("COORDINATOR", event.actorRole);
        assertTrue(event.changeJson.contains("REJECTED"));
    }

    @Test
    void budgetTotalsRemainSeparatedByCurrencyAndValidateNegativeAmounts() throws Exception {
        String coordinator = coordinatorToken();
        LocalDate start = LocalDate.now().plusDays(3);
        Map<String, Object> base = new java.util.LinkedHashMap<>();
        base.put("traveler", "Budget Traveler"); base.put("travelerEmail", email());
        base.put("origin", "Pune"); base.put("destination", "Delhi");
        base.put("startDate", start.toString()); base.put("endDate", start.plusDays(2).toString());
        base.put("estimatedBudget", 1000); base.put("budgetCurrency", "INR");
        base.put("items", List.of(
                Map.of("kind", "HOTEL", "title", "INR stay", "location", "Delhi", "startsAt", start + "T15:00:00", "endsAt", start.plusDays(1) + "T11:00:00", "estimatedCost", 1200, "costCurrency", "INR"),
                Map.of("kind", "TRANSPORT", "title", "USD transfer", "location", "Delhi", "startsAt", start.plusDays(1) + "T12:00:00", "endsAt", start.plusDays(1) + "T13:00:00", "estimatedCost", 50, "costCurrency", "USD")));
        mvc.perform(post("/api/trips").header("Idempotency-Key", UUID.randomUUID().toString()).header("Authorization", bearer(coordinator))
                .contentType(MediaType.APPLICATION_JSON).content(json(base))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.costTotals.INR").value(1200)).andExpect(jsonPath("$.costTotals.USD").value(50))
                .andExpect(jsonPath("$.budgetExceeded").value(true));
        base.put("estimatedBudget", -1);
        mvc.perform(post("/api/trips").header("Idempotency-Key", UUID.randomUUID().toString()).header("Authorization", bearer(coordinator))
                .contentType(MediaType.APPLICATION_JSON).content(json(base))).andExpect(status().isBadRequest());
    }

    @Test
    void notificationReadAccessIsLimitedToItsOwner() throws Exception {
        String coordinator = coordinatorToken();
        String ownerEmail = email(); String owner = register(ownerEmail); String stranger = register(email());
        long id = createTrip(coordinator, ownerEmail, "Notify traveler");
        mvc.perform(post("/api/trips/{id}/cancel", id).header("Authorization", bearer(coordinator))).andExpect(status().isOk());
        long noticeId = body(mvc.perform(get("/api/notifications").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(1)).andReturn()).path("items").get(0).path("id").asLong();
        mvc.perform(post("/api/notifications/{id}/read", noticeId).header("Authorization", bearer(stranger))).andExpect(status().isNotFound());
        mvc.perform(post("/api/notifications/{id}/read", noticeId).header("Authorization", bearer(owner))).andExpect(status().isOk());
        mvc.perform(get("/api/notifications").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void helpMailFailureKeepsCheckInAndSchedulesOutboxRetry() throws Exception {
        String coordinator = coordinatorToken();
        long id = createTrip(coordinator, email(), "Help retry");
        mvc.perform(post("/api/trips/{id}/check-in", id).header("Authorization", bearer(coordinator))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "NEEDS_HELP", "note", "Test only"))))
                .andExpect(status().isOk());
        doThrow(new IllegalStateException("mock SMTP outage")).when(mailSender).send(any(SimpleMailMessage.class));
        emailAlerts.deliverDue();
        assertEquals("NEEDS_HELP", trips.findById(id).orElseThrow().checkInStatus);
        var job = outbox.findAll().getFirst();
        assertEquals("PENDING", job.status);
        assertEquals(1, job.attempts);
        assertTrue(job.nextAttemptAt.isAfter(Instant.now()));
    }

    @Test
    void expiredDuffelAlternativeCannotBeApplied() throws Exception {
        String coordinator = coordinatorToken();
        LocalDate departure = LocalDate.now().plusDays(3);
        var original = flightOffer("off_expireorig", departure.atTime(8, 0), departure.atTime(10, 0), "5000.00");
        var expired = new FlightController.FlightOffer("off_expirealt", "DUFFEL_TEST", "Expired Air", "PNQ", "DEL",
                departure + "T12:00:00+05:30", departure + "T14:00:00+05:30", "Asia/Kolkata", "Asia/Kolkata",
                "6000.00", "INR", "2020-01-01T00:00:00Z", 0, 120, "Expired Air", List.of());
        doReturn(original).when(flights).verifiedOffer("off_expireorig");
        doReturn(expired).when(flights).verifiedOffer("off_expirealt");
        doReturn(new FlightController.SearchResult("TEST", List.of(expired))).when(flights).searchOffers(eq("PNQ"), eq("DEL"), eq(departure));
        long tripId = createDuffelTrip(coordinator, departure, "off_expireorig");
        long itemId = trips.findById(tripId).orElseThrow().items.getFirst().id;
        mvc.perform(post("/api/trips/{id}/disruptions", tripId).header("Authorization", bearer(coordinator)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("type", "FLIGHT_CANCELLATION", "itemId", itemId)))).andExpect(status().isOk());
        mvc.perform(post("/api/trips/{id}/items/{itemId}/alternatives/refresh", tripId, itemId).header("Authorization", bearer(coordinator))).andExpect(status().isOk());
        Alternative option = alternatives.findByTripIdAndItemId(tripId, itemId).stream().filter(a -> "off_expirealt".equals(a.flightOfferId)).findFirst().orElseThrow();
        mvc.perform(post("/api/trips/{id}/items/{itemId}/alternatives/{alternativeId}/apply", tripId, itemId, option.id)
                        .header("Authorization", bearer(coordinator)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Flight offer expired. Refresh replacement flights."));
        assertEquals(1, trips.findById(tripId).orElseThrow().items.size());
    }

    @Test
    void invalidDisruptionAndForeignRecoveryAlternativeAreRejected() throws Exception {
        String coordinator = coordinatorToken();
        long firstTrip = createTrip(coordinator, email(), "First recovery");
        long secondTrip = createTrip(coordinator, email(), "Second recovery");
        long firstItem = trips.findById(firstTrip).orElseThrow().items.getFirst().id;
        long secondItem = trips.findById(secondTrip).orElseThrow().items.getFirst().id;
        mvc.perform(post("/api/trips/{id}/disruptions", firstTrip).header("Authorization", bearer(coordinator)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("type", "FLIGHT_CANCELLATION", "itemId", firstItem)))).andExpect(status().isBadRequest());
        MvcResult sourceDisruption = mvc.perform(post("/api/trips/{id}/disruptions", firstTrip).header("Authorization", bearer(coordinator)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("type", "HOTEL_UNAVAILABLE", "itemId", firstItem)))).andExpect(status().isOk()).andReturn();
        long alternativeId = body(sourceDisruption).path("alternatives").get(0).path("id").asLong();
        mvc.perform(post("/api/trips/{id}/items/{itemId}/alternatives/{alternativeId}/apply", secondTrip, secondItem, alternativeId)
                .header("Authorization", bearer(coordinator))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/trips/{id}/disruptions", firstTrip).header("Authorization", bearer(coordinator)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("type", "NOT_A_DISRUPTION", "itemId", firstItem)))).andExpect(status().isBadRequest());
    }

    @Test
    void twentyConcurrentApprovalsProduceOneTransitionAndOneEvent() throws Exception {
        String coordinator = coordinatorToken();
        String email = email();
        String traveler = register(email);
        long id = body(mvc.perform(post("/api/trips").header("Idempotency-Key", UUID.randomUUID().toString()).header("Authorization", bearer(traveler)).contentType(MediaType.APPLICATION_JSON)
                        .content(tripJson(email, "Concurrent approval", null, null))).andExpect(status().isCreated()).andReturn()).path("id").asLong();
        List<Integer> responses = parallelRequests(20, () -> mvc.perform(post("/api/trips/{id}/approve", id)
                .header("Authorization", bearer(coordinator))).andReturn().getResponse().getStatus());
        assertEquals(1, responses.stream().filter(code -> code == 200).count());
        assertEquals(19, responses.stream().filter(code -> code == 400).count());
        assertEquals(1, events.findByTripIdOrderByOccurredAtDescIdDesc(id).stream().filter(e -> "REQUEST_APPROVED".equals(e.type)).count());
    }

    @Test
    void twentyConcurrentRecoveryAppliesCreateOneReplacement() throws Exception {
        String coordinator = coordinatorToken();
        long id = createTrip(coordinator, email(), "Concurrent recovery");
        long itemId = trips.findById(id).orElseThrow().items.getFirst().id;
        MvcResult result = mvc.perform(post("/api/trips/{id}/disruptions", id).header("Authorization", bearer(coordinator)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("type", "HOTEL_UNAVAILABLE", "itemId", itemId)))).andExpect(status().isOk()).andReturn();
        long alternativeId = body(result).path("alternatives").get(0).path("id").asLong();
        List<Integer> responses = parallelRequests(20, () -> mvc.perform(post("/api/trips/{id}/items/{itemId}/alternatives/{alternativeId}/apply", id, itemId, alternativeId)
                .header("Authorization", bearer(coordinator))).andReturn().getResponse().getStatus());
        assertEquals(1, responses.stream().filter(code -> code == 200).count());
        assertEquals(19, responses.stream().filter(code -> code == 400).count());
        assertEquals(2, trips.findById(id).orElseThrow().items.size());
        assertEquals(1, events.findByTripIdOrderByOccurredAtDescIdDesc(id).stream().filter(e -> "ALTERNATIVE_APPLIED".equals(e.type)).count());
    }

    @Test
    void flightSearchUsesDuffelStubAndHelpEmailUsesSmtpMock() throws Exception {
        String coordinator = coordinatorToken();
        var offer = new FlightController.FlightOffer("off_mock123", "DUFFEL_TEST", "Mock Air 123", "PNQ", "DEL",
                "2030-01-05T08:00:00Z", "2030-01-05T10:00:00Z", "Asia/Kolkata", "Asia/Kolkata",
                "5000.00", "INR", "2030-01-04T00:00:00Z", 0, 120, "Mock Air", List.of());
        doReturn(new FlightController.SearchResult("TEST", List.of(offer)))
                .when(flights).searchOffers(eq("PNQ"), eq("DEL"), any(LocalDate.class));
        mvc.perform(post("/api/flights/search").header("Authorization", bearer(coordinator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("origin", "PNQ", "destination", "DEL", "departureDate", "2030-01-05"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.offers[0].id").value("off_mock123"));
        verify(flights).searchOffers(eq("PNQ"), eq("DEL"), any(LocalDate.class));

        long tripId = createTrip(coordinator, email(), "Help alert itinerary");
        mvc.perform(post("/api/trips/{id}/check-in", tripId).header("Authorization", bearer(coordinator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "NEEDS_HELP", "note", "Mock alert only"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.checkInStatus").value("NEEDS_HELP"));
        assertEquals(1, outbox.count());
        emailAlerts.deliverDue();
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        org.junit.jupiter.api.Assertions.assertTrue(message.getValue().getText().contains("Mock alert only"));
    }

    private long createTrip(String coordinator, String travelerEmail, String title) throws Exception {
        var payload = (tools.jackson.databind.node.ObjectNode) mapper.readTree(tripJson(travelerEmail, title, null, null));
        users.findByEmailIgnoreCase(travelerEmail).ifPresent(user -> payload.put("travelerUserId", user.id));
        MvcResult result = mvc.perform(post("/api/trips").header("Idempotency-Key", UUID.randomUUID().toString()).header("Authorization", bearer(coordinator))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(payload)))
                .andExpect(status().isCreated()).andReturn();
        return body(result).path("id").asLong();
    }

    private long createDuffelTrip(String coordinator, LocalDate departure, String offerId) throws Exception {
        Map<String, Object> flightItem = Map.of("kind", "FLIGHT", "title", "Mock Air", "location", "PNQ → DEL",
                "startsAt", departure + "T08:00:00", "endsAt", departure + "T10:00:00",
                "flightSource", "DUFFEL_TEST", "flightOfferId", offerId);
        Map<String, Object> trip = Map.of("traveler", "Test Traveler", "travelerEmail", email(), "origin", "Pune", "destination", "Delhi",
                "originAirportCode", "PNQ", "destinationAirportCode", "DEL", "startDate", departure.toString(),
                "endDate", departure.plusDays(2).toString(), "items", List.of(flightItem));
        return body(mvc.perform(post("/api/trips").header("Idempotency-Key", UUID.randomUUID().toString()).header("Authorization", bearer(coordinator)).contentType(MediaType.APPLICATION_JSON)
                .content(json(trip))).andExpect(status().isCreated()).andReturn()).path("id").asLong();
    }

    private List<Integer> parallelRequests(int count, Callable<Integer> request) throws Exception {
        var executor = Executors.newFixedThreadPool(count);
        var ready = new CountDownLatch(count);
        var start = new CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<Integer>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) futures.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting to start concurrent requests");
                return request.call();
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "Concurrent requests did not get ready");
            start.countDown();
            List<Integer> statuses = new java.util.ArrayList<>();
            for (var future : futures) statuses.add(future.get(30, TimeUnit.SECONDS));
            return statuses;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private String register(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Test Traveler", "email", email, "password", "correct-horse-42"))))
                .andExpect(status().isOk()).andReturn();
        return body(result).path("token").asText();
    }

    private String tokenFor(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "correct-horse-42"))))
                .andExpect(status().isOk()).andReturn();
        return body(result).path("token").asText();
    }

    private String coordinatorToken() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "coordinator@tripshield.test", "password", "integration-test-coordinator-password"))))
                .andExpect(status().isOk()).andReturn();
        return body(result).path("token").asText();
    }

    private String tripJson(String email, String title, LocalDate forcedStart, LocalDate forcedEnd) throws Exception {
        LocalDate start = forcedStart == null ? LocalDate.now().plusDays(3) : forcedStart;
        LocalDate end = forcedEnd == null ? start.plusDays(2) : forcedEnd;
        return json(Map.of("traveler", "Test Traveler", "travelerEmail", email, "origin", "Pune", "destination", "Delhi",
                "startDate", start.toString(), "endDate", end.toString(),
                "items", List.of(Map.of("kind", "HOTEL", "title", title, "location", "Delhi",
                        "startsAt", start + "T15:00:00", "endsAt", start.plusDays(1) + "T11:00:00"))));
    }

    private String editTripJson(long tripId, String email, String title) throws Exception {
        var trip = trips.findById(tripId).orElseThrow();
        var item = trip.items.getFirst();
        Map<String, Object> editItem = Map.of("id", item.id, "kind", item.kind, "title", title, "location", item.location,
                "startsAt", item.startsAt.toString(), "endsAt", item.endsAt.toString());
        return json(Map.of("traveler", "Test Traveler", "travelerEmail", email, "origin", trip.origin, "destination", trip.destination,
                "startDate", trip.startDate.toString(), "endDate", trip.endDate.toString(), "items", List.of(editItem)));
    }

    private FlightController.FlightOffer flightOffer(String id, LocalDateTime departure, LocalDateTime arrival, String amount) {
        return new FlightController.FlightOffer(id, "DUFFEL_TEST", "Mock Air " + id, "PNQ", "DEL",
                departure.toString(), arrival.toString(), "Asia/Kolkata", "Asia/Kolkata", amount, "INR",
                "2030-01-04T00:00:00Z", 0, 120, "Mock Air", List.of());
    }

    private String email() { return "traveler-" + UUID.randomUUID() + "@example.test"; }
    private String bearer(String token) { return "Bearer " + token; }
    private String json(Object value) throws Exception { return mapper.writeValueAsString(value); }
    private JsonNode body(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()); }
}
