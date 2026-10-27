# TripShield

TripShield is a college project for corporate travel planning and disruption response. Coordinators plan itineraries and review traveler requests. Travelers request trips, see assigned itineraries, check in, and receive in-app notifications. Flight results come from Duffel when available, or from clearly labeled offline demo schedules. Hotel and transport choices are planning estimates. Disruptions are simulated; saving an offer does not issue a ticket, reserve a room, or charge a payment.

The interface uses a shared responsive visual system across both roles. The coordinator overview prioritizes trips that need action; the traveler workspace shows trip status and an expandable notification inbox. In recovery, the recommendation is computed from the returned options by fewest downstream itinerary conflicts, then arrival timing and price. Coordinators review the affected item and proposed replacement before confirming an itinerary change. The recommendation and comparison use application data; they are not live booking promises.

## Stack and startup

- React 19, TypeScript, Vite, Leaflet in `frontend/`
- Java 21+, Spring Boot 4, JPA, Flyway in `backend/`
- MySQL 8 for trips, accounts, sessions, invitations, notifications, outbox jobs, and history

Create a development MySQL schema named `tripshield` if needed. Copy `backend/config/local.properties.example` to ignored `backend/config/local.properties`; set the MySQL connection and coordinator bootstrap credentials. Duffel and SMTP are optional. Keep secrets out of Git. With no active profile specified, Spring Boot defaults to **local** and loads this file. Back up an existing development schema before starting the local backend; normal startup may apply pending Flyway migrations.

In separate PowerShell terminals:

```powershell
cd backend
mvn.cmd spring-boot:run
```

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Open the Vite URL (normally `http://localhost:5173`). Its `/api` proxy points to `http://127.0.0.1:8080` by default. Set `TRIPSHIELD_API_TARGET` before starting Vite to use another backend port. `GET /api/health` checks backend readiness. Use `npm.cmd` when PowerShell blocks `npm.ps1`.

For the final college presentation, use the dedicated `tripshield_demo` schema and commands below. The older `tripshield_test` startup remains available for automated test work; it is separate from the presentation dataset. Neither workflow needs the development `tripshield` schema.

## Final college demonstration

The setup script creates only `tripshield_demo` and a restricted MySQL user for that schema. It uses the existing ignored local MySQL administrator configuration, generates new demo-only secrets in ignored `backend/config/demo-secrets.properties`, and refuses to take over an unknown existing demo schema. It does not query, migrate, or change the development database.

From `backend/`, run setup once, build the jar, then start the demo server:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\setup-demo.ps1
mvn.cmd package -DskipTests
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-demo.ps1 -Port 18084
```

In a second terminal, seed the four connected scenarios. Running this seed again on the same day is idempotent and preserves edits made during a walkthrough. Use the reset command below to restore the initial presentation state.

```powershell
cd backend
node .\seed-demo.mjs
```

In a third terminal, start the frontend:

```powershell
cd frontend
$env:TRIPSHIELD_API_TARGET='http://127.0.0.1:18084'
npm.cmd run dev -- --host 127.0.0.1 --port 15174 --strictPort
```

Open `http://127.0.0.1:15174`. The demo coordinator is `coordinator@demo.tripshield.test`. Traveler accounts are `maya@demo.tripshield.test`, `arjun@demo.tripshield.test`, and `nisha@demo.tripshield.test`. The coordinator and traveler passwords are stored only in ignored `backend/config/demo-secrets.properties`; open that file locally to sign in. Do not put the passwords in screenshots or terminal logs. The demo runs without Duffel or SMTP credentials. `GET http://127.0.0.1:18084/api/demo/identity` must return `DEMO` and `tripshield_demo` before seeding or resetting.

The starting state has four fictional itineraries, all dated relative to the reset day:

| Scenario | Traveler and route | Initial state |
| --- | --- | --- |
| A | Maya Rao, Mumbai–London | International flight via Dubai cancelled; airport car and hotel at risk; three or more replacement schedules available. Includes a London–Mumbai return flight. |
| B | Arjun Mehta, Pune–New Delhi | Severe weather flight, transfer, and stay recovered; the updated itinerary is on track. |
| C | Nisha Patel, New Delhi–Dubai | Hotel disruption recovered and traveler notified. |
| D | Nisha Patel, Bengaluru–Hyderabad | Ground transfer disrupted; hotel connection at risk; recovery options ready. |

