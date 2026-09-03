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
- The admin dashboard (`login.html`, `dashboard.html`, `hall.html`, `super-admin.html`) supports English and Russian, via a language `<select>` in the sidebar (login page: top of the card, since it has no sidebar). The choice is a per-browser preference — not tied to any admin account field — persisted in `localStorage` (`js/admin-i18n.js`) and applied on every admin page load; switching it reloads the page rather than trying to live-re-render every dynamically-built table/modal. Error messages returned by the API (validation failures, "Guest not found", etc.) are generated server-side in English and are **not** translated by this — that would need the backend to negotiate language itself, a separate change.
- New guests default to the admin's own current dashboard language (not always English) in the guest editor's "Invitation language" picker — an admin working in Russian is presumably inviting Russian-speaking guests. It's still just a default; pick a different one per guest (including Uzbek, which isn't an admin UI language) as needed.
- `invitation.html` supports Russian, Uzbek, and English — the three languages guests at this wedding actually speak. All of its static copy (fixed date/venue text, schedule, contacts, etc. — none of it guest-specific) lives in `js/i18n.js`, keyed by `data-i18n` attributes in the markup; `js/invitation.js` picks the dictionary from the guest's own `language` field (set by the admin in the guest editor) once that's fetched, and falls back to a browser-language guess for the loading/invalid-link screens shown before that's known. Guest-entered content (display name, custom greeting message) is never translated — it's shown back exactly as the admin typed it.
