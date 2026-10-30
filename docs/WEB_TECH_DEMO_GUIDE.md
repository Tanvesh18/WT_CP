# TripShield: 5–7 minute web technology demonstration

Use the isolated Demo Mode. Passwords are in ignored `backend/config/demo-secrets.properties`; do not show the file, passwords, cookie values, CSRF values, or tokens on a projector or in screenshots. The coordinator email and traveler emails are listed there beside their password entries.

## Start

From `backend/`, once only if the demo has not been set up: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\setup-demo.ps1`. Build with `mvn.cmd package -DskipTests`. Start the backend in a terminal with `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-demo.ps1 -Port 18084`. Run `node .\seed-demo.mjs` once to populate the scenarios. In another terminal from `frontend/`, run:

```powershell
$env:TRIPSHIELD_API_TARGET='http://127.0.0.1:18084'
npm.cmd run dev -- --host 127.0.0.1 --port 15174 --strictPort
```

Open `http://127.0.0.1:15174`. Check `http://127.0.0.1:18084/api/demo/identity` reports `DEMO` and `tripshield_demo`. If the scenarios were seeded on an earlier date, run `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\reset-demo.ps1` from `backend/` before presenting; this resets **only** `tripshield_demo` and is intentionally destructive to demo walkthrough edits. The normal profile uses the same application but a separate database; do not start it against the development schema for this demonstration.

## Walkthrough

1. **0:00–1:00 — Architecture.** Show the Coordinator login. Explain React → same-origin `/api` proxy → Spring Boot controllers → MySQL. The browser has no database access. Demo flights are explicitly simulated.
2. **1:00–2:00 — Login and cookies.** Open DevTools **Network**, enable *Preserve log*, then sign in. Select `POST /api/auth/login`. Show status 200 and the `Set-Cookie` header **without expanding or reading cookie values aloud**. In **Application → Cookies**, point out `TRIPSHIELD_SESSION` is HttpOnly, SameSite Lax, Path `/api`; `TRIPSHIELD_CSRF` is readable so the UI can send its matching header. On HTTP localhost, Secure is off; on HTTPS it is on. The JSON response contains the role and name but no browser session token.
3. **2:00–3:00 — Session persistence and protected APIs.** Refresh. The app calls `GET /api/auth/me`, resolves the server-side session row, and remains signed in. Open a trip and show `GET /api/trips`. The cookie is sent automatically. Explain that MySQL stores only a SHA-256 hash of the random session token and that the token itself is HttpOnly.
4. **3:00–4:00 — Roles and workflow.** As Coordinator, show the Action Center, a disrupted trip, recovery options, and a notification. Explain that `Access.java` checks role and trip ownership on the server. The traveler UI hides coordinator buttons, but the API check is the actual security boundary.
5. **4:00–5:00 — CSRF and logout.** In Network, show a state-changing `/api` request with `X-TripShield-CSRF` (do not display its value). Explain that a cookie-authenticated write without the header gets 403. Sign out; the server deletes the session row and clears cookies. A subsequent protected API request gets 401. Use a fresh private browser context or Network replay only if you can avoid displaying secrets.
6. **5:00–6:30 — Traveler and expiration.** Sign in with a demo Traveler account from the ignored secrets file. Show assigned trips and notifications. `GET /api/travelers` is Coordinator-only and returns 403 for the Traveler; this is verified in automated browser tests, so you can describe it without exposing credentials in a manual console command. Explain the fixed 12-hour expiry: `/api/auth/me` returns 401 after expiry, and the UI asks the user to sign in again. For a live expiration demo, use a disposable isolated test account and test database; do not edit the presentation or development database.

## Questions to be ready for

- **Is this a JWT?** No. The random token maps to a server-side `app_session` row. The browser carries it in an HttpOnly cookie; API clients can still use a bearer token. Every protected request checks the database row and expiry.
- **Why two cookies?** The session cookie authenticates and cannot be read by JavaScript. The CSRF cookie is read by the UI and echoed in a custom header; the backend checks that it matches this particular server-side session.
- **What is session fixation protection?** A new login issues a new random session token and invalidates any supplied previous session. The browser test verifies the old cookie no longer works.
- **What happens after logout?** The session row is deleted and both cookies are cleared. Reusing the old cookie gives 401.
- **Why is Secure off locally?** The demo uses plain HTTP localhost. The backend sets Secure when it sees HTTPS. Behind a trusted TLS proxy, configure forwarded-header handling correctly.
- **How is XSS handled?** React escapes rendered text and the session cookie is HttpOnly. A full deployment CSP and security review remain future hardening work.
- **Are DEMO flights real?** No. They are labeled planning schedules, not airline availability or issued tickets.
