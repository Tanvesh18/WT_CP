# Run TripShield (Windows PowerShell)

Run the backend and frontend in **separate PowerShell terminals**. Both modes use the same application. Demo Mode uses the isolated `tripshield_demo` database and labeled sample flights; Normal Mode uses your configured `tripshield` database and Duffel when available.

## Demo Mode with presentation data

**First-time setup only**, from the project root:

```powershell
cd backend
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\setup-demo.ps1
mvn.cmd package -DskipTests
```

**Terminal 1 — backend**, from the project root:

```powershell
cd backend
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-demo.ps1 -Port 18084
```

**Populate the demo data** after the backend starts, in another terminal from the project root:

```powershell
cd backend
node .\seed-demo.mjs
```

The seed command is repeatable. It preserves existing demo edits. To return only `tripshield_demo` to the initial presentation state, keep the demo backend running and use `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\reset-demo.ps1` from `backend/`. This deletes and recreates **demo records only**; it does not touch the normal database. Run reset on presentation day if the seeded trip dates are old.

**Terminal 2 — frontend**, from the project root:

```powershell
cd frontend
npm.cmd ci
$env:TRIPSHIELD_API_TARGET='http://127.0.0.1:18084'
npm.cmd run dev -- --host 127.0.0.1 --port 15174 --strictPort
```

Open **http://127.0.0.1:15174**. Sign in with the demo Coordinator or Traveler accounts. Their emails and passwords are in the ignored local file `backend/config/demo-secrets.properties`; do not commit or share it. The demo works without Duffel or SMTP credentials. You can confirm the backend mode at **http://127.0.0.1:18084/api/demo/identity**; it should say `DEMO` and `tripshield_demo`.

## Normal Mode (your development data)

Before starting, make sure MySQL is running and `backend/config/local.properties` exists. If it does not, copy `backend/config/local.properties.example` and fill in your own MySQL connection and Coordinator bootstrap credentials. Create the `tripshield` schema if needed. **Back up an existing development database first:** normal startup can apply pending Flyway migrations. Normal Mode does not seed demo accounts or demo trips.

**Terminal 1 — backend**, from the project root:

```powershell
cd backend
mvn.cmd spring-boot:run
```

**Terminal 2 — frontend**, from the project root:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Open the URL printed by Vite (normally **http://localhost:5173**). The backend uses **http://127.0.0.1:8080** by default. Check **http://127.0.0.1:8080/api/health** if startup fails. Log in with the Coordinator credentials configured in `backend/config/local.properties`, or register a Traveler account in the UI.

Normal Mode uses Duffel only when its token is configured. Without a token, or when a search fails, the existing fallback returns clearly labeled **DEMO planning schedules**. Those schedules are not live availability or bookings. SMTP is optional; in-app notifications still work without it.

Stop either mode with **Ctrl+C** in each terminal. For architecture and a presentation walkthrough, see [the system design](docs/WEB_TECH_SYSTEM_DESIGN.md) and [demo guide](docs/WEB_TECH_DEMO_GUIDE.md).
