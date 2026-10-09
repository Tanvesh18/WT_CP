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
    private final FlightController flights;

    public TripController(TripRepository trips, AlternativeRepository alternatives, TripEventRepository events, EmailAlerts emailAlerts, Access access, DisruptionPlanner planner, FlightController flights) {
        this.trips = trips; this.alternatives = alternatives; this.events = events; this.emailAlerts = emailAlerts; this.access = access; this.planner = planner; this.flights = flights;
    }

    public record ItemInput(@NotBlank String kind, @NotBlank String title, @NotBlank String location,
                            @NotNull LocalDateTime startsAt, @NotNull LocalDateTime endsAt, String flightSource, String flightOfferId, String flightAmount, String flightCurrency, String flightExpiresAt) {}
    public record TripInput(@NotBlank String traveler, @NotBlank @jakarta.validation.constraints.Email String travelerEmail, @NotBlank String origin, @NotBlank String destination, String originAirportCode, String destinationAirportCode,
                            @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                            @NotEmpty List<@Valid ItemInput> items) {}
    public record EditItemInput(Long id, @NotBlank String kind, @NotBlank String title, @NotBlank String location, @NotNull LocalDateTime startsAt, @NotNull LocalDateTime endsAt, String flightSource, String flightOfferId, String flightAmount, String flightCurrency, String flightExpiresAt) {}
    public record EditTripInput(@NotBlank String traveler, @NotBlank @jakarta.validation.constraints.Email String travelerEmail, @NotBlank String origin, @NotBlank String destination, String originAirportCode, String destinationAirportCode, @NotNull LocalDate startDate, @NotNull LocalDate endDate, List<@Valid EditItemInput> items) {}
    public record ItemView(Long id, String kind, String title, String location, LocalDateTime startsAt,
                           LocalDateTime endsAt, String status, String changeNote, String flightSource, String flightOfferId, String flightAmount, String flightCurrency, String flightExpiresAt, String flightOriginTimeZone, String flightDestinationTimeZone, Integer flightDurationMinutes, Integer flightStops, String flightOperatingCarriers, String flightRouteJson) {}
    public record TripView(Long id, String traveler, String travelerEmail, String origin, String destination, String originAirportCode, String destinationAirportCode, LocalDate startDate,
                           LocalDate endDate, String status, String riskLevel, String riskReason,
                           String checkInStatus, LocalDateTime checkedInAt, String checkInNote,
                           List<ItemView> items) {}
    public record DisruptionInput(@NotBlank String type, @NotNull Long itemId) {}
    public record CheckInInput(@NotBlank String status, String note) {}
    public record ImpactView(String message, ItemView affectedItem, List<SegmentImpact> impactedSegments, List<OptionView> alternatives, String recoverySearchMessage) {}

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
        trip.destination = input.destination().trim(); trip.originAirportCode = code(input.originAirportCode()); trip.destinationAirportCode = code(input.destinationAirportCode()); validateAirportPair(trip); trip.startDate = input.startDate(); trip.endDate = input.endDate();
        for (ItemInput value : input.items()) {
            String kind = value.kind().toUpperCase();
            if (!List.of("FLIGHT", "HOTEL", "TRANSPORT").contains(kind)) throw badRequest("Invalid item kind");
            TripItem item = new TripItem(); item.trip = trip; item.kind = kind;
            item.title = value.title().trim(); item.location = value.location().trim();
            item.startsAt = value.startsAt(); item.endsAt = value.endsAt();
            flightMetadata(item, value.flightSource(), value.flightOfferId(), true);
            validateFlightRoute(item, trip); validateSchedule(item, trip);
            trip.items.add(item);
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
        trip.originAirportCode = code(input.originAirportCode()); trip.destinationAirportCode = code(input.destinationAirportCode()); validateAirportPair(trip);
        trip.startDate = input.startDate(); trip.endDate = input.endDate();
        java.util.Set<Long> retained = new java.util.HashSet<>();
        for (EditItemInput value : input.items()) {
            if (!List.of("FLIGHT", "HOTEL", "TRANSPORT").contains(value.kind().toUpperCase())) throw badRequest("Invalid item kind");
            TripItem item;
            if (value.id() == null) { item = new TripItem(); item.trip = trip; trip.items.add(item); }
            else { item = item(trip, value.id()); retained.add(item.id); if (!"CONFIRMED".equals(item.status)) throw badRequest("Only confirmed items can be edited"); }
            boolean preserveOffer = "FLIGHT".equalsIgnoreCase(value.kind()) && value.flightOfferId() != null
                    && value.flightOfferId().equals(item.flightOfferId) && value.flightSource() != null && value.flightSource().equals(item.flightSource);
            item.kind = value.kind().toUpperCase();
            if (!preserveOffer) { item.title = value.title().trim(); item.location = value.location().trim(); item.startsAt = value.startsAt(); item.endsAt = value.endsAt(); }
            flightMetadata(item, value.flightSource(), value.flightOfferId(), !preserveOffer);
            validateFlightRoute(item, trip); validateSchedule(item, trip);
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
        String searchMessage = "FLIGHT_CANCELLATION".equals(type) && item.flightSource != null ? refreshDuffelAlternatives(trip, item) : null;
        return impact(trip, item, searchMessage);
    }

    @GetMapping("/{id}/items/{itemId}/alternatives")
    public ImpactView options(@PathVariable Long id, @PathVariable Long itemId) {
        Trip trip = find(id); TripItem item = item(trip, itemId);
        if (!List.of("AFFECTED", "AT_RISK").contains(item.status)) throw badRequest("This segment does not need recovery");
        return impact(trip, item);
    }

    @PostMapping("/{id}/items/{itemId}/alternatives/refresh")
    public ImpactView refreshFlightOptions(@PathVariable Long id, @PathVariable Long itemId) {
        access.coordinator(); Trip trip = find(id); TripItem item = item(trip, itemId);
        if (!"AFFECTED".equals(item.status) || !"FLIGHT_CANCELLATION".equals(item.disruptionType) || item.flightSource == null)
            throw badRequest("Duffel replacement search is available for cancelled Duffel flights");
        return impact(trip, item, refreshDuffelAlternatives(trip, item));
    }

    @PostMapping("/{id}/items/{itemId}/alternatives/{alternativeId}/apply")
    public TripView apply(@PathVariable Long id, @PathVariable Long itemId, @PathVariable Long alternativeId) {
        access.coordinator(); Trip trip = find(id);
        if ("CANCELLED".equals(trip.lifecycle)) throw badRequest("Trip is cancelled");
        TripItem affected = item(trip, itemId);
        if (!List.of("AFFECTED", "AT_RISK").contains(affected.status)) throw badRequest("Item does not need recovery");
        Alternative option = alternatives.findById(alternativeId).orElseThrow(() -> notFound("Alternative not found"));
        if (!trip.id.equals(option.tripId) || !affected.id.equals(option.itemId)) throw badRequest("Alternative does not belong to this item");
        if (option.source != null) {
            if (affected.recoverySearchId == null || !affected.recoverySearchId.equals(option.recoverySearchId))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Replacement search has been refreshed. Choose a current offer.");
            verifyRecoveryOffer(option);
        }
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
        if (option.source != null) {
            replacement.flightSource = option.source; replacement.flightOfferId = option.flightOfferId;
            replacement.flightAmount = option.estimatedCost.toPlainString(); replacement.flightCurrency = option.currency;
            replacement.flightExpiresAt = option.flightExpiresAt; replacement.flightOriginTimeZone = option.flightOriginTimeZone;
            replacement.flightDestinationTimeZone = option.flightDestinationTimeZone; replacement.flightDurationMinutes = option.flightDurationMinutes;
            replacement.flightStops = option.flightStops; replacement.flightOperatingCarriers = option.flightOperatingCarriers;
            replacement.flightRouteJson = option.flightRouteJson;
        }
        replacement.changeNote = "Replaced " + affected.title + " after " + affected.changeNote;
        affected.status = "REPLACED";
        trip.items.add(replacement);
        Trip saved = trips.saveAndFlush(trip);
        events.save(new TripEvent(saved.id, affected.id, "ALTERNATIVE_APPLIED",
                option.title + " replaced " + affected.title + "; " + remainingRisk.size() + " later segment(s) still need attention"
                + (option.source == null ? "" : "; Duffel offer added to itinerary only, no ticket booked")));
        return view(saved);
    }

    private ImpactView impact(Trip trip, TripItem item) { return impact(trip, item, null); }
    private ImpactView impact(Trip trip, TripItem item, String searchMessage) {
        List<Alternative> options = alternatives.findByTripIdAndItemId(trip.id, item.id);
        boolean duffelRecovery = "FLIGHT".equals(item.kind) && "FLIGHT_CANCELLATION".equals(item.disruptionType) && item.flightSource != null;
        if (!duffelRecovery && options.stream().noneMatch(option -> planner.fits(trip, item, option))) {
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
        List<OptionView> valid = options.stream().filter(option -> !duffelRecovery || option.source != null)
                .filter(option -> option.source == null || item.recoverySearchId != null && item.recoverySearchId.equals(option.recoverySearchId))
                .filter(option -> option.source == null || offerCurrent(option)).filter(option -> planner.fits(trip, item, option))
                .map(option -> planner.describe(trip, item, option))
                .sorted(Comparator.comparingInt((OptionView option) -> option.impactedSegments().size())
                        .thenComparingInt(OptionView::delayMinutes)
                        .thenComparing(OptionView::estimatedCost))
                .toList();
        List<SegmentImpact> impacted = "AFFECTED".equals(item.status)
                ? planner.assessDisruption(trip, item, item.disruptionType == null ? defaultType(item.kind) : item.disruptionType)
                : List.of();
        String message = item.changeNote + " affects " + item.title;
        return new ImpactView(message, itemView(item), impacted, valid, searchMessage);
    }

    private String refreshDuffelAlternatives(Trip trip, TripItem item) {
        item.recoverySearchId = java.util.UUID.randomUUID().toString();
        String[] route = item.location == null ? new String[0] : item.location.split(" → ");
        if (route.length != 2 || !route[0].matches("[A-Z]{3}") || !route[1].matches("[A-Z]{3}"))
            return "This flight has no searchable airport route.";
        if (item.startsAt.toLocalDate().isBefore(LocalDate.now())) return "Past flights cannot be searched in Duffel.";
        try {
            List<FlightController.FlightOffer> offers = flights.searchOffers(route[0], route[1], item.startsAt.toLocalDate()).offers();
            List<Alternative> saved = alternatives.findByTripIdAndItemId(trip.id, item.id);
            int count = 0;
            for (FlightController.FlightOffer offer : offers.stream()
                    .filter(value -> !value.id().equals(item.flightOfferId))
                    .sorted(Comparator.comparing(FlightController.FlightOffer::arrivingAt)
                            .thenComparing(value -> new java.math.BigDecimal(value.totalAmount())))
                    .limit(12).toList()) {
                Alternative option = saved.stream().filter(value -> offer.id().equals(value.flightOfferId)).findFirst().orElse(null);
                if (option == null) option = new Alternative(trip.id, item.id, "FLIGHT", offer.title(),
                        offer.origin() + " → " + offer.destination(), LocalDateTime.parse(offer.departingAt()),
                        LocalDateTime.parse(offer.arrivingAt()), offer.totalAmount(), "Duffel offer for the cancelled route; itinerary planning only.");
                option.kind = "FLIGHT"; option.title = offer.title(); option.location = offer.origin() + " → " + offer.destination();
                option.startsAt = LocalDateTime.parse(offer.departingAt()); option.endsAt = LocalDateTime.parse(offer.arrivingAt());
                option.estimatedCost = new java.math.BigDecimal(offer.totalAmount()); option.source = offer.source(); option.currency = offer.totalCurrency();
                option.flightOfferId = offer.id(); option.flightExpiresAt = offer.expiresAt();
                option.flightOriginTimeZone = offer.originTimeZone(); option.flightDestinationTimeZone = offer.destinationTimeZone();
                option.flightStops = offer.stops(); option.flightDurationMinutes = offer.durationMinutes();
                option.flightOperatingCarriers = offer.operatingCarriers().length() > 250 ? offer.operatingCarriers().substring(0, 250) : offer.operatingCarriers();
                option.flightRouteJson = flights.routeJson(offer); option.recoverySearchId = item.recoverySearchId;
                option.delayMinutes = (int) java.time.Duration.between(item.startsAt, option.startsAt).toMinutes();
                option.arrivalDelayMinutes = arrivalDelay(item, option);
                option.costDifference = item.flightCurrency != null && item.flightCurrency.equals(option.currency) && item.flightAmount != null
                        ? option.estimatedCost.subtract(new java.math.BigDecimal(item.flightAmount)) : null;
                if (planner.fits(trip, item, option)) { alternatives.save(option); count++; }
            }
            return count == 0 ? "Duffel returned no usable replacement offers for this route and trip window." : count + " Duffel replacement offer(s) found. Prices and availability can change.";
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            return "Duffel replacement search unavailable: " + ex.getReason();
        } catch (Exception ex) {
            return "Duffel replacement search is unavailable. Try again later.";
        }
    }
    private int arrivalDelay(TripItem original, Alternative option) {
        try {
            java.time.Instant oldArrival = original.endsAt.atZone(java.time.ZoneId.of(original.flightDestinationTimeZone)).toInstant();
            java.time.Instant newArrival = option.endsAt.atZone(java.time.ZoneId.of(option.flightDestinationTimeZone)).toInstant();
            return (int) java.time.Duration.between(oldArrival, newArrival).toMinutes();
        } catch (Exception ex) { return (int) java.time.Duration.between(original.endsAt, option.endsAt).toMinutes(); }
    }
    private boolean offerCurrent(Alternative option) {
        try { return java.time.Instant.parse(option.flightExpiresAt).isAfter(java.time.Instant.now()); }
        catch (Exception ex) { return false; }
    }
    private void verifyRecoveryOffer(Alternative option) {
        if (!offerCurrent(option)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Flight offer expired. Refresh replacement flights.");
        FlightController.FlightOffer current = flights.verifiedOffer(option.flightOfferId);
        if (!current.source().equals(option.source) || !current.totalCurrency().equals(option.currency)
                || new java.math.BigDecimal(current.totalAmount()).compareTo(option.estimatedCost) != 0
                || !LocalDateTime.parse(current.departingAt()).equals(option.startsAt)
                || !LocalDateTime.parse(current.arrivingAt()).equals(option.endsAt)
                || !(current.origin() + " → " + current.destination()).equals(option.location)
                || !flights.routeJson(current).equals(option.flightRouteJson))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Flight offer changed. Refresh replacement flights before applying it.");
    }

    private void flightMetadata(TripItem item, String source, String offerId, boolean verify) {
        if (!"FLIGHT".equals(item.kind) || source == null || offerId == null) {
            item.flightSource = null; item.flightOfferId = null; item.flightAmount = null; item.flightCurrency = null;
            item.flightExpiresAt = null; item.flightOriginTimeZone = null; item.flightDestinationTimeZone = null;
            item.flightDurationMinutes = null; item.flightStops = null; item.flightOperatingCarriers = null; item.flightRouteJson = null; return;
        }
        if (!List.of("DUFFEL_TEST", "DUFFEL_LIVE").contains(source)) throw badRequest("Invalid flight source");
        if (!verify && offerId.equals(item.flightOfferId) && source.equals(item.flightSource)) return;
        FlightController.FlightOffer offer = flights.verifiedOffer(offerId);
        if (!source.equals(offer.source())) throw badRequest("Flight offer source does not match");
        item.title = offer.title(); item.location = offer.origin() + " → " + offer.destination();
        item.startsAt = LocalDateTime.parse(offer.departingAt()); item.endsAt = LocalDateTime.parse(offer.arrivingAt());
        item.flightSource = offer.source(); item.flightOfferId = offer.id(); item.flightAmount = offer.totalAmount();
        item.flightCurrency = offer.totalCurrency(); item.flightExpiresAt = offer.expiresAt();
        item.flightOriginTimeZone = offer.originTimeZone(); item.flightDestinationTimeZone = offer.destinationTimeZone();
        item.flightDurationMinutes = offer.durationMinutes(); item.flightStops = offer.stops();
        item.flightOperatingCarriers = offer.operatingCarriers().length() > 250 ? offer.operatingCarriers().substring(0, 250) : offer.operatingCarriers();
        item.flightRouteJson = flights.routeJson(offer);
    }
    private String code(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase();
        if (!normalized.matches("[A-Z]{3}")) throw badRequest("Airport code must contain three letters");
        return normalized;
    }
    private void validateAirportPair(Trip trip) {
        if ((trip.originAirportCode == null) != (trip.destinationAirportCode == null)) throw badRequest("Choose both airports");
        if (trip.originAirportCode != null && trip.originAirportCode.equals(trip.destinationAirportCode)) throw badRequest("Choose different airports");
    }
    private void validateFlightRoute(TripItem item, Trip trip) {
        if (item.flightSource == null || trip.originAirportCode == null || trip.destinationAirportCode == null) return;
        String route = item.location;
        String forward = trip.originAirportCode + " → " + trip.destinationAirportCode;
        String reverse = trip.destinationAirportCode + " → " + trip.originAirportCode;
        if (!route.equals(forward) && !route.equals(reverse)) throw badRequest("Selected flight does not match the trip route");
    }
    private void validateSchedule(TripItem item, Trip trip) {
        boolean chronological;
        if (item.flightSource != null && item.flightOriginTimeZone != null && item.flightDestinationTimeZone != null
                && !item.flightOriginTimeZone.isBlank() && !item.flightDestinationTimeZone.isBlank()) {
            try { chronological = item.endsAt.atZone(java.time.ZoneId.of(item.flightDestinationTimeZone)).toInstant()
                    .isAfter(item.startsAt.atZone(java.time.ZoneId.of(item.flightOriginTimeZone)).toInstant()); }
            catch (Exception ex) { throw badRequest("Flight has invalid airport time zones"); }
        } else chronological = item.endsAt.isAfter(item.startsAt);
        if (!chronological || item.startsAt.toLocalDate().isBefore(trip.startDate) || item.endsAt.toLocalDate().isAfter(trip.endDate))
            throw badRequest("Item times must be within trip dates and end after start");
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
                item.endsAt, item.status, item.changeNote, item.flightSource, item.flightOfferId, item.flightAmount, item.flightCurrency, item.flightExpiresAt, item.flightOriginTimeZone, item.flightDestinationTimeZone, item.flightDurationMinutes, item.flightStops, item.flightOperatingCarriers, item.flightRouteJson);
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
        return new TripView(trip.id, trip.traveler, trip.travelerEmail, trip.origin, trip.destination, trip.originAirportCode, trip.destinationAirportCode, trip.startDate,
                trip.endDate, status, risk, reason, trip.checkInStatus == null ? "PENDING" : trip.checkInStatus,
                trip.checkedInAt, trip.checkInNote, items);
    }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