To return **only the dedicated demo dataset** to its initial state, keep the demo backend running and execute:

```powershell
cd backend
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\reset-demo.ps1
```

Reset verifies the local demo API identity and restricted MySQL connection before deleting and reseeding rows in `tripshield_demo`. It never targets `tripshield` or `tripshield_test`. Flyway tables are retained. Reset invalidates current demo sessions, so sign in again. Sample flights and prices are labeled DEMO planning schedules, and hotel/transport options are simulated; no ticket, room, or vehicle is booked.

The seeded browser walkthrough can be repeated from `frontend/` with `$env:DEMO_E2E='1'; $env:E2E_BASE_URL='http://127.0.0.1:15174'; npm.cmd run test:e2e -- e2e/seeded-demo.spec.ts`. It reads demo passwords from the ignored file, creates extra browser-test trips, and changes recovery state. Run `reset-demo.ps1` afterward. Install Playwright Chromium if the browser is missing.

## Roles and workflows

**Coordinator:** sign in with the configured bootstrap account; create a trip for a registered traveler or guest; approve or reject requests; edit or cancel active trips; simulate disruptions; refresh and apply valid recovery options; inspect risk, budget totals, check-ins, notifications, and history. For a guest trip, issue a single-use invitation and share its code privately with the named traveler. A matching email alone cannot claim the trip.

**Traveler:** register, sign in, request a trip, claim an invited guest itinerary with its code, see assigned trips, check in as safe or needing help, and read notifications. Sessions use hashed bearer tokens in MySQL and expire after 12 hours or logout. Registration does not verify mailbox ownership; a private invitation code is required for an existing guest trip.

The trip wizard provides searchable airports, flight cards with price, timing, duration, stops and filters, optional sample hotel/transfer segments, a budget input, itinerary review, and route preview. Flight offers are revalidated on first save and before approval or recovery application. Expired or changed offers require a fresh search. Recovery rankings use cost, timing, and connection risk heuristics. Currencies are totaled separately without conversion. Route maps use OpenStreetMap tiles and bundled OurAirports coordinates; airport markers and route lines still appear when tiles are unavailable.

### Offline flight demo

When `tripshield.duffel.token` is empty, flight search automatically uses deterministic **DEMO** schedules and estimated prices. If a configured Duffel search is unavailable, new searches also fall back to DEMO results; existing Duffel offers still require Duffel revalidation. Set `tripshield.demo.flights=true` in local properties to force the offline demo even when a token is configured. Every card, itinerary item, and recovery option identifies its source. DEMO schedules are examples, not airline inventory or confirmed availability.

Demo routes run in both directions: PNQ–DEL, PNQ–BOM, BOM–DEL, BLR–DEL, BLR–HYD, HYD–DEL, BOM–DXB, DEL–DXB, BOM–LHR, and DEL–LHR. Each supported route offers several daily nonstop schedules using airport time zones and estimated fares. An unsupported route returns no invented flights; choose a listed route for the demonstration. Flight cancellation and severe weather can produce replacement schedules, including the next day when the trip window permits. Hotel and transport replacements remain simulated planning choices. No Duffel or SMTP credentials are needed for this workflow.

Help requests create a durable email outbox entry in the same transaction as the check-in. A worker sends configured SMTP alerts after commit with retries. Without SMTP, check-in remains saved and the outbox entry is skipped. In-app traveler notifications persist for approvals, rejections, cancellations, disruptions, and itinerary changes.

## API

