# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Wedding invitation platform: a single Spring Boot 4 app (Java 25, Gradle) that serves both a JSON API under `/api/**` and the frontend — plain HTML/CSS/JS ES modules in `src/main/resources/static/`, no framework, no build step. Backed by MariaDB.

Two reference docs live with the frontend and should be kept in sync with code changes:
- `src/main/resources/static/API.md` — the full API contract (endpoints, error shapes, curl examples)
- `src/main/resources/static/README.md` — frontend structure and the invitation-link design decision

## Commands

All Gradle commands run from the repository root (note: this repo has a `gradle/` wrapper subdirectory — don't confuse it with the root).

```bash
./gradlew build          # compile + test
./gradlew test           # all tests
./gradlew test --tests "uz.bobnoza.wedding.WeddingApplicationTests"   # single test class
./gradlew bootRun        # run the app on :8080
```

Media conversion needs `ffmpeg` and `libvips` with HEIC support locally (`brew install ffmpeg vips`; on Ubuntu see `DEPLOYMENT.md`) — `bootRun` refuses to start without them; `MediaPipelineTest` is skipped if they're missing.

Both `bootRun` and the tests (`@SpringBootTest`) need a local MariaDB with a `wedding` database (`root`/`root` per `application.yaml`). On startup Spring runs `schema.sql` + `data.sql` (`sql.init.mode: always`); both are written to be idempotent on re-run. Seeded demo logins are in `data.sql` (e.g. `bride_side` / `test-password-123`).

## Architecture

### Roles and ownership scoping

Three access levels: `super_admin`, regular `admin` (one per wedding side, `BRIDE` or `GROOM`), and unauthenticated guests. The core invariant: **every admin-facing service method takes `AdminPrincipal` and filters by its `admin_id`** — one admin can never see another's guests, and cross-admin lookups return **404, not 403** (so the other ID's existence isn't leaked; documented in API.md). Super admin bypasses ownership/side restrictions (`isSuperAdmin()` branches, `/api/super-admin/**` gated by `ROLE_SUPER_ADMIN` in `SecurityConfig`).

The one deliberate exception is a **hall admin** (`admins.hall` set, e.g. `sam_hall` = groom side, Samarkand only): limited to that one hall, but manages every guest its side has there, whoever created them. Who-may-manage-which-guest lives in `AdminPrincipal.canManageGuest` (single-guest lookups), `GuestRepository.findAllManagedByHallAdmin` (lists) and the `get_seating_chart_for_admin` procedure's `is_own_guest` — keep the three in sync.

An admin's `side` (and `hall`) is intentionally **not** a JWT claim — it's re-derived from the DB row on every request via `AdminPrincipal.getSide()`/`getHall()`, so changes apply without reissuing tokens.

### Auth

Stateless JWT (`JwtAuthFilter` → `JwtService`), token from `POST /api/auth/login`. JWT uses **jjwt with the Gson binding, not jjwt-jackson** — deliberate, to stay decoupled from Spring Boot 4's Jackson 2/3 coexistence (see comment in `build.gradle`); don't switch it. Guest-facing endpoints (`/api/public/**`) have no login — they're authenticated by the unguessable invitation slug. Static files and `/i/**` are public; real protection lives in the API calls each page makes.

### Database owns the schema and the hard rules

`ddl-auto: validate` — Hibernate never mutates DDL; `schema.sql`/`data.sql` are the source of truth. Key conventions:

- Both SQL files use a custom `$$` statement separator (`spring.sql.init.separator`) because trigger/procedure bodies contain `;` — no literal `$$` may ever appear inside a statement or seed value.
- Business-rule enforcement of last resort is **in the database**: triggers cap table capacity (`trg_guests_table_capacity_*`) and per-guest media counts (`trg_guest_media_limit`). Java-side checks in services exist only to return clean 4xx errors before the trigger fires.
- `SeatingService` deliberately calls the views (`v_table_occupancy`, etc.) and the `get_seating_chart_for_admin` procedure via `JdbcTemplate` instead of reimplementing that logic in JPQL — don't duplicate it in Java.
- Enum-like columns are VARCHAR + CHECK, mapped through `AttributeConverter`s in `entity/converter/` (DB stores lowercase/snake values, Java uses enums). `guests.group_members` is CSV TEXT via `StringListCsvConverter`.
- Guests are soft-deleted (`deleted` flag); repository queries filter `DeletedFalse`.

### Halls

Every guest and every seating table has a `hall`: `TASHKENT` (main: head/bride/groom tables) or `SAMARKAND` (groom side only, tables 1B–8B seeded once in `data.sql`). `Hall.isOpenTo(side)` + `AdminPrincipal.canAccessHall()` (which also applies a hall admin's single hall) are the access rule; a hall the caller can't access is treated as nonexistent (404), consistent with the ownership rule. A guest can only sit at a table in their own hall (Java check + `check_table_hall` in the guests triggers), and their hall picks which invitation (venue/date/time) `invitation.html` renders.

### Media

Uploads are chunked and resumable (`MediaUploadService`, `media_uploads` table; client in `js/uploader.js`): start → PUT chunks at explicit offsets → complete. On completion the file is identified by **content**, never name/Content-Type (`MediaProbeService`: magic-byte allowlist, then libvips `vipsheader` for photos, `ffprobe -f mov` for videos — incl. the real video length), the untouched original is stored via `MediaStorageService` (`LocalFilesystemMediaStorageService`, dir from `app.media.storage-dir`; scratch space for in-progress uploads/conversions is on the same volume so finishing is a rename), and a `PROCESSING` row is queued. The DB stores only storage keys, never bytes.

`MediaProcessingQueue` (bounded worker lanes, photos never wait behind videos; re-queues `PROCESSING` rows on startup) runs `MediaProcessor` → `MediaTranscodeService`, which shells out (via `ProcessRunner`, hard timeouts, no shell) to **vipsthumbnail** (photo → sRGB JPEG display + thumbnail, all metadata incl. GPS stripped) and **ffmpeg** (video → H.264 8-bit ≤1080p MP4 + poster; HDR tone-mapped with zscale, falling back to the `colorspace` filter; metadata stripped; already-compatible H.264 is only remuxed). This exists because iPhone HEIC/HEVC/HDR don't display in Chrome. `MediaTools` checks the binaries (and libvips HEIC support) at startup and fails fast unless `app.media.require-tools=false` (tests). `GuestMedia.storageKey` = original, `displayKey`/`thumbKey` = converted copies.

Visibility (`GuestMediaService`): each upload is `PUBLIC` or `PRIVATE` (`MediaVisibility`, `guest_media.visibility`), chosen by the guest when starting the upload (default: photos public, videos private) and switchable later (`PATCH /api/public/invitations/{slug}/media/{id}`). A guest sees all their own media plus every guest's READY **PUBLIC** photos and videos in their own hall, grouped by table (`feed`); PRIVATE media is seen only by the uploader and admins. Admins see media of guests they can manage (`AdminPrincipal.canManageGuest`, same as the dashboard). Files are served only through **signed, expiring URLs** (`MediaUrlSigner`, HMAC keyed off the JWT secret, variant is part of the signature so display/thumb links can't become `original`) by `PublicMediaFileController` — authorization happens when a list response is built, not on the file request.

Limits come from `app.media.*` (`MediaProperties`): 15 photos / 4 videos per guest (mirrored by `trg_guest_media_limit`), videos ≤ 60 s (mirrored by `ck_guest_media_video_duration`), max file sizes, `min-free-disk` (507 below it). Rejections carry a machine-readable `error` code (`MediaRejectedException`: `VIDEO_TOO_LONG`, `UNSUPPORTED_FORMAT`, `FILE_TOO_LARGE`, `STORAGE_FULL`) that the guest page localizes.

### Invitation links

`app.invitation.base-url` controls the link admins copy (`GuestResponse.invitationUrl`). `InvitationRedirectController` forwards the pretty `/i/{slug}` path to `invitation.html?slug=...`. The frontend never reconstructs URLs from slugs beyond reading its own query string.

### Configuration

Runtime config via env vars with defaults in `application.yaml`: `SERVER_PORT`, `JWT_SECRET`, `JWT_EXPIRATION_MINUTES`, `MEDIA_STORAGE_DIR`, `MEDIA_MIN_FREE_DISK`, `MEDIA_REQUIRE_TOOLS`, `FFMPEG_PATH`/`FFPROBE_PATH`/`VIPS_PATH`/`VIPSHEADER_PATH`/`VIPSTHUMBNAIL_PATH`, `INVITATION_BASE_URL`.
