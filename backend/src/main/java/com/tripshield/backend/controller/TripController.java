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
import java.util.Comparator;
import java.util.List;
import com.tripshield.backend.service.EmailAlerts;
import com.tripshield.backend.service.DisruptionPlanner;
import com.tripshield.backend.service.DisruptionPlanner.SegmentImpact;
import com.tripshield.backend.service.DisruptionPlanner.OptionView;
import java.util.Set;
import java.util.stream.Collectors;
import com.tripshield.backend.security.Access;

@RestController
@RequestMapping("/api/trips")
@Transactional
public class TripController {
    private final TripRepository trips;
    private final AlternativeRepository alternatives;
    private final TripEventRepository events;
    private final EmailAlerts emailAlerts;
    private final Access access;
    private final DisruptionPlanner planner;

    public TripController(TripRepository trips, AlternativeRepository alternatives, TripEventRepository events, EmailAlerts emailAlerts, Access access, DisruptionPlanner planner) {
        this.trips = trips; this.alternatives = alternatives; this.events = events; this.emailAlerts = emailAlerts; this.access = access; this.planner = planner;
    }

    public record ItemInput(@NotBlank String kind, @NotBlank String title, @NotBlank String location,
                            @NotNull LocalDateTime startsAt, @NotNull LocalDateTime endsAt) {}
    public record TripInput(@NotBlank String traveler, @NotBlank @jakarta.validation.constraints.Email String travelerEmail, @NotBlank String origin, @NotBlank String destination,
                            @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                            @NotEmpty List<@Valid ItemInput> items) {}
    public record EditItemInput(Long id, @NotBlank String kind, @NotBlank String title, @NotBlank String location, @NotNull LocalDateTime startsAt, @NotNull LocalDateTime endsAt) {}
    public record EditTripInput(@NotBlank String traveler, @NotBlank @jakarta.validation.constraints.Email String travelerEmail, @NotBlank String origin, @NotBlank String destination, @NotNull LocalDate startDate, @NotNull LocalDate endDate, List<@Valid EditItemInput> items) {}
    public record ItemView(Long id, String kind, String title, String location, LocalDateTime startsAt,
                           LocalDateTime endsAt, String status, String changeNote) {}
    public record TripView(Long id, String traveler, String travelerEmail, String origin, String destination, LocalDate startDate,
                           LocalDate endDate, String status, String riskLevel, String riskReason,
                           String checkInStatus, LocalDateTime checkedInAt, String checkInNote,
                           List<ItemView> items) {}
    public record DisruptionInput(@NotBlank String type, @NotNull Long itemId) {}
    public record CheckInInput(@NotBlank String status, String note) {}
    public record ImpactView(String message, ItemView affectedItem, List<SegmentImpact> impactedSegments, List<OptionView> alternatives) {}

    @GetMapping
    public List<TripView> list() { return trips.findAll().stream().filter(access::canView).map(this::view).toList(); }

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
        access.coordinator();
        Trip trip = new Trip();
        trip.traveler = input.traveler().trim(); trip.travelerEmail = input.travelerEmail().trim().toLowerCase(); trip.origin = input.origin().trim();
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

    @PutMapping("/{id}")
    public TripView update(@PathVariable Long id, @Valid @RequestBody EditTripInput input) {
        access.coordinator(); Trip trip = find(id);
        if ("CANCELLED".equals(trip.lifecycle)) throw badRequest("Cancelled trips cannot be edited");
        if (input.endDate().isBefore(input.startDate())) throw badRequest("End date must be after start date");
        trip.traveler = input.traveler().trim(); trip.travelerEmail = input.travelerEmail().trim().toLowerCase(); trip.origin = input.origin().trim(); trip.destination = input.destination().trim();
        trip.startDate = input.startDate(); trip.endDate = input.endDate();
        java.util.Set<Long> retained = new java.util.HashSet<>();
        for (EditItemInput value : input.items()) {
            if (!List.of("FLIGHT", "HOTEL", "TRANSPORT").contains(value.kind().toUpperCase())) throw badRequest("Invalid item kind");
            if (!value.endsAt().isAfter(value.startsAt()) || value.startsAt().toLocalDate().isBefore(trip.startDate) || value.endsAt().toLocalDate().isAfter(trip.endDate)) throw badRequest("Item times must be within the trip dates");
            TripItem item;
            if (value.id() == null) { item = new TripItem(); item.trip = trip; trip.items.add(item); }
            else { item = item(trip, value.id()); retained.add(item.id); if (!"CONFIRMED".equals(item.status)) throw badRequest("Only confirmed items can be edited"); }
            item.kind = value.kind().toUpperCase(); item.title = value.title().trim(); item.location = value.location().trim();
            item.startsAt = value.startsAt(); item.endsAt = value.endsAt();
        }
        trip.items.removeIf(item -> item.id != null && "CONFIRMED".equals(item.status) && !retained.contains(item.id));
        Trip saved = trips.saveAndFlush(trip); events.save(new TripEvent(id, null, "TRIP_UPDATED", "Trip details updated")); return view(saved);
    }

