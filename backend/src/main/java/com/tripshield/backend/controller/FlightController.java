package com.tripshield.backend.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tripshield.backend.security.Access;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/flights")
public class FlightController {
    private final Access access;
    private final ObjectMapper mapper;
    private final String token;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    public FlightController(Access access, ObjectMapper mapper, @Value("${tripshield.duffel.token:}") String token) {
        this.access = access; this.mapper = mapper; this.token = token.trim();
    }
    public record SearchInput(@NotNull @Pattern(regexp = "[A-Z]{3}") String origin,
            @NotNull @Pattern(regexp = "[A-Z]{3}") String destination,
            @NotNull @FutureOrPresent LocalDate departureDate) {}
    public record AirportStop(String code, String name, Double latitude, Double longitude) {}
    public record FlightLeg(String flightNumber, String airline, String origin, String destination,
            String departingAt, String arrivingAt, String originTimeZone, String destinationTimeZone,
            String originName, String destinationName, Double originLatitude, Double originLongitude, Double destinationLatitude, Double destinationLongitude,
            List<AirportStop> technicalStops) {}
    public record FlightOffer(String id, String source, String title, String origin, String destination,
            String departingAt, String arrivingAt, String originTimeZone, String destinationTimeZone,
            String totalAmount, String totalCurrency, String expiresAt, int stops, int durationMinutes,
            String operatingCarriers, List<FlightLeg> legs) {}
    public record SearchResult(String mode, List<FlightOffer> offers) {}

    @PostMapping("/search")
    public SearchResult search(@Valid @RequestBody SearchInput input) {
        access.current();
        return searchOffers(input.origin(), input.destination(), input.departureDate());
    }

    public SearchResult searchOffers(String origin, String destination, LocalDate departureDate) {
        if (origin.equals(destination)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose different airports");
        requireToken();
        try {
            byte[] body = mapper.writeValueAsBytes(Map.of("data", Map.of(
                "slices", List.of(Map.of("origin", origin, "destination", destination, "departure_date", departureDate.toString())),
                "passengers", List.of(Map.of("type", "adult")), "cabin_class", "economy")));
            JsonNode offers = request("/air/offer_requests?return_offers=true&supplier_timeout=10000", "POST", body).path("data").path("offers");
            List<FlightOffer> result = new ArrayList<>();
            if (offers.isArray()) for (JsonNode offer : offers) {
                FlightOffer parsed = parseOffer(offer);
                if (parsed != null && origin.equals(parsed.origin()) && destination.equals(parsed.destination()) && !expired(parsed.expiresAt())) result.add(parsed);
                if (result.size() == 30) break;
            }
            return new SearchResult(token.startsWith("duffel_test_") ? "TEST" : "LIVE", result);
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Duffel search is unavailable. Try again later."); }
    }

    public FlightOffer verifiedOffer(String id) {
        requireToken();
        if (id == null || !id.matches("off_[A-Za-z0-9]+")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Duffel offer ID");
        try {
            FlightOffer offer = parseOffer(request("/air/offers/" + id, "GET", null).path("data"));
            if (offer == null || expired(offer.expiresAt())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Flight offer expired. Search again.");
            return offer;
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not verify the Duffel offer. Try again."); }
    }

    private JsonNode request(String path, String method, byte[] body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://api.duffel.com" + path)).timeout(Duration.ofSeconds(35))
                .header("Authorization", "Bearer " + token).header("Duffel-Version", "v2")
                .header("Accept", "application/json");
        HttpRequest call = "POST".equals(method) ? builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build() : builder.GET().build();
        HttpResponse<String> response = client.send(call, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401 || response.statusCode() == 403) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Duffel rejected the configured token");
        if (response.statusCode() == 429) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Duffel rate limit reached. Try again shortly.");
        if (response.statusCode() == 404 || response.statusCode() == 410) throw new ResponseStatusException(HttpStatus.CONFLICT, "Flight offer unavailable. Search again.");
        if (response.statusCode() >= 400) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Duffel could not process this route or offer.");
        return mapper.readTree(response.body());
    }
    private FlightOffer parseOffer(JsonNode offer) {
        JsonNode slice = offer.path("slices").path(0);
        JsonNode segments = slice.path("segments");
        if (!segments.isArray() || segments.isEmpty()) return null;
        JsonNode first = segments.get(0), last = segments.get(segments.size() - 1);
        List<FlightLeg> legs = new ArrayList<>();
        int technicalStops = 0;
        List<String> carriers = new ArrayList<>();
        for (JsonNode segment : segments) {
            if (segment.path("stops").isArray()) technicalStops += segment.path("stops").size();
            String airline = segment.path("operating_carrier").path("name").asText("");
            if (!airline.isBlank() && !carriers.contains(airline)) carriers.add(airline);
            List<AirportStop> technical = new ArrayList<>();
            if (segment.path("stops").isArray()) for (JsonNode stop : segment.path("stops")) {
                JsonNode airport = stop.path("airport");
                technical.add(new AirportStop(airport.path("iata_code").asText(""), airport.path("name").asText(""),
                        coordinate(airport.path("latitude")), coordinate(airport.path("longitude"))));
            }
            String number = segment.path("marketing_carrier").path("iata_code").asText("") + segment.path("marketing_carrier_flight_number").asText("");
            legs.add(new FlightLeg(number, airline,
                    segment.path("origin").path("iata_code").asText(""), segment.path("destination").path("iata_code").asText(""),
                    segment.path("departing_at").asText(""), segment.path("arriving_at").asText(""),
                    segment.path("origin").path("time_zone").asText(""), segment.path("destination").path("time_zone").asText(""),
                    segment.path("origin").path("name").asText(""), segment.path("destination").path("name").asText(""),
                    coordinate(segment.path("origin").path("latitude")), coordinate(segment.path("origin").path("longitude")),
                    coordinate(segment.path("destination").path("latitude")), coordinate(segment.path("destination").path("longitude")), technical));
        }
        String source = offer.path("live_mode").asBoolean(false) ? "DUFFEL_LIVE" : "DUFFEL_TEST";
        String title = legs.stream().map(FlightLeg::flightNumber).filter(number -> !number.isBlank()).distinct().reduce((a,b) -> a + " + " + b).orElse(String.join(", ", carriers));
        int minutes = 0;
        try { minutes = Math.toIntExact(Duration.parse(slice.path("duration").asText()).toMinutes()); } catch (Exception ignored) { }
        return new FlightOffer(offer.path("id").asText(""), source, title,
                legs.get(0).origin(), legs.get(legs.size()-1).destination(), legs.get(0).departingAt(), legs.get(legs.size()-1).arrivingAt(),
                legs.get(0).originTimeZone(), legs.get(legs.size()-1).destinationTimeZone(),
                offer.path("total_amount").asText(""), offer.path("total_currency").asText(""), offer.path("expires_at").asText(""),
                segments.size()-1+technicalStops, minutes, String.join(", ", carriers), legs);
    }
    public String routeJson(FlightOffer offer) { return mapper.writeValueAsString(offer.legs()); }
    private Double coordinate(JsonNode value) { return value.isNumber() ? value.asDouble() : null; }
    private boolean expired(String value) {
        try { return !Instant.parse(value).isAfter(Instant.now()); } catch (Exception ex) { return true; }
    }
    private void requireToken() {
        if (token.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Duffel is not configured. Add tripshield.duffel.token to backend/config/local.properties and restart the backend.");
    }
}
