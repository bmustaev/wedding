# Wedding Invitation Platform — Frontend

Plain HTML/CSS/JS, no framework, no build step. Talks to the Spring Boot backend documented in `API.md`.

## Pages

| File | Who | What |
|---|---|---|
| `login.html` | Admin / super admin | Sign in |
| `dashboard.html` | Admin | Manage own guests, seating, bulk import |
| `hall.html` | Admin | Drag-and-drop seating map, one hall at a time (Tashkent / Samarkand switcher for the groom side and super admin) |
| `media-admin.html` | Admin | Every photo/video of the admin's guests in one hall, grouped by table: view, download the original, delete |
| `activities.html` | Admin | Quests (`#quests`, each with its ducat reward and how many guests were paid) and bets (`#bets`) of one hall: create, hide, close, mark the right answer; the bets leaderboard |
| `super-admin.html` | Super admin only | Manage admin accounts, view (read-only) any admin's guests, manage the invitation page's "about us" gallery |
| `invitation.html` | Guest, no login | View invitation. Its photo/video buttons open `media.html` (`#photos` / `#videos`), plus a link to everyone's photos (`#feed`) |
| `media.html` | Guest, no login | Upload and manage own photos (max 15) and videos (max 4, ≤ 60 s each); browse every guest's photos in the same hall, by table |
| `quests.html` | Guest, no login | The hall's photo challenges; a quest is done by uploading a photo for it. Each one earns ducats: the guest's wallet (earned / collected / waiting), each quest Open → Done → Paid, and — while ducats wait — a QR code for the bank plus a "Banker PIN" dialog. Polls every few seconds then, so a banker's scan flips the quests to Paid |
| `bets.html` | Guest, no login | The hall's predictions: pick while open, see results and the leaderboard once settled |
| `playlist.html` | Guest, no login | The band's songs by language (chips) and artist: like songs (the two most-liked are played once likes close, 20:50), buy one for ducats — the order waits until the guest pays the DJ |
| `bank.html` | Admin or banker | The bank table of one hall: a scanned guest QR (`bank.html?code=…`) pays out at once and shows, big, the ducats to hand over; totals, the banker's own PIN, and the payout log (refreshes every 15 s). A banker login lands here and can't open any other admin page. Signed out, it goes through `login.html?next=bank.html` and keeps the scanned code (sessionStorage) |
| `dj.html` | Admin or DJ | The hall's playlist: the DJ board (`#board` — tonight's two, orders waiting for payment, the band's queue, likes ranking, when likes close; refreshes every 10 s) and the repertoire (`#songs`). A DJ login lands here and can't open any other admin page |

## Guest menu

