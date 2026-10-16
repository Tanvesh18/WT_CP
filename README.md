# TripShield

TripShield is a college project for corporate travel planning and disruption response. Coordinators plan itineraries and review traveler requests. Travelers request trips, see assigned itineraries, check in, and receive in-app notifications. Flight results come from Duffel when configured. Hotel and transport choices are planning estimates. Disruptions are simulated; saving an offer does not issue a ticket, reserve a room, or charge a payment.

## Stack and startup

- React 19, TypeScript, Vite, Leaflet in `frontend/`
- Java 21+, Spring Boot 4, JPA, Flyway in `backend/`
- MySQL 8 for trips, accounts, sessions, invitations, notifications, outbox jobs, and history

Create a development MySQL schema named `tripshield` if needed. Copy `backend/config/local.properties.example` to ignored `backend/config/local.properties`; set the MySQL connection, coordinator bootstrap credentials, and optional Duffel/SMTP settings. Keep secrets out of Git. The **local** profile loads this file; the default profile has no datasource. Back up an existing schema before its first migration.

In separate PowerShell terminals:

```powershell
cd backend
mvn.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Open the Vite URL (normally `http://localhost:5173`). Its `/api` proxy points to `http://127.0.0.1:8080` by default. Set `TRIPSHIELD_API_TARGET` before starting Vite to use another backend port. `GET /api/health` checks backend readiness. Use `npm.cmd` when PowerShell blocks `npm.ps1`.

## Roles and workflows

**Coordinator:** sign in with the configured bootstrap account; create a trip for a registered traveler or guest; approve or reject requests; edit or cancel active trips; simulate disruptions; refresh and apply valid recovery options; inspect risk, budget totals, check-ins, notifications, and history. For a guest trip, issue a single-use invitation and share its code privately with the named traveler. A matching email alone cannot claim the trip.

**Traveler:** register, sign in, request a trip, claim an invited guest itinerary with its code, see assigned trips, check in as safe or needing help, and read notifications. Sessions use hashed bearer tokens in MySQL and expire after 12 hours or logout. Registration does not verify mailbox ownership; a private invitation code is required for an existing guest trip.

The trip wizard provides searchable airports, Duffel flight cards with price, timing, duration, stops and filters, optional sample hotel/transfer segments, a budget input, itinerary review, and route preview. Flight offers are revalidated on first save and before approval or recovery application. Expired or changed offers require a fresh search. Recovery rankings use cost, timing, and connection risk heuristics. Currencies are totaled separately without conversion. Route maps use OpenStreetMap tiles and bundled OurAirports coordinates; tile access needs a network connection.

Help requests create a durable email outbox entry in the same transaction as the check-in. A worker sends configured SMTP alerts after commit with retries. Without SMTP, check-in remains saved and the outbox entry is skipped. In-app traveler notifications persist for approvals, rejections, cancellations, disruptions, and itinerary changes.

## API

All endpoints except registration and login use `Authorization: Bearer <token>`. `POST /api/trips` requires a unique `Idempotency-Key` header. An identical retry returns the existing trip; reuse with different details returns 409.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/auth/register`, `/api/auth/login`, `/api/auth/logout` | Account and session |
| GET | `/api/auth/me` | Current session |
| POST | `/api/auth/claim-trip` | Claim guest trip with invitation code |
| GET | `/api/travelers` | Coordinator traveler directory |
| POST | `/api/flights/search` | Duffel economy offer search |
| GET, POST | `/api/trips` | Visible trips; create/request with idempotency key |
| GET, PUT | `/api/trips/{id}` | View or coordinator edit |
| GET | `/api/trips/{id}/history` | Actor-aware activity history |
| POST | `/api/trips/{id}/invitation` | Coordinator issues private code |
| POST | `/api/trips/{id}/approve`, `/reject`, `/cancel` | Review and lifecycle |
| POST | `/api/trips/{id}/check-in` | Assigned traveler or coordinator check-in |
| POST | `/api/trips/{id}/disruptions` | Coordinator simulation |
| GET | `/api/trips/{id}/items/{itemId}/alternatives` | Recovery choices |
| POST | `/api/trips/{id}/items/{itemId}/alternatives/refresh` | Refresh Duffel replacements |
| POST | `/api/trips/{id}/items/{itemId}/alternatives/{alternativeId}/apply` | Apply recovery to itinerary |
| GET | `/api/notifications` | Own inbox and unread count |
| POST | `/api/notifications/{id}/read` | Mark own notification read |

Creation accepts optional `travelerUserId` for coordinator assignment, `estimatedBudget` and `budgetCurrency`; non-flight items accept `estimatedCost` and `costCurrency`. Amounts must be nonnegative and currencies use three uppercase letters. Responses group totals by currency and include `budgetExceeded`. Rejection accepts an optional JSON `comment`. New history events include actor ID, role, timestamp, action, and structured `changeJson`; legacy events retain their original fields.

## Migrations and tests

`V1__legacy_schema.sql` creates legacy tables in a fresh schema. Existing nonempty schemas are baselined at V1; `V2__secure_workflows.sql` adds ownership, budget, audit, invitation, idempotency, notification, and outbox structures without deleting legacy rows. Fresh-schema migration is verified on `tripshield_test`; development-row preservation has not been independently compared before and after. See `docs/TRIPSHIELD_AUDIT.md` for the recorded development-schema startup incident.

Backend integration tests are pinned to `jdbc:mysql://127.0.0.1:3306/tripshield_test` as `tripshield_test_app` with grants only on that schema. Store its password in ignored `backend/config/test-secrets.properties`. The test startup guard rejects another URL or user. Duffel and SMTP are mocked in automated tests.

```powershell
cd backend
mvn.cmd test
cd ../frontend
npm.cmd run test
npm.cmd run lint
npm.cmd run build
```

No real payment, ticket issuance, hotel reservation, live disruption feed, mailbox verification delivery, or production load test is included. Browser acceptance and live Duffel/SMTP behavior remain unverified.
