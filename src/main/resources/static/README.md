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

The backend's `app.invitation.base-url` setting controls what link an admin sees/copies for a guest (`GuestResponse.invitationUrl`). The invitation page accepts the guest's slug in **either** of two forms:

- `invitation.html?slug=...` — query string, works on any static file server
- `/i/{slug}` — the pretty path, served by `InvitationRedirectController` on the backend, which internally forwards the request to `invitation.html` (the browser's address bar keeps showing `/i/{slug}`, and the page's JS extracts the slug from the path)

Because the page can be served at `/i/{slug}`, its CSS/JS references in `invitation.html` are **root-absolute** (`/css/...`, `/js/...`) — relative paths would resolve under `/i/` and 404. Keep any new asset references on this page root-absolute too.

**Set this on the backend to match** (no trailing slash — the backend appends `/{slug}` itself):
```yaml
app:
  invitation:
    base-url: http://localhost:8080/i
```
(or whatever host you actually deploy to). With that, `GuestResponse.invitationUrl` will already be a complete, correct, clickable link — the frontend doesn't reconstruct it from the slug anywhere except reading it back out of its own URL on the invitation page.

## Auth

The JWT is stored in `localStorage` (see `js/auth.js`) after login and attached as `Authorization: Bearer <token>` to every request except `/api/auth/login` and anything under `/api/public/`. A `401` response anywhere clears the session and redirects to `login.html`.

This is simple but is vulnerable to XSS (any injected script can read `localStorage`). For a production deployment handling real guest data, consider having the backend issue the token as an `HttpOnly` cookie instead — that's a backend change, not something fixable purely in this frontend.

## What isn't covered

- No client-side form validation beyond what's needed to avoid obviously-bad requests (required fields, min length) — the backend's validation (`400` responses with `details[]`) is the source of truth and is what's actually displayed on error.
- No automated tests.
- No i18n/translation of the UI chrome itself — only the guest-entered `language` field is stored and displayed back as-is. The admin dashboard is English only; `invitation.html` is hardcoded in Russian to match this deployment's actual wedding (fixed date/venue copy lives inline in the page, guest name/table/greeting come from the API).
