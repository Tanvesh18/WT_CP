package com.tripshield.backend.controller;

import com.tripshield.backend.model.Alternative;
import com.tripshield.backend.model.Trip;
import com.tripshield.backend.model.TripEvent;
import com.tripshield.backend.model.TripItem;
import com.tripshield.backend.repository.AlternativeRepository;
import com.tripshield.backend.repository.TripEventRepository;
import com.tripshield.backend.repository.TripRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/trips")
@Transactional
public class TripController {
    private final TripRepository trips;
    private final AlternativeRepository alternatives;
    private final TripEventRepository events;

    public TripController(TripRepository trips, AlternativeRepository alternatives, TripEventRepository events) {
        this.trips = trips; this.alternatives = alternatives; this.events = events;
    }

    public record ItemInput(@NotBlank String kind, @NotBlank String title, @NotBlank String location,
                            @NotNull LocalDateTime startsAt, @NotNull LocalDateTime endsAt) {}
    public record TripInput(@NotBlank String traveler, @NotBlank String origin, @NotBlank String destination,
                            @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                            @NotEmpty List<@Valid ItemInput> items) {}
    public record ItemView(Long id, String kind, String title, String location, LocalDateTime startsAt,
                           LocalDateTime endsAt, String status, String changeNote) {}
    public record TripView(Long id, String traveler, String origin, String destination, LocalDate startDate,
                           LocalDate endDate, String status, String riskLevel, String riskReason,
                           String checkInStatus, LocalDateTime checkedInAt, String checkInNote,
                           List<ItemView> items) {}
    public record DisruptionInput(@NotBlank String type, @NotNull Long itemId) {}
    public record CheckInInput(@NotBlank String status, String note) {}
    public record ImpactView(String message, ItemView affectedItem, List<Alternative> alternatives) {}

    @GetMapping
    public List<TripView> list() { return trips.findAll().stream().map(this::view).toList(); }

    @GetMapping("/{id}")
    public TripView get(@PathVariable Long id) { return view(find(id)); }

