# TextPort MVP

Production-oriented MVP for transparent SMS sync from Android to user account backend.

## Compliance posture
- Explicit onboarding disclosure and opt-in consent required.
- No stealth or hidden background capture without user enablement.
- In-app privacy policy.
- Pause/resume sync controls.
- Export account data.
- Delete account and synced data.
- Play policy design target: SMS Backup/Restore declaration path (or default SMS app path).

## Android app
- Module: `app/`
- Runtime permissions: `RECEIVE_SMS`, `READ_SMS`
- Core flow:
  - User consents in UI.
  - User grants SMS permissions.
  - User authenticates to backend.
  - `SmsReceiver` enqueues `WorkManager` upload.
  - User can pause/resume/export/delete.

## Backend API
- Module: `backend/`
- Stack: Node.js, Express, SQLite, JWT
- Security baseline: Helmet, CORS allowlist, rate limit, auth middleware
- Endpoints:
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `POST /api/messages/sync`
  - `GET /api/messages`
  - `POST /api/account/pause`
  - `POST /api/account/resume`
  - `GET /api/account/export`
  - `POST /api/account/delete`

## Web dashboard
- Module: `dashboard/`
- Plain HTML/JS management console for MVP validation and ops.

## Run locally
1. `docker compose up`
2. Open dashboard: `http://localhost:3000`
3. Backend API: `http://localhost:8080/api/health`

## Hardening before production launch
1. Move SQLite to managed Postgres with encrypted storage.
2. Add TLS, HSTS, managed secret store, key rotation.
3. Add audit logging and per-device token revocation.
4. Add stronger PII controls and retention policy options.
5. Add CI tests, Android instrumentation tests, and threat model review.
