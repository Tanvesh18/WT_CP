package com.tripshield.backend.service;

import com.tripshield.backend.model.Alternative;
import com.tripshield.backend.model.Trip;
import com.tripshield.backend.model.TripItem;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Deterministic prototype rules. Prices and availability are simulated, never live quotes. */
@Component
public class DisruptionPlanner {
    public record SegmentImpact(Long id, String title, String kind, int bufferMinutes, int requiredMinutes, String reason) {}
    public record OptionView(Long id, String kind, String title, String location, LocalDateTime startsAt,
                             LocalDateTime endsAt, int delayMinutes, java.math.BigDecimal estimatedCost,
                             String description, String practicality, List<SegmentImpact> impactedSegments,
                             String source, String currency, String flightOfferId, String flightExpiresAt,
                             Integer arrivalDelayMinutes, java.math.BigDecimal costDifference,
                             Integer flightStops, Integer flightDurationMinutes, String flightOperatingCarriers,
                             String flightOriginTimeZone, String flightDestinationTimeZone) {}

    public int assumedDelay(String type) {
        return switch (type) {
            case "FLIGHT_CANCELLATION" -> 180;
            case "HOTEL_UNAVAILABLE" -> 120;
            case "TRANSPORT_DISRUPTION" -> 90;
            case "SEVERE_WEATHER" -> 240;
            default -> throw new IllegalArgumentException("Unknown disruption type");
        };
    }

    public List<SegmentImpact> assessDisruption(Trip trip, TripItem source, String type) {
        return assess(trip, source, reference(source).plusMinutes(assumedDelay(type)));
    }

    public List<SegmentImpact> assessOption(Trip trip, TripItem source, Alternative option) {
        LocalDateTime ready = "HOTEL".equals(option.kind) ? option.startsAt : option.endsAt;
        return assess(trip, source, ready);
    }

    private List<SegmentImpact> assess(Trip trip, TripItem source, LocalDateTime ready) {
        LocalDateTime horizon = reference(source).plusHours(12);
        return trip.items.stream()
                .filter(next -> !next.id.equals(source.id) && !"REPLACED".equals(next.status))
                .filter(next -> !isUpstreamReplacement(trip, source, next) && !replacesEarlierSegment(trip, source, next))
                .filter(next -> next.startsAt.isAfter(source.startsAt) && !next.startsAt.isAfter(horizon))
                .map(next -> {
                    int required = connectionMinutes(source.kind, next.kind);
                    int buffer = (int) Duration.between(ready, next.startsAt).toMinutes();
                    String reason = buffer < 0 ? "Replacement timing overlaps this segment"
                            : "Only " + buffer + " min remains before this segment; " + required + " min is recommended";
                    return new SegmentImpact(next.id, next.title, next.kind, buffer, required, reason);
                })
                .filter(impact -> impact.bufferMinutes() < impact.requiredMinutes())
                .sorted(Comparator.comparing(impact -> trip.items.stream().filter(item -> item.id.equals(impact.id())).findFirst().orElseThrow().startsAt))
                .toList();
    }

    public int connectionMinutes(String source, String next) {
        if ("FLIGHT".equals(next)) return "TRANSPORT".equals(source) ? 120 : 90;
        if ("TRANSPORT".equals(next)) return 45;
        return 60;
    }

    private LocalDateTime reference(TripItem item) {
        return "HOTEL".equals(item.kind) ? item.startsAt : item.endsAt;
    }

    private TripItem replacementFor(Trip trip, TripItem original) {
        TripItem replacement = trip.items.stream().filter(other -> original.id.equals(other.replacesItemId)
                && !"REPLACED".equals(other.status)).findFirst().orElse(null);
        if (replacement == null && "REPLACED".equals(original.status)) {
            String legacyNote = "Replaced " + original.title + " after ";
            replacement = trip.items.stream().filter(other -> other.changeNote != null
                    && other.changeNote.startsWith(legacyNote) && original.kind.equals(other.kind)
                    && !"REPLACED".equals(other.status)).findFirst().orElse(null);
        }
        return replacement;
    }

