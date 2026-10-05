# TripShield

A prototype for corporate trip planning and disruption recovery. The React frontend calls a Spring Boot API, which stores trips and sample alternatives in MySQL.

## Run locally

1. Create a MySQL database named `tripshield`.
2. Set `DB_USER` and `DB_PASSWORD` in your terminal. Set `DB_URL` too if MySQL is not at `localhost:3306/tripshield`.
3. From `backend`, run `mvn spring-boot:run` with Java 21 or newer.
4. From `frontend`, run `npm install` and `npm run dev`.
5. Open the URL printed by Vite. Its `/api` requests are proxied to port 8080.

In PowerShell, for example: `$env:DB_USER='root'` and `$env:DB_PASSWORD='your-password'`. These values apply only to that terminal session. Do not commit passwords.

## Demo flow

Create a trip with one or more flight, hotel, or transport items. Open the trip and simulate a disruption on an item. The API marks that item as affected and returns sample alternatives stored in MySQL. Select an alternative to add a replacement item and mark the old one as replaced. The trip list highlights trips that still need attention.

Sample alternatives are seeded automatically when the alternatives table is empty. They are generic demo options, not live bookings or checked for real availability. The listed costs are sample estimates.

## API

- `GET /api/health`
- `GET /api/trips`
- `POST /api/trips`
- `GET /api/trips/{id}`
- `POST /api/trips/{id}/disruptions`
- `GET /api/trips/{id}/items/{itemId}/alternatives`
- `POST /api/trips/{id}/items/{itemId}/alternatives/{alternativeId}/apply`
