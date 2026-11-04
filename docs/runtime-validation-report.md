# Runtime Validation Report

**Generated:** 2026-11-04  
**Target:** `C:\Users\tanve\.vscode\WebTechLab\WT_CP`

## Summary

| Step | Status | Exit code | Details |
| --- | --- | --- | --- |
| Isolated backend startup | PASS | 0 | Spring Boot test profile, `tripshield_test`, `/api/health` returned 200 |
| Demo backend startup | PASS | 0 | Spring Boot demo profile, `tripshield_demo`, `/api/demo/identity` returned `DEMO` |
| Backend integration tests | PASS | 0 | 49 tests, 0 failures |
| Frontend unit tests | PASS | 0 | 12 tests, 0 failures |
| Frontend lint | PASS | 0 | ESLint completed cleanly |
| Frontend production build | PASS | 0 | TypeScript and Vite build completed |
| Normal/test Playwright suite | PASS | 0 | 4 passed, 1 intentional demo-only skip |
| Demo Playwright walkthrough | PASS | 0 | 1 passed |
| Final demo reset | PASS | 0 | Four original relative-date scenarios restored |

**Overall:** PASS for the implemented isolated/demo workflows.

## Capability evidence

- **Docker:** unavailable; the Docker CLI was not installed.
- **Node.js:** available (`v22.12.0`).
- **Playwright Chromium:** installed successfully and used headlessly.
- **Infrastructure fallback:** repository-provided isolated MySQL schemas and scripts were used; the development schema was not used.
- **Browser tier:** Playwright remained primary because Node.js and Chromium were available.

## Browser journeys

- Coordinator login, trip creation, flight search, hotel and transport selection, timeline, route map, responsive layout, disruption simulation, recommendation selection, replacement confirmation, and persistence.
- Traveler registration/login, refresh persistence, itinerary viewing, notification inbox, help request, responsive layout, logout, and coordinator-only action restrictions.
- Session rotation, old-session invalidation, HttpOnly/SameSite cookie assertions, no Web Storage bearer token, CSRF rejection, and role API restrictions.
- Seeded demo scenarios for flight cancellation, transport disruption, hotel recovery, severe-weather connected itinerary recovery, duplicate replacement rejection, and direct database status verification.
- Flight search loading/error/retry behavior and weather-alert rendering with controlled external-service fixtures.

## Known gaps

Live Duffel availability, real SMTP delivery, production TLS/CSP configuration, and the unavailable Docker-based infrastructure path were not tested. These do not reduce the isolated browser/API verdict, but require separate staging validation before production deployment.