Every guest page (`invitation.html`, `media.html`, `quests.html`, `bets.html`, `playlist.html`) gets the same tab bar fixed to the bottom of the screen from `js/guest-nav.js` (`initGuestNav(slug, active)`): Invitation, Photos, Quests, Bets, Playlist. It's added only once the invitation has loaded, so an invalid link shows no menu, and every link carries the guest's slug (`/i/{slug}` for the invitation, `?slug=` for the rest). Labels and the quests/bets/playlist copy are in `js/guest-i18n.js` (Russian, Uzbek, English — the guest's own `language`); `css/guest.css` builds on `invitation.css` and `media.css`.

Quest photos go through the same `uploader.js` as `media.html`, with a `questId` (see API.md section 9). Their ducats are paid out at the bank table (API.md section 11): the QR on `quests.html` is an `<img>` of `GET …/bank/qr` — rendered by the server, so no QR library here — reloaded every 5 minutes, never on each poll.

## Admin menu

Every admin page (`dashboard.html`, `hall.html`, `media-admin.html`, `activities.html`, `super-admin.html`) has an empty `<aside class="sidebar">` that `js/nav.js` (`initAdminNav()`) fills from one list, so the pages can't drift apart: Guests (guest list, hall seating, import), Content (photos and videos, plus the invitation gallery for super admin) and Management (admins, super admin only), with the username, EN/RU switch and sign-out at the bottom. Guest activities (quests, bets, DJ board, songs, bank) sit between Guests and Content. A DJ login (`auth.js` `isDj()`) sees only the two `dj.html` items, a banker (`isBanker()`) only the bank; `requireAuth()` sends either from any other admin page to their own, and `login.js` lands each role on `homePage()` (or on `?next=`, a page of this site). Add or rename a menu item there, not in the HTML.

Items are real links. Pages with several panels pick the panel from the URL hash — `dashboard.html#guests` / `#import`, `activities.html#quests` / `#bets`, `dj.html#board` / `#songs`, `super-admin.html#admins` / `#gallery` — and listen for `hashchange`, so panels are linkable and the back button works. On screens ≤ 720px the sidebar becomes a drawer behind a one-line top bar (☰ + current page); it sits below modals and above the hall page's unseated-guests tray.

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

## Two halls, two invitations

Guests are invited to one of two celebrations — each guest has a `hall` (`TASHKENT` or `SAMARKAND`, see API.md section 4). Samarkand belongs to the groom side alone: the bride side never sees the hall switcher, the guest editor's "Hall" picker, or the dashboard's "Hall" column (`getAccessibleHalls()` in `js/auth.js`, mirroring `Hall.java`).

A **hall admin** (created from the super admin page with "Hall access" set, e.g. `sam_hall` — groom side, Samarkand only) gets just that one hall, so none of those hall controls appear for them either, and their dashboard and hall map list every guest their side has in that hall, not only the ones they added (API.md sections 1–2). The login response's `hall` is saved in the session for `getAccessibleHalls()`.

`invitation.html` is one page for both: `js/invitation.js` reads the guest's `hall` and picks the event date (`EVENT_DATES`) and map query, and `js/i18n.js` overlays `HALL_STRINGS.SAMARKAND` on top of the Tashkent copy for venue/date/time text. The static `<title>`/Open Graph tags in `invitation.html` describe Tashkent; messenger crawlers don't run JS, so for a Samarkand guest `InvitationRedirectController` serves `/i/{slug}` with those head tags rewritten to Samarkand's date and venue (its `SAMARKAND_HEAD` must match the head text — startup fails if it drifts). The `invitation.html?slug=` form always previews as Tashkent.

## Photos and videos

`media.html` has three tabs: **My photos**, **My videos** and **Guests' feed**. A guest manages only their own uploads. New photos are uploaded as `PUBLIC` (they appear in the feed of every guest in the same hall, grouped by table — head, bride, groom, then "no table"), new videos as `PRIVATE` (only the guest and the admins). Each own tile has a lock/people button to switch it at any time; a short legend above each picker explains the two icons and that tapping one switches that photo or video. Admins always see both kinds; `media-admin.html` tags private ones. The rules live server-side (`GuestMediaService`); the page just renders what the API returns.

- **Uploads** go through `js/uploader.js`: chunked (8 MB) and resumable, 2 files at a time, with a progress bar per file (percent, MB, time left) and an overall one. A dropped connection retries the chunk automatically; if the page is reloaded, picking the same file again resumes it (the session id is kept in `localStorage`). Leaving mid-upload asks for confirmation.
- **60-second limit:** `js/video-duration.js` reads a video's length from its MP4/MOV header before uploading (works for iPhone HEVC `.mov` files even in browsers that can't play them), so a longer clip is refused immediately. The server re-measures the real file regardless.
- **iPhone formats:** guests can upload HEIC photos and HEVC/HDR `.mov` videos as they come off the phone. The server keeps the original and converts each upload into a JPEG / H.264 MP4 every browser can show (stripping location metadata), so after upload a tile shows "processing" (with % for videos) until it's ready. The page polls every 2 s while anything is processing.
- **Files** are loaded through signed, expiring links from the API (`fileUrl`, `thumbUrl`) — never built from ids. They stay identical for hours, so re-rendering reuses cached images.
- Strings for ru / uz / en are in `js/media-i18n.js` (the guest's `language` decides).

Admins see the same media per hall on `media-admin.html` (with "download original"), and per guest in the guest editor's "Media" section, which uploads through the same `uploader.js`.

## Auth

The JWT is stored in `localStorage` (see `js/auth.js`) after login and attached as `Authorization: Bearer <token>` to every request except `/api/auth/login` and anything under `/api/public/`. A `401` response anywhere clears the session and redirects to `login.html`.

This is simple but is vulnerable to XSS (any injected script can read `localStorage`). For a production deployment handling real guest data, consider having the backend issue the token as an `HttpOnly` cookie instead — that's a backend change, not something fixable purely in this frontend.

## What isn't covered

- No client-side form validation beyond what's needed to avoid obviously-bad requests (required fields, min length) — the backend's validation (`400` responses with `details[]`) is the source of truth and is what's actually displayed on error.
- No automated tests.
- The admin dashboard (`login.html`, `dashboard.html`, `hall.html`, `super-admin.html`) supports English and Russian, via the EN/RU switch at the bottom of the menu (login page: top of the card, since it has no sidebar). The choice is a per-browser preference — not tied to any admin account field — persisted in `localStorage` (`js/admin-i18n.js`) and applied on every admin page load; switching it reloads the page rather than trying to live-re-render every dynamically-built table/modal. Error messages returned by the API (validation failures, "Guest not found", etc.) are generated server-side in English and are **not** translated by this — that would need the backend to negotiate language itself, a separate change.
- New guests default to the admin's own current dashboard language (not always English) in the guest editor's "Invitation language" picker — an admin working in Russian is presumably inviting Russian-speaking guests. It's still just a default; pick a different one per guest (including Uzbek, which isn't an admin UI language) as needed.
- `invitation.html` supports Russian, Uzbek, and English — the three languages guests at this wedding actually speak. All of its static copy (fixed date/venue text, schedule, contacts, etc. — none of it guest-specific) lives in `js/i18n.js`, keyed by `data-i18n` attributes in the markup; `js/invitation.js` picks the dictionary from the guest's own `language` field (set by the admin in the guest editor) once that's fetched, and falls back to a browser-language guess for the loading/invalid-link screens shown before that's known. Guest-entered content (display name, custom greeting message) is never translated — it's shown back exactly as the admin typed it.