    private boolean isUpstreamReplacement(Trip trip, TripItem source, TripItem candidate) {
        if (source.riskSourceItemId == null) return false;
        TripItem upstream = trip.items.stream().filter(item -> source.riskSourceItemId.equals(item.id)).findFirst().orElse(null);
        return upstream != null && (candidate.id.equals(upstream.id)
                || candidate == replacementFor(trip, upstream));
    }

    private boolean replacesEarlierSegment(Trip trip, TripItem source, TripItem candidate) {
        if (candidate.replacesItemId != null) {
            TripItem original = trip.items.stream().filter(item -> candidate.replacesItemId.equals(item.id)).findFirst().orElse(null);
            return original != null && original.startsAt.isBefore(source.startsAt);
        }
        if (candidate.changeNote != null && candidate.changeNote.startsWith("Replaced ")) {
            return trip.items.stream().anyMatch(original -> "REPLACED".equals(original.status)
                    && original.startsAt.isBefore(source.startsAt)
                    && candidate.changeNote.startsWith("Replaced " + original.title + " after "));
        }
        return false;
    }

    public LocalDateTime earliestFeasibleStart(Trip trip, TripItem item) {
        if (item.riskSourceItemId == null) return null;
        TripItem source = trip.items.stream().filter(other -> item.riskSourceItemId.equals(other.id)).findFirst().orElse(null);
        if (source == null) return null;
        TripItem replacement = replacementFor(trip, source);
        LocalDateTime upstreamReady = replacement != null ? reference(replacement)
                : source.disruptionType != null ? reference(source).plusMinutes(assumedDelay(source.disruptionType)) : reference(source);
        LocalDateTime earliest = upstreamReady.plusMinutes(connectionMinutes(source.kind, item.kind));
        for (TripItem original : trip.items) {
            if (original.id.equals(item.id) || !original.startsAt.isBefore(item.startsAt)
                    || !("FLIGHT".equals(original.kind) || "TRANSPORT".equals(original.kind))) continue;
            TripItem laterReplacement = replacementFor(trip, original);
            if (laterReplacement != null) {
                LocalDateTime ready = reference(laterReplacement).plusMinutes(connectionMinutes(original.kind, item.kind));
                if (ready.isAfter(earliest)) earliest = ready;
            }
        }
        return earliest;
    }

    public List<Alternative> generate(Trip trip, TripItem item, String type) {
        List<Alternative> options = new ArrayList<>();
        String route = trip.origin + " → " + trip.destination;
        int nights = (int) Math.max(1, ChronoUnit.DAYS.between(item.startsAt.toLocalDate(), item.endsAt.toLocalDate()));
        switch (item.kind) {
            case "FLIGHT" -> {
                if (!"SEVERE_WEATHER".equals(type)) options.add(option(trip, item, "Earlier standby flight", route, -60, -60, 13500,
                        "Simulated earlier seat; higher fare but can protect tight connections."));
                options.add(option(trip, item, "Direct flight rebooking", route, "SEVERE_WEATHER".equals(type) ? 180 : 90,
                        "SEVERE_WEATHER".equals(type) ? 180 : 90, 9800,
                        "Simulated direct replacement with standard transfer timing."));
                options.add(option(trip, item, "Later economy flight", route, "SEVERE_WEATHER".equals(type) ? 360 : 240,
                        "SEVERE_WEATHER".equals(type) ? 360 : 240, 6200,
                        "Simulated lower-fare departure with a longer delay."));
            }
            case "HOTEL" -> {
                options.add(option(trip, item, "Nearby equivalent hotel", item.location, 0, 0, nights * 7200,
                        "Simulated nearby room with the original check-in window."));
                options.add(option(trip, item, "Flexible check-in hotel", item.location, 90, 0, nights * 5200,
                        "Simulated room with a later check-in and the same checkout."));
                options.add(option(trip, item, "Budget replacement stay", item.location, 150, 0, nights * 3900,
                        "Simulated lower-cost stay with a later check-in."));
            }
            default -> {
                options.add(option(trip, item, "Priority car transfer", item.location, 15, 15, 2800,
                        "Simulated private transfer with the shortest pickup delay."));
                options.add(option(trip, item, "Shared airport shuttle", item.location, 75, 75, 900,
                        "Simulated shared transfer with a scheduled pickup."));
                options.add(option(trip, item, "Scheduled rail or road transfer", item.location, 120, 120, 650,
                        "Simulated low-cost transfer with a longer wait."));
            }
        }
        return options.stream().filter(option -> fits(trip, item, option)).toList();
    }

