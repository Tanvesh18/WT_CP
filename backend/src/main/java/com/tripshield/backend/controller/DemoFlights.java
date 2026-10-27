package com.tripshield.backend.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Deterministic planning examples. These are never airline inventory or bookable offers. */
@Component
public class DemoFlights {
    private record Airport(String code, String name, String zone, double latitude, double longitude) {}
    private record Route(String a, String b, int minutes, int basePrice, String currency) {}

    private static final Map<String, Airport> AIRPORTS = Map.of(
            "PNQ", new Airport("PNQ", "Pune Airport", "Asia/Kolkata", 18.5821, 73.9197),
            "BOM", new Airport("BOM", "Chhatrapati Shivaji Maharaj International Airport", "Asia/Kolkata", 19.0896, 72.8656),
            "DEL", new Airport("DEL", "Indira Gandhi International Airport", "Asia/Kolkata", 28.5562, 77.1000),
            "BLR", new Airport("BLR", "Kempegowda International Airport", "Asia/Kolkata", 13.1986, 77.7066),
            "HYD", new Airport("HYD", "Rajiv Gandhi International Airport", "Asia/Kolkata", 17.2403, 78.4294),
            "DXB", new Airport("DXB", "Dubai International Airport", "Asia/Dubai", 25.2532, 55.3657),
            "LHR", new Airport("LHR", "Heathrow Airport", "Europe/London", 51.4700, -0.4543));
    private static final List<Route> ROUTES = List.of(
            new Route("PNQ", "DEL", 125, 5600, "INR"),
            new Route("PNQ", "BOM", 65, 3400, "INR"),
            new Route("BOM", "DEL", 130, 5900, "INR"),
            new Route("BLR", "DEL", 165, 6900, "INR"),
            new Route("BLR", "HYD", 70, 3500, "INR"),
            new Route("HYD", "DEL", 125, 5700, "INR"),
            new Route("BOM", "DXB", 190, 18500, "INR"),
            new Route("DEL", "DXB", 220, 21000, "INR"),
            new Route("BOM", "LHR", 570, 52000, "INR"),
            new Route("DEL", "LHR", 555, 51000, "INR"));
    private static final List<LocalTime> DEPARTURES = List.of(
            LocalTime.of(6, 15), LocalTime.of(9, 30), LocalTime.of(13, 0),
            LocalTime.of(17, 15), LocalTime.of(21, 0));
    private static final String[] CARRIERS = {"IndiGo", "Air India", "Akasa Air", "Air India", "IndiGo"};

    public List<FlightController.FlightOffer> search(String origin, String destination, LocalDate date) {
        Route route = ROUTES.stream().filter(r -> r.a.equals(origin) && r.b.equals(destination)
                || r.b.equals(origin) && r.a.equals(destination)).findFirst().orElse(null);
        if (route == null || !AIRPORTS.containsKey(origin) || !AIRPORTS.containsKey(destination)) return List.of();
        Airport from = AIRPORTS.get(origin), to = AIRPORTS.get(destination);
        List<FlightController.FlightOffer> offers = new ArrayList<>();
        for (int i = 0; i < DEPARTURES.size(); i++) {
            ZonedDateTime departure = date.atTime(DEPARTURES.get(i)).atZone(ZoneId.of(from.zone));
            Instant expiry = departure.minusMinutes(30).toInstant();
            if (!expiry.isAfter(Instant.now())) continue;
            ZonedDateTime arrival = departure.plusMinutes(route.minutes + (i % 2) * 10)
                    .withZoneSameInstant(ZoneId.of(to.zone));
            String carrier = route.currency.equals("INR") && !origin.equals("LHR") && !destination.equals("LHR")
                    ? CARRIERS[i] : "Air India";
            String number = "DEMO " + (241 + i * 17);
            String id = "demo_" + origin + "_" + destination + "_" + date.toString().replace("-", "") + "_" + i;
            String amount = String.format(java.util.Locale.ROOT, "%.2f", (double) (route.basePrice + i * (route.currency.equals("INR") ? 640 : 65)));
            FlightController.FlightLeg leg = new FlightController.FlightLeg(number, carrier + " (demo)", origin, destination,
                    departure.toOffsetDateTime().toString(), arrival.toOffsetDateTime().toString(), from.zone, to.zone,
                    from.name, to.name, from.latitude, from.longitude, to.latitude, to.longitude, List.of());
            offers.add(new FlightController.FlightOffer(id, "DEMO", number + " · " + carrier + " demo schedule", origin, destination,
                    leg.departingAt(), leg.arrivingAt(), from.zone, to.zone, amount, route.currency, expiry.toString(),
                    0, route.minutes + (i % 2) * 10, carrier + " (demo)", List.of(leg)));
        }
        if (origin.equals("BOM") && destination.equals("LHR") || origin.equals("LHR") && destination.equals("BOM")) {
            Airport via = AIRPORTS.get("DXB");
            ZonedDateTime departure = date.atTime(9, 30).atZone(ZoneId.of(from.zone));
            Instant expiry = departure.minusMinutes(30).toInstant();
            if (expiry.isAfter(Instant.now())) {
                ZonedDateTime connectionArrival = departure.plusMinutes(origin.equals("BOM") ? 190 : 450).withZoneSameInstant(ZoneId.of(via.zone));
                ZonedDateTime connectionDeparture = connectionArrival.plusMinutes(120);
                ZonedDateTime arrival = connectionDeparture.plusMinutes(destination.equals("LHR") ? 450 : 190).withZoneSameInstant(ZoneId.of(to.zone));
                String id = "demo_" + origin + "_" + destination + "_" + date.toString().replace("-", "") + "_5";
                var first = new FlightController.FlightLeg("DEMO 318", "Air India (demo)", origin, "DXB",
                        departure.toOffsetDateTime().toString(), connectionArrival.toOffsetDateTime().toString(), from.zone, via.zone,
                        from.name, via.name, from.latitude, from.longitude, via.latitude, via.longitude, List.of());
                var second = new FlightController.FlightLeg("DEMO 419", "Emirates (demo)", "DXB", destination,
                        connectionDeparture.toOffsetDateTime().toString(), arrival.toOffsetDateTime().toString(), via.zone, to.zone,
                        via.name, to.name, via.latitude, via.longitude, to.latitude, to.longitude, List.of());
                offers.add(new FlightController.FlightOffer(id, "DEMO", "DEMO 318/419 · Dubai connection", origin, destination,
                        first.departingAt(), second.arrivingAt(), from.zone, to.zone, "47500.00", "INR", expiry.toString(),
                        1, 760, "Air India / Emirates (demo)", List.of(first, second)));
            }
        }
        return offers;
    }

    public FlightController.FlightOffer find(String id) {
        if (id == null || !id.matches("demo_[A-Z]{3}_[A-Z]{3}_[0-9]{8}_[0-5]")) return null;
        String[] parts = id.split("_");
        try {
            LocalDate date = LocalDate.parse(parts[3], java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            return search(parts[1], parts[2], date).stream().filter(offer -> id.equals(offer.id())).findFirst().orElse(null);
        } catch (RuntimeException invalid) {
            return null;
        }
    }
}