    @GetMapping("/{id}/history")
    public List<TripEvent> history(@PathVariable Long id) {
        find(id);
        return events.findByTripIdOrderByOccurredAtDescIdDesc(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripView create(@Valid @RequestBody TripInput input) {
        if (input.endDate().isBefore(input.startDate())) throw badRequest("End date must be after start date");
        Trip trip = new Trip();
        trip.traveler = input.traveler().trim(); trip.origin = input.origin().trim();
        trip.destination = input.destination().trim(); trip.startDate = input.startDate(); trip.endDate = input.endDate();
        for (ItemInput value : input.items()) {
            if (!value.endsAt().isAfter(value.startsAt())) throw badRequest("An itinerary item must end after it starts");
            if (value.startsAt().toLocalDate().isBefore(trip.startDate) || value.endsAt().toLocalDate().isAfter(trip.endDate))
                throw badRequest("Itinerary items must be within trip dates");
            String kind = value.kind().toUpperCase();
            if (!List.of("FLIGHT", "HOTEL", "TRANSPORT").contains(kind)) throw badRequest("Invalid item kind");
            TripItem item = new TripItem(); item.trip = trip; item.kind = kind;
            item.title = value.title().trim(); item.location = value.location().trim();
            item.startsAt = value.startsAt(); item.endsAt = value.endsAt(); trip.items.add(item);
        }
        Trip saved = trips.saveAndFlush(trip);
        events.save(new TripEvent(saved.id, null, "TRIP_CREATED", "Trip created for " + saved.traveler));
        return view(saved);
    }

    @PostMapping("/{id}/check-in")
    public TripView checkIn(@PathVariable Long id, @Valid @RequestBody CheckInInput input) {
        Trip trip = find(id);
        String status = input.status().toUpperCase();
        if (!List.of("SAFE", "NEEDS_HELP").contains(status)) throw badRequest("Status must be SAFE or NEEDS_HELP");
        trip.checkInStatus = status; trip.checkedInAt = LocalDateTime.now();
        trip.checkInNote = input.note() == null ? null : input.note().trim();
        if (trip.checkInNote != null && trip.checkInNote.length() > 500) throw badRequest("Note is too long");
        events.save(new TripEvent(trip.id, null, "CHECK_IN", status + (trip.checkInNote == null || trip.checkInNote.isBlank() ? "" : ": " + trip.checkInNote)));
        return view(trips.save(trip));
    }

    @PostMapping("/{id}/disruptions")
    public ImpactView disrupt(@PathVariable Long id, @Valid @RequestBody DisruptionInput input) {
        Trip trip = find(id);
        TripItem item = item(trip, input.itemId());
        String type = input.type().toUpperCase();
        String expected = switch (type) {
            case "FLIGHT_CANCELLATION" -> "FLIGHT";
            case "HOTEL_UNAVAILABLE" -> "HOTEL";
            case "TRANSPORT_DISRUPTION" -> "TRANSPORT";
            case "SEVERE_WEATHER" -> item.kind;
            default -> throw badRequest("Invalid disruption type");
        };
        if (!expected.equals(item.kind)) throw badRequest("Disruption does not affect this item");
        if (!"CONFIRMED".equals(item.status)) throw badRequest("Only confirmed items can be disrupted");
        item.status = "AFFECTED"; item.changeNote = type.replace('_', ' ');
        trips.save(trip);
        events.save(new TripEvent(trip.id, item.id, "DISRUPTION", item.changeNote + " affected " + item.title));
        return impact(trip, item);
    }

    @GetMapping("/{id}/items/{itemId}/alternatives")
    public ImpactView options(@PathVariable Long id, @PathVariable Long itemId) {
        Trip trip = find(id); TripItem item = item(trip, itemId);
        if (!"AFFECTED".equals(item.status)) throw badRequest("Simulate a disruption first");
        return impact(trip, item);
    }

    @PostMapping("/{id}/items/{itemId}/alternatives/{alternativeId}/apply")
    public TripView apply(@PathVariable Long id, @PathVariable Long itemId, @PathVariable Long alternativeId) {
        Trip trip = find(id); TripItem affected = item(trip, itemId);
        if (!"AFFECTED".equals(affected.status)) throw badRequest("Item is not affected");
        Alternative option = alternatives.findById(alternativeId).orElseThrow(() -> notFound("Alternative not found"));
        if (!trip.id.equals(option.tripId) || !affected.id.equals(option.itemId)) throw badRequest("Alternative does not belong to this item");
        if (!fits(trip, affected, option)) throw badRequest("Alternative no longer fits this itinerary");
        TripItem replacement = new TripItem(); replacement.trip = trip; replacement.kind = affected.kind;
        replacement.title = option.title; replacement.location = option.location;
        replacement.startsAt = option.startsAt; replacement.endsAt = option.endsAt;
        replacement.changeNote = "Replaced " + affected.title + " after " + affected.changeNote;
        affected.status = "REPLACED";
        trip.items.add(replacement);
        Trip saved = trips.saveAndFlush(trip);
        events.save(new TripEvent(saved.id, affected.id, "ALTERNATIVE_APPLIED", option.title + " replaced " + affected.title));
        return view(saved);
    }

    private ImpactView impact(Trip trip, TripItem item) {
        List<Alternative> options = alternatives.findByTripIdAndItemId(trip.id, item.id);
        if (options.isEmpty()) options = alternatives.saveAll(generate(trip, item));
        List<Alternative> valid = options.stream().filter(option -> fits(trip, item, option))
                .sorted(Comparator.comparing((Alternative option) -> option.startsAt).thenComparing(option -> option.estimatedCost)).toList();
        return new ImpactView(item.changeNote + " affects " + item.title, itemView(item), valid);
    }

    private List<Alternative> generate(Trip trip, TripItem item) {
        String place = item.kind.equals("FLIGHT") ? trip.origin + " → " + trip.destination : item.location;
        String label = switch (item.kind) {
            case "FLIGHT" -> "flight";
            case "HOTEL" -> "hotel";
            default -> "transfer";
        };
        int firstDelay = item.kind.equals("HOTEL") ? 0 : 60;
        int secondDelay = item.kind.equals("HOTEL") ? 0 : 180;
        Alternative first = new Alternative(trip.id, item.id, item.kind, "Alternative " + label + " A", place,
                        item.startsAt.plusMinutes(firstDelay), item.endsAt.plusMinutes(firstDelay),
                        item.kind.equals("HOTEL") ? "6200" : "8500", "Sample replacement for " + place);
        Alternative second = new Alternative(trip.id, item.id, item.kind, "Alternative " + label + " B", place,
                        item.startsAt.plusMinutes(secondDelay), item.endsAt.plusMinutes(secondDelay),
                        item.kind.equals("HOTEL") ? "4800" : "11200", "Second sample option for " + place);
        first.delayMinutes = (int) Duration.between(item.startsAt, first.startsAt).toMinutes();
        second.delayMinutes = (int) Duration.between(item.startsAt, second.startsAt).toMinutes();
        return List.of(first, second);
    }

    private boolean fits(Trip trip, TripItem affected, Alternative option) {
        if (!affected.kind.equals(option.kind) || option.startsAt == null || option.endsAt == null) return false;
        if (!option.endsAt.isAfter(option.startsAt) || option.startsAt.toLocalDate().isBefore(trip.startDate)
                || option.endsAt.toLocalDate().isAfter(trip.endDate)) return false;
        return trip.items.stream().filter(other -> !other.id.equals(affected.id) && "CONFIRMED".equals(other.status)
                        && other.kind.equals(affected.kind))
                .noneMatch(other -> option.startsAt.isBefore(other.endsAt) && other.startsAt.isBefore(option.endsAt));
    }

    private Trip find(Long id) { return trips.findById(id).orElseThrow(() -> notFound("Trip not found")); }
    private TripItem item(Trip trip, Long id) {
        return trip.items.stream().filter(value -> value.id.equals(id)).findFirst()
                .orElseThrow(() -> notFound("Itinerary item not found"));
    }
    private ItemView itemView(TripItem item) {
        return new ItemView(item.id, item.kind, item.title, item.location, item.startsAt,
                item.endsAt, item.status, item.changeNote);
    }
    private TripView view(Trip trip) {
        List<ItemView> items = trip.items.stream().sorted(Comparator.comparing(i -> i.startsAt)).map(this::itemView).toList();
        long affected = items.stream().filter(i -> "AFFECTED".equals(i.status())).count();
        boolean needsHelp = "NEEDS_HELP".equals(trip.checkInStatus);
        String status = affected > 0 || needsHelp ? "NEEDS_ATTENTION" : "ON_TRACK";
        String risk = needsHelp || affected > 1 ? "HIGH" : affected == 1 ? "MEDIUM" : "LOW";
        String reason = needsHelp ? "Traveler requested help" : affected > 1 ? "Multiple unresolved disruptions"
                : affected == 1 ? "One unresolved disruption" : "No active disruption or help request";
        return new TripView(trip.id, trip.traveler, trip.origin, trip.destination, trip.startDate,
                trip.endDate, status, risk, reason, trip.checkInStatus == null ? "PENDING" : trip.checkInStatus,
                trip.checkedInAt, trip.checkInNote, items);
    }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
