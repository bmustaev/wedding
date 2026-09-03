# Wedding Invitation Platform — Frontend

Plain HTML/CSS/JS, no framework, no build step. Talks to the Spring Boot backend documented in `API.md`.

## Pages

| File | Who | What |
|---|---|---|
| `login.html` | Admin / super admin | Sign in |
| `dashboard.html` | Admin | Manage own guests, seating, bulk import |
| `super-admin.html` | Super admin only | Manage admin accounts, view (read-only) any admin's guests |
| `invitation.html` | Guest, no login | View invitation, upload photos/videos |

## Running it locally

This uses ES modules (`<script type="module">`), which browsers block from `file://` URLs — you need to serve it over HTTP. Two options:

**Option A — quick local server, separate from the backend:**
```bash
cd wedding-frontend
python3 -m http.server 5500
```
Then open `http://localhost:5500/login.html`. **You'll need to enable CORS on the backend** for this to work, since the frontend (port 5500) and API (port 8080) are different origins — not covered by this repo.

**Option B — same-origin as the backend (recommended, zero CORS setup):**
Copy everything in this folder into the Spring Boot project's `src/main/resources/static/` directory. Spring Boot serves static files automatically, so once the backend is running, everything is reachable at `http://localhost:8080/login.html`, `http://localhost:8080/dashboard.html`, etc. — same origin as `/api/**`, so `fetch()` calls need no CORS configuration at all. This is what the code assumes (all API calls use relative paths like `/api/guests`).

## The invitation-link decision

The backend's `app.invitation.base-url` setting controls what link an admin sees/copies for a guest (`GuestResponse.invitationUrl`). This frontend reads the guest's slug from a **query string** (`invitation.html?slug=...`), not a path segment, because a static file server has no way to route an arbitrary path like `/i/{slug}` to `invitation.html` without a server-side rewrite rule.

**Set this on the backend to match:**
```yaml
app:
  invitation:
    base-url: http://localhost:8080/invitation.html?slug=
```
(or whatever host you actually deploy to). With that, `GuestResponse.invitationUrl` will already be a complete, correct, clickable link — the frontend doesn't reconstruct it from the slug anywhere except reading it back out of its own query string on `invitation.html`.

If you'd rather have the prettier `/i/{slug}` path, that requires one small addition on the backend (a controller forwarding `/i/{slug}` to `invitation.html` with the slug attached), which is out of scope here.

## Auth

The JWT is stored in `localStorage` (see `js/auth.js`) after login and attached as `Authorization: Bearer <token>` to every request except `/api/auth/login` and anything under `/api/public/`. A `401` response anywhere clears the session and redirects to `login.html`.

This is simple but is vulnerable to XSS (any injected script can read `localStorage`). For a production deployment handling real guest data, consider having the backend issue the token as an `HttpOnly` cookie instead — that's a backend change, not something fixable purely in this frontend.

## What isn't covered

- No client-side form validation beyond what's needed to avoid obviously-bad requests (required fields, min length) — the backend's validation (`400` responses with `details[]`) is the source of truth and is what's actually displayed on error.
- No automated tests.
- No i18n/translation of the UI chrome itself — only the guest-entered `language` field is stored and displayed back as-is; the admin dashboard and invitation page copy are English only.
