# TripShield

TripShield is a course project prototype for managing corporate trips and responding to travel disruptions. An employee or travel coordinator can create an itinerary, simulate a problem, review sample alternatives, and update the trip.

## Technology

- **Frontend:** React, TypeScript, and Vite (`frontend/`)
- **Backend:** Java, Spring Boot, REST APIs, and JPA/Hibernate (`backend/`)
- **Database:** MySQL

The frontend sends requests to the backend under `/api`. Vite forwards those requests to `http://localhost:8080` during local development. The backend stores trips, itinerary items, and sample alternatives in MySQL.

## Implemented features

- Create a trip with a traveler, origin, destination, dates, and flight, hotel, or transport items.
- View all trips and the selected trip's itinerary in time order.
- Highlight trips with an affected itinerary item in the coordinator-style trip list.
- Simulate a flight cancellation, hotel unavailability, transport issue, or severe weather on an itinerary item.
- See which item is affected and compare sample alternatives by delay and estimated cost.
- Choose an alternative to add a replacement item, mark the old item as replaced, and see an on-screen update message.

The alternatives are automatically added to an empty alternatives table. They are generic demonstration data. They do not represent actual available bookings or live prices.

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

### Terminal 1: backend

```powershell
cd backend
$env:DB_USER = "root"
$env:DB_PASSWORD = "your-mysql-password"
mvn.cmd spring-boot:run
```

The default database address is `jdbc:mysql://localhost:3306/tripshield`. If yours differs, set `$env:DB_URL` before starting Maven. Keep the terminal open. To check the backend, visit `http://localhost:8080/api/health`.

### Terminal 2: frontend

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

Open the local URL printed by Vite, usually `http://localhost:5173`. Keep this terminal open too. On PowerShell, use `npm.cmd` if the `npm.ps1` script is blocked by the execution policy.

Database credentials are read from environment variables in the backend terminal. Do not put a real password in `application.properties` or commit it to Git.

## Try the demo

1. Create a trip and add at least one itinerary item. Item start and end times must fall within the trip dates.
2. Select the trip from the left-hand list.
3. Simulate a disruption on a confirmed item. The trip is marked **Needs attention**.
4. Review the suggested alternatives and select one. The replacement is added to the itinerary and the trip returns to **On track** when no affected items remain.

## API endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/health` | Check that the backend is running |
| GET | `/api/trips` | List trips |
| POST | `/api/trips` | Create a trip |
| GET | `/api/trips/{id}` | Get one trip |
| POST | `/api/trips/{id}/disruptions` | Simulate a disruption on an item |
| GET | `/api/trips/{id}/items/{itemId}/alternatives` | Get alternatives for an affected item |
| POST | `/api/trips/{id}/items/{itemId}/alternatives/{alternativeId}/apply` | Apply an alternative |

## Current scope

This is a prototype using seeded alternatives and simulated disruptions. An interactive map, live flight/hotel/weather integrations, user accounts, and persistent notification delivery are not implemented. The update message is shown in the current browser session.

The frontend build and lint checks pass. A full backend run depends on a working local Java installation and MySQL connection.

## Package lockfiles

`frontend/package-lock.json` belongs to the React app and should be kept so installs use the recorded dependency versions. The `package-lock.json` in the `WT_CP` root is an empty npm lockfile with no matching root `package.json`; the project does not need it. Run npm commands from `frontend/`.