    @PostMapping("/{id}/cancel")
    public TripView cancel(@PathVariable Long id) {
        access.coordinator(); Trip trip = find(id); trip.lifecycle = "CANCELLED";
        events.save(new TripEvent(id, null, "TRIP_CANCELLED", "Trip cancelled")); return view(trips.save(trip));
    }

    @PostMapping("/{id}/check-in")
    public TripView checkIn(@PathVariable Long id, @Valid @RequestBody CheckInInput input) {
        Trip trip = find(id);
        if ("CANCELLED".equals(trip.lifecycle)) throw badRequest("Trip is cancelled");
        String status = input.status().toUpperCase();
        if (!List.of("SAFE", "NEEDS_HELP").contains(status)) throw badRequest("Status must be SAFE or NEEDS_HELP");
        trip.checkInStatus = status; trip.checkedInAt = LocalDateTime.now();
        trip.checkInNote = input.note() == null ? null : input.note().trim();
        if (trip.checkInNote != null && trip.checkInNote.length() > 500) throw badRequest("Note is too long");
        events.save(new TripEvent(trip.id, null, "CHECK_IN", status + (trip.checkInNote == null || trip.checkInNote.isBlank() ? "" : ": " + trip.checkInNote)));
        if ("NEEDS_HELP".equals(status)) emailAlerts.helpRequested(trip);
        return view(trips.save(trip));
    }

    @PostMapping("/{id}/disruptions")
    public ImpactView disrupt(@PathVariable Long id, @Valid @RequestBody DisruptionInput input) {
        access.coordinator(); Trip trip = find(id);
        if ("CANCELLED".equals(trip.lifecycle)) throw badRequest("Cancelled trips cannot be disrupted");
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
        List<SegmentImpact> impacted = planner.assessDisruption(trip, item, type);
        item.status = "AFFECTED"; item.changeNote = type.replace('_', ' '); item.disruptionType = type;
        for (SegmentImpact consequence : impacted) {
            TripItem dependent = item(trip, consequence.id());
            if ("CONFIRMED".equals(dependent.status)) {
                dependent.status = "AT_RISK";
                dependent.riskSourceItemId = item.id;
                dependent.changeNote = "Connection may be missed after " + item.title;
            }
        }
        trips.save(trip);
        events.save(new TripEvent(trip.id, item.id, "DISRUPTION",
                item.changeNote + " affected " + item.title + "; " + impacted.size() + " later segment(s) at risk"));
        return impact(trip, item);
    }

    @GetMapping("/{id}/items/{itemId}/alternatives")
    public ImpactView options(@PathVariable Long id, @PathVariable Long itemId) {
        Trip trip = find(id); TripItem item = item(trip, itemId);
        if (!List.of("AFFECTED", "AT_RISK").contains(item.status)) throw badRequest("This segment does not need recovery");
        return impact(trip, item);
    }