All endpoints except registration and login use `Authorization: Bearer <token>`. `POST /api/trips` requires a unique `Idempotency-Key` header. An identical retry returns the existing trip; reuse with different details returns 409.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/auth/register`, `/api/auth/login`, `/api/auth/logout` | Account and session |
| GET | `/api/auth/me` | Current session |
| POST | `/api/auth/claim-trip` | Claim guest trip with invitation code |
| GET | `/api/travelers` | Coordinator traveler directory |
| POST | `/api/flights/search` | Duffel economy offers or labeled offline demo schedules |
| GET, POST | `/api/trips` | Visible trips; create/request with idempotency key |
| GET, PUT | `/api/trips/{id}` | View or coordinator edit |
| GET | `/api/trips/{id}/history` | Actor-aware activity history |
| POST | `/api/trips/{id}/invitation` | Coordinator issues private code |
| POST | `/api/trips/{id}/approve`, `/reject`, `/cancel` | Review and lifecycle |
| POST | `/api/trips/{id}/check-in` | Assigned traveler or coordinator check-in |
| POST | `/api/trips/{id}/disruptions` | Coordinator simulation |
| GET | `/api/trips/{id}/items/{itemId}/alternatives` | Recovery choices |
| POST | `/api/trips/{id}/items/{itemId}/alternatives/refresh` | Refresh disrupted flight replacements |
| POST | `/api/trips/{id}/items/{itemId}/alternatives/{alternativeId}/apply` | Apply recovery to itinerary |
| GET | `/api/notifications` | Own inbox and unread count |
| POST | `/api/notifications/{id}/read` | Mark own notification read |

Creation accepts optional `travelerUserId` for coordinator assignment, `estimatedBudget` and `budgetCurrency`; non-flight items accept `estimatedCost` and `costCurrency`. Amounts must be nonnegative and currencies use three uppercase letters. Responses group totals by currency and include `budgetExceeded`. Rejection accepts an optional JSON `comment`. New history events include actor ID, role, timestamp, action, and structured `changeJson`; legacy events retain their original fields.

## Migrations and tests

`V1__legacy_schema.sql` creates legacy tables in a fresh schema. Existing nonempty schemas are baselined at V1; `V2__secure_workflows.sql` adds ownership, budget, audit, invitation, idempotency, notification, and outbox structures without deleting legacy rows. Fresh-schema migration is verified on `tripshield_test`; development-row preservation has not been independently compared before and after. See `docs/TRIPSHIELD_AUDIT.md` for the recorded development-schema startup incident.

Backend integration tests are pinned to `jdbc:mysql://127.0.0.1:3306/tripshield_test` as `tripshield_test_app` with grants only on that schema. Store its password in ignored `backend/config/test-secrets.properties`. Maven tests activate **test** explicitly, and a test-classpath startup guard rejects Spring Boot test contexts unless **test** is the only active profile and the datasource URL and user match the isolated schema. The guard runs before database beans initialize. The application also guards explicit test-profile launches. Duffel and SMTP are mocked in automated tests; offline demo flight search and recovery are exercised without a provider. `mvn.cmd test` changes only the isolated test schema; it never needs the development schema.

The integration suite deletes rows in `tripshield_test` during setup. Back up and restore that schema around the suite if it contains records you need to preserve. The presentation dataset in `tripshield_demo` is unaffected.

```powershell
cd backend
mvn.cmd test
cd ../frontend
npm.cmd run test
npm.cmd run lint
npm.cmd run build
```

Playwright tests use synthetic accounts and the isolated `tripshield_test` schema. After `mvn.cmd package -DskipTests`, run `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-isolated-demo.ps1` from `backend/` in one terminal. It reads the ignored test database secret, starts on port 18083, and forces DEMO flights. In another terminal, start Vite with `$env:TRIPSHIELD_API_TARGET='http://127.0.0.1:18083'; npm.cmd run dev -- --host 127.0.0.1 --port 15174 --strictPort`. Then from `frontend/` run `$env:E2E_BASE_URL='http://127.0.0.1:15174'; npm.cmd run test:e2e`. Install Playwright Chromium with `npx.cmd playwright install chromium` if it is not already present. The browser test creates synthetic rows in `tripshield_test` and does not access `tripshield`.

No real payment, ticket issuance, hotel reservation, live disruption feed, mailbox verification delivery, or production load test is included. Automated browser journeys cover the offline demo on desktop and mobile; live Duffel and SMTP behavior remain unverified.