    private Alternative option(Trip trip, TripItem item, String title, String location, int startShift,
                               int endShift, int cost, String description) {
        LocalDateTime earliest = earliestFeasibleStart(trip, item);
        LocalDateTime startsAt = earliest == null ? item.startsAt.plusMinutes(startShift)
                : earliest.plusMinutes(Math.max(0, startShift));
        LocalDateTime endsAt = earliest == null ? item.endsAt.plusMinutes(endShift)
                : "HOTEL".equals(item.kind) ? item.endsAt : startsAt.plus(Duration.between(item.startsAt, item.endsAt));
        Alternative option = new Alternative(trip.id, item.id, item.kind, title, location,
                startsAt, endsAt, String.valueOf(cost), description);
        option.delayMinutes = (int) Duration.between(item.startsAt, option.startsAt).toMinutes();
        return option;
    }

    public boolean fits(Trip trip, TripItem affected, Alternative option) {
        if (!affected.kind.equals(option.kind) || option.startsAt == null || option.endsAt == null) return false;
        LocalDateTime earliest = earliestFeasibleStart(trip, affected);
        if (earliest != null && option.startsAt.isBefore(earliest)) return false;
        if (!chronological(option) || option.startsAt.toLocalDate().isBefore(trip.startDate)
                || option.endsAt.toLocalDate().isAfter(trip.endDate)) return false;
        return trip.items.stream().filter(other -> !other.id.equals(affected.id) && ("CONFIRMED".equals(other.status) || "AT_RISK".equals(other.status))
                        && other.kind.equals(affected.kind))
                .noneMatch(other -> option.startsAt.isBefore(other.endsAt) && other.startsAt.isBefore(option.endsAt));
    }

    private boolean chronological(Alternative option) {
        if (option.flightOriginTimeZone != null && option.flightDestinationTimeZone != null) {
            try { return option.endsAt.atZone(java.time.ZoneId.of(option.flightDestinationTimeZone)).toInstant()
                    .isAfter(option.startsAt.atZone(java.time.ZoneId.of(option.flightOriginTimeZone)).toInstant()); }
            catch (Exception ignored) { return false; }
        }
        return option.endsAt.isAfter(option.startsAt);
    }

    public OptionView describe(Trip trip, TripItem affected, Alternative option) {
        List<SegmentImpact> impacts = assessOption(trip, affected, option);
        String practicality = impacts.isEmpty() ? "Protects the remaining itinerary"
                : impacts.size() == 1 ? "One connection needs attention" : impacts.size() + " segments need attention";
        return new OptionView(option.id, option.kind, option.title, option.location, option.startsAt,
                option.endsAt, option.delayMinutes, option.estimatedCost, option.description, practicality, impacts,
                option.source, option.currency, option.flightOfferId, option.flightExpiresAt,
                option.arrivalDelayMinutes, option.costDifference, option.flightStops, option.flightDurationMinutes, option.flightOperatingCarriers,
                option.flightOriginTimeZone, option.flightDestinationTimeZone);
    }
}
