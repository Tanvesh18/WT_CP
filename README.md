# TripShield

TripShield is a course project prototype for managing corporate trips and responding to travel disruptions. An employee or travel coordinator can create an itinerary, simulate a problem, review sample alternatives, and update the trip.

## Technology

- **Frontend:** React, TypeScript, and Vite (`frontend/`)
- **Backend:** Java, Spring Boot, REST APIs, and JPA/Hibernate (`backend/`)
- **Database:** MySQL

The frontend sends requests to the backend under `/api`. Vite forwards those requests to `http://127.0.0.1:8080` during local development. The backend stores trips, itinerary items, sample alternatives, and activity history in MySQL.

## Implemented features

- Create a trip with a traveler, origin, destination, dates, and flight, hotel, or transport items.
- Search trips and filter by status, risk, and overlapping date range; sort by date or risk.
- View each trip's itinerary as a day-grouped timeline with segment state and activity history.
- Highlight trips with a disrupted or at-risk itinerary segment in the coordinator workspace.
- Simulate a flight cancellation, hotel unavailability, transport issue, or severe weather on an itinerary item.
- Automatically flag nearby connected segments when a simulated disruption leaves insufficient transfer time.
- Compare simulated alternatives by estimated cost, departure timing, and remaining connection risks.
- Choose an alternative to add a replacement item, mark the old item as replaced, and see an on-screen update message.
- Generate context-based sample flights, stays, and transfers. Dependent replacement segments cannot depart before the upstream travel arrives. Options outside trip dates or conflicting with another booking of the same type are hidden.
- View a persistent activity history for trip creation, disruptions, alternative selections, and check-ins.
- See a simple low, medium, or high risk level based on unresolved disruptions, at-risk connections, and help requests.
- Record a traveler check-in as **Safe** or **Needs help**, with an optional note.
- Switch between the itinerary timeline and a schematic route map for supported Indian cities. Unmapped locations remain visible in the stop list.

Alternatives are saved for each affected itinerary item. They are demonstration options built from that item's details; they do not represent available bookings or live prices. The connection rules use a 12-hour look-ahead and fixed minimum buffers (45–120 minutes depending on segment type). They are planning heuristics, not airline, hotel, or safety guarantees.

## Run locally

### Prerequisites

- Java 21 or newer and Maven
- Node.js and npm
- A running MySQL server

Create the database once in MySQL:

```sql
CREATE DATABASE tripshield;
```

Open **two PowerShell terminals** in the project root (`WT_CP`).

Before the first run, open `backend/config/local.properties` and enter your MySQL password after `spring.datasource.password=`. You can change the database URL or username there too. This file is ignored by Git and Spring Boot loads it automatically when started from `backend/`. A shareable example is in `backend/config/local.properties.example`.

### Terminal 1: backend

```powershell
cd backend
mvn.cmd spring-boot:run
```

The default database address is `jdbc:mysql://localhost:3306/tripshield`. Keep the terminal open. To check the backend, visit `http://127.0.0.1:8080/api/health`.

### Terminal 2: frontend

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

Open the local URL printed by Vite, usually `http://localhost:5173`. Keep this terminal open too. On PowerShell, use `npm.cmd` if the `npm.ps1` script is blocked by the execution policy.

You only need to edit `backend/config/local.properties` once, unless your database credentials change. Do not put a real password in the tracked `application.properties` or `local.properties.example` files.

## Accounts and new features

Before using the new interface, add these lines to the ignored `backend/config/local.properties` and restart the backend:

```properties
tripshield.coordinator.email=coordinator@example.com
tripshield.coordinator.password=choose-a-strong-password
```

Sign in as coordinator to review booking requests, create or edit itineraries, simulate disruptions, and cancel trips. Existing trips remain in MySQL. Edit each existing trip once to add the traveler's email address. A traveler can select **Create traveler account** on the sign-in screen and register with the same email address assigned to their trips. The account is stored in MySQL with a bcrypt password hash. Travelers can search Duffel flight offers and submit a booking request for themselves from **My trips**. A coordinator approves the request before it becomes an active itinerary. TripShield does not issue a ticket, charge a payment, or reserve a hotel or vehicle. Sessions are stored in MySQL as token hashes and expire after 12 hours or sign-out.

The coordinator dashboard highlights active trips, upcoming journeys, disruptions, and help requests. The traveler dashboard shows assigned journeys and an entry point to submit a booking request. Risk and check-in controls appear only in the coordinator workspace. Cancellation keeps a trip and its history for review. To send a real email when a traveler requests help, configure `tripshield.alert.to` and the `spring.mail.*` SMTP properties shown in `backend/config/local.properties.example`. Without SMTP settings, check-ins still work and are saved, but no email is sent.

## Try the demo