    @PostMapping("/{id}/items/{itemId}/alternatives/{alternativeId}/apply")
    public TripView apply(@PathVariable Long id, @PathVariable Long itemId, @PathVariable Long alternativeId) {
        access.coordinator(); Trip trip = find(id);
        if ("CANCELLED".equals(trip.lifecycle)) throw badRequest("Trip is cancelled");
        TripItem affected = item(trip, itemId);
        if (!List.of("AFFECTED", "AT_RISK").contains(affected.status)) throw badRequest("Item does not need recovery");
        Alternative option = alternatives.findById(alternativeId).orElseThrow(() -> notFound("Alternative not found"));
        if (!trip.id.equals(option.tripId) || !affected.id.equals(option.itemId)) throw badRequest("Alternative does not belong to this item");
        if (!planner.fits(trip, affected, option)) throw badRequest("Alternative no longer fits this itinerary");
        List<SegmentImpact> remainingRisk = planner.assessOption(trip, affected, option);
        Set<Long> atRiskIds = remainingRisk.stream().map(SegmentImpact::id).collect(Collectors.toSet());
        for (TripItem dependent : trip.items) {
            if ("AT_RISK".equals(dependent.status) && affected.id.equals(dependent.riskSourceItemId)
                    && !atRiskIds.contains(dependent.id)) {
                dependent.status = "CONFIRMED"; dependent.changeNote = null; dependent.riskSourceItemId = null;
                events.save(new TripEvent(trip.id, dependent.id, "CONNECTION_CLEARED", dependent.title + " is back on track"));
            }
        }
        for (SegmentImpact consequence : remainingRisk) {
            TripItem dependent = item(trip, consequence.id());
            if ("CONFIRMED".equals(dependent.status)) {
                dependent.status = "AT_RISK"; dependent.riskSourceItemId = affected.id;
            }
            if ("AT_RISK".equals(dependent.status) && affected.id.equals(dependent.riskSourceItemId))
                dependent.changeNote = consequence.reason();
        }
        TripItem replacement = new TripItem(); replacement.trip = trip; replacement.kind = affected.kind;
        replacement.title = option.title; replacement.location = option.location;
        replacement.startsAt = option.startsAt; replacement.endsAt = option.endsAt;
        replacement.replacesItemId = affected.id;
        replacement.changeNote = "Replaced " + affected.title + " after " + affected.changeNote;
        affected.status = "REPLACED";
        trip.items.add(replacement);
        Trip saved = trips.saveAndFlush(trip);
        events.save(new TripEvent(saved.id, affected.id, "ALTERNATIVE_APPLIED",
                option.title + " replaced " + affected.title + "; " + remainingRisk.size() + " later segment(s) still need attention"));
        return view(saved);
    }

    private ImpactView impact(Trip trip, TripItem item) {
        List<Alternative> options = alternatives.findByTripIdAndItemId(trip.id, item.id);
        if (options.stream().noneMatch(option -> planner.fits(trip, item, option))) {
            String type = item.disruptionType == null ? "CONNECTION_RISK" : item.disruptionType;
            List<Alternative> existing = options;
            List<Alternative> fresh = planner.generate(trip, item, type).stream()
                    .filter(candidate -> existing.stream().noneMatch(saved -> saved.title.equals(candidate.title)
                            && saved.startsAt.equals(candidate.startsAt) && saved.endsAt.equals(candidate.endsAt)))
                    .toList();
            if (!fresh.isEmpty()) {
                options = new java.util.ArrayList<>(existing);
                options.addAll(alternatives.saveAll(fresh));
            }
        }
        List<OptionView> valid = options.stream().filter(option -> planner.fits(trip, item, option))
                .map(option -> planner.describe(trip, item, option))
                .sorted(Comparator.comparingInt((OptionView option) -> option.impactedSegments().size())
                        .thenComparingInt(OptionView::delayMinutes)
                        .thenComparing(OptionView::estimatedCost))
                .toList();
        List<SegmentImpact> impacted = "AFFECTED".equals(item.status)
                ? planner.assessDisruption(trip, item, item.disruptionType == null ? defaultType(item.kind) : item.disruptionType)
                : List.of();
        String message = item.changeNote + " affects " + item.title;
        return new ImpactView(message, itemView(item), impacted, valid);
    }

    private String defaultType(String kind) {
        return switch (kind) {
            case "FLIGHT" -> "FLIGHT_CANCELLATION";
            case "HOTEL" -> "HOTEL_UNAVAILABLE";
            default -> "TRANSPORT_DISRUPTION";
        };
    }

    private Trip find(Long id) { Trip trip = trips.findById(id).orElseThrow(() -> notFound("Trip not found")); access.view(trip); return trip; }
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
        long atRisk = items.stream().filter(i -> "AT_RISK".equals(i.status())).count();
        boolean needsHelp = "NEEDS_HELP".equals(trip.checkInStatus);
        String status = "CANCELLED".equals(trip.lifecycle) ? "CANCELLED" : affected > 0 || atRisk > 0 || needsHelp ? "NEEDS_ATTENTION" : "ON_TRACK";
        String risk = needsHelp || affected + atRisk > 1 ? "HIGH" : affected + atRisk == 1 ? "MEDIUM" : "LOW";
        String reason = needsHelp ? "Traveler requested help" : atRisk > 0
                ? affected + " disrupted and " + atRisk + " connected segment(s) at risk"
                : affected > 1 ? "Multiple unresolved disruptions"
                : affected == 1 ? "One unresolved disruption" : "No active disruption or help request";
        return new TripView(trip.id, trip.traveler, trip.travelerEmail, trip.origin, trip.destination, trip.startDate,
                trip.endDate, status, risk, reason, trip.checkInStatus == null ? "PENDING" : trip.checkInStatus,
                trip.checkedInAt, trip.checkInNote, items);
    }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
