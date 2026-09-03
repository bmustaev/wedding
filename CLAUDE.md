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

Both `bootRun` and the tests (`@SpringBootTest`) need a local MariaDB with a `wedding` database (`root`/`root` per `application.yaml`). On startup Spring runs `schema.sql` + `data.sql` (`sql.init.mode: always`); both are written to be idempotent on re-run. Seeded demo logins are in `data.sql` (e.g. `bride_side` / `test-password-123`).

## Architecture

### Roles and ownership scoping

Three access levels: `super_admin`, regular `admin` (one per wedding side, `BRIDE` or `GROOM`), and unauthenticated guests. The core invariant: **every admin-facing service method takes `AdminPrincipal` and filters by its `admin_id`** — one admin can never see another's guests, and cross-admin lookups return **404, not 403** (so the other ID's existence isn't leaked; documented in API.md). Super admin bypasses ownership/side restrictions (`isSuperAdmin()` branches, `/api/super-admin/**` gated by `ROLE_SUPER_ADMIN` in `SecurityConfig`).

An admin's `side` is intentionally **not** a JWT claim — it's re-derived from the DB row on every request via `AdminPrincipal.getSide()`, so side changes apply without reissuing tokens.

### Auth

Stateless JWT (`JwtAuthFilter` → `JwtService`), token from `POST /api/auth/login`. JWT uses **jjwt with the Gson binding, not jjwt-jackson** — deliberate, to stay decoupled from Spring Boot 4's Jackson 2/3 coexistence (see comment in `build.gradle`); don't switch it. Guest-facing endpoints (`/api/public/**`) have no login — they're authenticated by the unguessable invitation slug. Static files and `/i/**` are public; real protection lives in the API calls each page makes.

### Database owns the schema and the hard rules

`ddl-auto: validate` — Hibernate never mutates DDL; `schema.sql`/`data.sql` are the source of truth. Key conventions:

- Both SQL files use a custom `$$` statement separator (`spring.sql.init.separator`) because trigger/procedure bodies contain `;` — no literal `$$` may ever appear inside a statement or seed value.
- Business-rule enforcement of last resort is **in the database**: triggers cap table capacity (`trg_guests_table_capacity_*`) and per-guest media counts (`trg_guest_media_limit`). Java-side checks in services exist only to return clean 4xx errors before the trigger fires.
- `SeatingService` deliberately calls the views (`v_table_occupancy`, etc.) and the `get_seating_chart_for_admin` procedure via `JdbcTemplate` instead of reimplementing that logic in JPQL — don't duplicate it in Java.
- Enum-like columns are VARCHAR + CHECK, mapped through `AttributeConverter`s in `entity/converter/` (DB stores lowercase/snake values, Java uses enums). `guests.group_members` is CSV TEXT via `StringListCsvConverter`.
- Guests are soft-deleted (`deleted` flag); repository queries filter `DeletedFalse`.

### Media

Uploaded files go to the filesystem via `MediaStorageService` (`LocalFilesystemMediaStorageService`, dir from `app.media.storage-dir`); the DB stores only the storage key, never bytes. Limits (15 photos / 4 videos per guest) come from `app.media.*` config and are mirrored by the DB trigger.

### Invitation links

`app.invitation.base-url` controls the link admins copy (`GuestResponse.invitationUrl`). `InvitationRedirectController` forwards the pretty `/i/{slug}` path to `invitation.html?slug=...`. The frontend never reconstructs URLs from slugs beyond reading its own query string.

### Configuration

Runtime config via env vars with defaults in `application.yaml`: `SERVER_PORT`, `JWT_SECRET`, `JWT_EXPIRATION_MINUTES`, `MEDIA_STORAGE_DIR`, `INVITATION_BASE_URL`.