1. Create a trip and add at least one itinerary item. Item start and end times must fall within the trip dates.
2. Open **Trips** and select the trip from the list.
3. Simulate a disruption on a confirmed item. The trip is marked **Needs attention**.
4. Review the alternatives by timing, cost, and connection risk, then apply one. If a later segment remains **At risk**, open its recovery options and resolve it too. The trip returns to **On track** when no disrupted or at-risk segments remain.
5. Try the **Route map** tab, record a traveler check-in, and review the activity history. A **Needs help** check-in keeps the trip marked **Needs attention** until it is changed to **Safe**.

## API endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/health` | Check that the backend is running |
| POST | `/api/auth/register` | Create a traveler account |
| POST | `/api/auth/login` | Sign in |
| POST | `/api/auth/logout` | Sign out |
| GET | `/api/auth/me` | Validate the current session |
| GET | `/api/trips` | List trips |
| POST | `/api/trips` | Create a trip |
| GET | `/api/trips/{id}` | Get one trip |
| GET | `/api/trips/{id}/history` | Get the trip's activity history |
| POST | `/api/trips/{id}/check-in` | Record safe or needs-help status |
| POST | `/api/trips/{id}/disruptions` | Simulate a disruption on an item |
| GET | `/api/trips/{id}/items/{itemId}/alternatives` | Get alternatives for a disrupted or at-risk item |
| POST | `/api/trips/{id}/items/{itemId}/alternatives/{alternativeId}/apply` | Apply an alternative |

For a check-in, send JSON such as `{ "status": "SAFE", "note": "Reached the hotel" }` or use `"NEEDS_HELP"`. To simulate a disruption, send `{ "type": "FLIGHT_CANCELLATION", "itemId": 1 }`; other types are `HOTEL_UNAVAILABLE`, `TRANSPORT_DISRUPTION`, and `SEVERE_WEATHER`.

## Current scope

This is a prototype using generated sample alternatives and simulated disruptions. The map is a schematic view of supported Indian city coordinates; it is not a live map or location tracker. Live flight/hotel/weather integrations are not implemented. Account login and optional SMTP help-request alerts are available after local configuration. On-screen messages last only for the current browser session, while the activity history is stored in MySQL. The risk level is a simple rule, not a formal safety assessment.

Run `npm.cmd run test`, `npm.cmd run build`, and `npm.cmd run lint` from `frontend/` for the frontend checks. A full backend test run needs Maven access to the required Surefire and JUnit artifacts.

## Package lockfiles

`frontend/package-lock.json` belongs to the React app and should be kept so installs use the recorded dependency versions. The `package-lock.json` in the `WT_CP` root is an empty npm lockfile with no matching root `package.json`; the project does not need it. Run npm commands from `frontend/`.

## Duffel flight search

Coordinators can search economy flights for one adult from the trip editor using three-letter airport codes (for example, PNQ to DEL). Add `tripshield.duffel.token=duffel_test_...` to the ignored `backend/config/local.properties` and restart the backend. Obtain the token from your Duffel dashboard. The token is used only by Spring Boot and must not be placed in the frontend. Without a token, search shows a configuration error; manual itinerary entry still works.

Duffel test tokens return simulated offers. A live token can return real offers on routes supported by Duffel. Selecting an offer saves its schedule, price, source, and offer ID as an itinerary snapshot. It does **not** book or pay for a ticket. Offers expire and price or availability may change. Disruption recovery alternatives remain simulated.

## Creating a trip

Coordinators use **New trip** to open the full-page planner: choose airports and travel dates, assign a registered traveler (or a guest email), search and select Duffel outbound/optional return offers, add optional seeded stay and airport transfer templates, then review the itinerary and estimated costs. Airport suggestions are a curated search aid; only the Duffel API supplies flight results. Hotel and transfer templates are planning examples, not reservations. Flight offers are checked again with Duffel when a new trip is saved, and expired offers must be searched again. The app saves an itinerary, not an airline ticket. Existing trips remain editable from the trip workspace.

## Flight route map and disruption recovery

Selected Duffel offers now save their airport coordinates and flight legs. The trip workspace places a geographic route map beside the itinerary timeline, and the flight-selection step previews the same route immediately. The map uses OpenStreetMap tiles with visible attribution; the overlay plots airport coordinates from Duffel. Its connecting lines are geographic guides, not tracked aircraft paths. Older flights without saved coordinates show a clear fallback. Browser access to the tile server is needed for the basemap.

For a simulated cancellation of a Duffel flight, TripShield searches Duffel again for the flight route and departure date. Replacement offers show arrival delay, fare difference when currencies match, and downstream itinerary risks. Refreshing starts a new search and makes older offers inapplicable. The backend verifies an offer again before adding it as an itinerary replacement. This does **not** book, pay for, or change an airline ticket. Duffel test-token results remain simulated. Recovery options for hotel, ground transport, and other prototype disruptions remain simulated and are labeled accordingly.
