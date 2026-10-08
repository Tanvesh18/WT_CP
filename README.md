# TripShield

TripShield is a course project prototype for managing corporate trips and responding to travel disruptions. An employee or travel coordinator can create an itinerary, simulate a problem, review sample alternatives, and update the trip.

## Technology

- **Frontend:** React, TypeScript, and Vite (`frontend/`)
- **Backend:** Java, Spring Boot, REST APIs, and JPA/Hibernate (`backend/`)
- **Database:** MySQL

The frontend sends requests to the backend under `/api`. Vite forwards those requests to `http://127.0.0.1:8080` during local development. The backend stores trips, itinerary items, sample alternatives, and activity history in MySQL.

## Implemented features

- Create a trip with a traveler, origin, destination, dates, and flight, hotel, or transport items.
- View all trips and the selected trip's itinerary in time order.
- Highlight trips with an affected itinerary item in the coordinator-style trip list.
- Simulate a flight cancellation, hotel unavailability, transport issue, or severe weather on an itinerary item.
- See which item is affected and compare sample alternatives by delay and estimated cost.
- Choose an alternative to add a replacement item, mark the old item as replaced, and see an on-screen update message.
- Generate sample alternatives for the affected trip item, using its route or location and times. Options outside the trip dates or overlapping another confirmed item of the same type are hidden.
- View a persistent activity history for trip creation, disruptions, alternative selections, and check-ins.
- See a simple low, medium, or high risk level based on unresolved disruptions and help requests.
- Record a traveler check-in as **Safe** or **Needs help**, with an optional note.
- Switch between the itinerary timeline and a schematic route map for supported Indian cities.

Alternatives are saved for each affected itinerary item. They are demonstration options built from that item's details; they do not represent available bookings or live prices.

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

Sign in as coordinator to create, edit, search, filter, and cancel trips. Existing trips remain in MySQL. Edit each existing trip once to add the traveler's email address. A traveler can register from the sign-in screen using that same email address and then view their assigned trips and submit a check-in. Account sessions last until backend restart or sign-out.

The dashboard shows status, destination, and disruption counts. Cancellation keeps a trip and its history for review. To send a real email when a traveler requests help, configure `tripshield.alert.to` and the `spring.mail.*` SMTP properties shown in `backend/config/local.properties.example`. Without SMTP settings, check-ins still work and are saved, but no email is sent.

## Try the demo

1. Create a trip and add at least one itinerary item. Item start and end times must fall within the trip dates.
2. Select the trip from the left-hand list.
3. Simulate a disruption on a confirmed item. The trip is marked **Needs attention**.
4. Review the suggested alternatives and select one. The replacement is added to the itinerary and the trip returns to **On track** when no affected items remain.
5. Try the **Route map** tab, record a traveler check-in, and review the activity history. A **Needs help** check-in keeps the trip marked **Needs attention** until it is changed to **Safe**.

## API endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/health` | Check that the backend is running |
| GET | `/api/trips` | List trips |
| POST | `/api/trips` | Create a trip |
| GET | `/api/trips/{id}` | Get one trip |
| GET | `/api/trips/{id}/history` | Get the trip's activity history |
| POST | `/api/trips/{id}/check-in` | Record safe or needs-help status |
| POST | `/api/trips/{id}/disruptions` | Simulate a disruption on an item |
| GET | `/api/trips/{id}/items/{itemId}/alternatives` | Get alternatives for an affected item |
| POST | `/api/trips/{id}/items/{itemId}/alternatives/{alternativeId}/apply` | Apply an alternative |

For a check-in, send JSON such as `{ "status": "SAFE", "note": "Reached the hotel" }` or use `"NEEDS_HELP"`. To simulate a disruption, send `{ "type": "FLIGHT_CANCELLATION", "itemId": 1 }`; other types are `HOTEL_UNAVAILABLE`, `TRANSPORT_DISRUPTION`, and `SEVERE_WEATHER`.

## Current scope

This is a prototype using generated sample alternatives and simulated disruptions. The map is a schematic view of supported Indian city coordinates; it is not a live map or location tracker. Live flight/hotel/weather integrations are not implemented. Account login and optional SMTP help-request alerts are available after local configuration. On-screen messages last only for the current browser session, while the activity history is stored in MySQL. The risk level is a simple rule, not a formal safety assessment.

The frontend build and lint checks pass. A full backend run needs Java, MySQL, and Maven access to its dependencies.

## Package lockfiles

`frontend/package-lock.json` belongs to the React app and should be kept so installs use the recorded dependency versions. The `package-lock.json` in the `WT_CP` root is an empty npm lockfile with no matching root `package.json`; the project does not need it. Run npm commands from `frontend/`.
