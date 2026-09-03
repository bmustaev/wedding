# Wedding Backend — API Documentation

Base URL (local dev): `http://localhost:8080`

All request/response bodies are JSON unless noted (file uploads use `multipart/form-data`). All timestamps are ISO-8601 UTC (e.g. `2026-09-02T14:30:00Z`). All IDs are UUIDs.

## Authentication

Every endpoint requires a `Bearer` JWT **except**:
- `/api/auth/login`
- `/api/public/**` (guest-facing — authenticated by the unguessable invitation link instead)

```
Authorization: Bearer <token>
```

Get a token from `POST /api/auth/login`. Tokens expire after `app.jwt.expiration-minutes` (default 480 minutes / 8 hours — see `application.yml`).

Endpoints under `/api/super-admin/**` additionally require the token to belong to a `super_admin` account — a regular admin's valid token is rejected with `403` before any controller code runs.

## Error format

Every error follows the same shape:

```json
{
  "timestamp": "2026-09-02T14:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Guest not found: 3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "details": []
}
```

| Status | When |
|---|---|
| 400 | Request body failed validation — `details` lists each field |
| 401 | Bad username/password on login |
| 403 | Token valid but the account doesn't have permission (e.g. non-super-admin hitting `/api/super-admin/**`) |
| 404 | Resource doesn't exist — **or belongs to a different admin**. An admin never gets a 403 for another admin's guest; they get a 404, so they can't even confirm the ID exists |
| 409 | A business rule was violated — table doesn't have enough seats, or the guest already hit their photo/video cap |
| 413 | Uploaded file exceeds the size limit (25MB default) |
| 500 | Unexpected server error |

---

## 1. Authentication

### `POST /api/auth/login`

**Auth:** none

**Request:**
```json
{
  "username": "bride_side",
  "password": "test-password-123"
}
```

**Response `200`:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJicmlkZV9zaWRlIiwi...",
  "adminId": "8a1e2b3c-4d5e-6f70-8192-a3b4c5d6e7f8",
  "username": "bride_side",
  "role": "ADMIN",
  "side": "BRIDE"
}
```

`side` is `"BRIDE"` or `"GROOM"` for a regular admin, `null` for a super admin. It's not read from a JWT claim — every request re-derives it fresh from the admin's row via `AdminPrincipal.getSide()`, so a side change takes effect on the very next request without needing a new token.

**Response `401`** (wrong password or disabled account):
```json
{
  "timestamp": "2026-09-02T14:30:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid username or password",
  "details": []
}
```

**curl:**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"bride_side","password":"test-password-123"}'
```

---

## 2. Guest management (admin's own list)

Every endpoint in this section is scoped to the calling admin — `admin_id` on the JWT determines which guests are visible, regardless of what ID appears in the URL.

### `GET /api/guests`

**Auth:** admin

**Query params** (standard Spring pagination — all optional):
| Param | Default | Example |
|---|---|---|
| `page` | 0 | `?page=1` |
| `size` | 50 | `?size=20` |
| `sort` | — | `?sort=displayName,asc` |

**curl:**
```bash
curl http://localhost:8080/api/guests?page=0&size=20 \
  -H "Authorization: Bearer $TOKEN"
```

**Response `200`:**
```json
{
  "content": [
    {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "displayName": "The Miller Family",
      "isGroup": true,
      "partySize": 4,
      "groupMembers": ["Tom Miller", "Ann Miller", "Lucy Miller", "Ben Miller"],
      "greetingMessage": "So excited to celebrate with you!",
      "language": "ru",
      "landingSlug": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4",
      "invitationUrl": "http://localhost:8080/i/a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4",
      "tableId": "b2c3d4e5-f6a1-b2c3-d4e5-f6a1b2c3d4e5",
      "tableNumber": 1,
      "pageGeneratedAt": "2026-09-01T10:00:00Z",
      "firstViewedAt": "2026-09-01T18:22:00Z",
      "createdAt": "2026-09-01T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### `POST /api/guests`

**Auth:** admin

**Request** (single guest):
```json
{
  "displayName": "Jane Doe",
  "isGroup": false,
  "greetingMessage": "So excited to celebrate with you!",
  "language": "en"
}
```

**Request** (group/family — `partySize > 1` implies a group):
```json
{
  "displayName": "The Miller Family",
  "isGroup": true,
  "partySize": 4,
  "groupMembers": ["Tom Miller", "Ann Miller", "Lucy Miller", "Ben Miller"],
  "language": "en"
}
```

| Field | Required | Notes |
|---|---|---|
| `displayName` | yes | Shown on the invitation |
| `isGroup` | no (default `false`) | Must be `true` if `partySize > 1` |
| `partySize` | no (default `1`) | Min `1`; forced to `1` server-side if `isGroup` is `false` |
| `groupMembers` | no | Individual names, display only |
| `greetingMessage` | no | Custom text on the landing page |
| `language` | no (default `"ru"`) | `"en"`, `"ru"`, or `"uz"` — selects which language `invitation.html` renders in for this guest (see its README.md). Any other value falls back to Russian client-side; not validated server-side. |

**Response `201`:** same shape as a `GuestResponse` above. `landingSlug`/`invitationUrl` are generated automatically — the landing page exists as soon as the guest is created, no separate "generate" step.

**curl:**
```bash
curl -X POST http://localhost:8080/api/guests \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"displayName":"Jane Doe","isGroup":false,"greetingMessage":"So excited!"}'
```

### `GET /api/guests/{guestId}`

**Auth:** admin (must own the guest)

Returns a single `GuestResponse`, or `404` if the guest doesn't exist or belongs to a different admin.

### `PATCH /api/guests/{guestId}`

**Auth:** admin (must own the guest)

All fields optional — only send what changes.

```json
{
  "greetingMessage": "Updated: can't wait to see you!",
  "partySize": 5,
  "groupMembers": ["Tom Miller", "Ann Miller", "Lucy Miller", "Ben Miller", "Baby Miller"]
}
```

To turn a single guest into a group (or back), include `isGroup` explicitly:
```json
{ "isGroup": true, "partySize": 4, "groupMembers": ["Tom Miller", "Ann Miller", "Lucy Miller", "Ben Miller"] }
```
Setting `isGroup: false` forces `partySize` back to `1` server-side regardless of what's sent, same as on create (`ck_guests_group_size`).

**Response `200`:** the updated `GuestResponse`. Editing content bumps `pageGeneratedAt` (treated as a regeneration of the invitation page).

### `DELETE /api/guests/{guestId}`

**Auth:** admin (must own the guest)

Soft delete — the row is kept (marked `is_deleted`), not removed. Also clears any table assignment.

**Response:** `204 No Content`

### `POST /api/guests/{guestId}/regenerate-page`

**Auth:** admin (must own the guest)

Bumps `pageGeneratedAt` to now without changing any content. Returns the updated `GuestResponse`.

### `PUT /api/guests/{guestId}/table`

**Auth:** admin (must own the guest). The target table must be on the admin's own side, or be the head table — a `403` otherwise.

**Request:**
```json
{
  "tableId": "b2c3d4e5-f6a1-b2c3-d4e5-f6a1b2c3d4e5"
}
```

**Response `200`:** the updated `GuestResponse` with `tableId`/`tableNumber` set.

**Response `403`** if the table belongs to the other side:
```json
{
  "timestamp": "2026-09-03T10:00:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "You can only seat your own guests at tables on your own side",
  "details": []
}
```

**Response `409`** if the table doesn't have enough seats left:
```json
{
  "timestamp": "2026-09-02T14:30:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Table 1B has 3 seat(s) left, but this guest needs 4",
  "details": []
}
```

### `DELETE /api/guests/{guestId}/table`

**Auth:** admin (must own the guest)

Removes the guest's table assignment. Returns the updated `GuestResponse` (with `tableId`/`tableNumber` now `null`).

---

## 3. Media — admin side

For an admin managing a guest they own (e.g. removing inappropriate content). Guests upload their own media through the **public** endpoints in section 7 instead.

### `GET /api/guests/{guestId}/media`

**Auth:** admin (must own the guest)

**Response `200`:**
```json
[
  {
    "id": "c3d4e5f6-a1b2-c3d4-e5f6-a1b2c3d4e5f6",
    "mediaType": "PHOTO",
    "storageKey": "photos/3fa85f64.../a1b2c3d4.jpg",
    "originalFilename": "beach.jpg",
    "uploadedAt": "2026-09-01T20:00:00Z"
  }
]
```

### `GET /api/guests/{guestId}/media/allowance`

**Auth:** admin (must own the guest)

**Response `200`:**
```json
{
  "photosUsed": 3,
  "photosRemaining": 12,
  "videosUsed": 1,
  "videosRemaining": 3
}
```

### `POST /api/guests/{guestId}/media/photos` and `.../videos`

**Auth:** admin (must own the guest)
**Content-Type:** `multipart/form-data`, field name `file`

**curl:**
```bash
curl -X POST http://localhost:8080/api/guests/$GUEST_ID/media/photos \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@beach.jpg"
```

**Response `201`:** a `MediaResponse` (shape above).

**Response `409`** once the guest is at the cap (15 photos / 4 videos):
```json
{
  "timestamp": "2026-09-02T14:30:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "This guest already has the maximum of 15 photo uploads",
  "details": []
}
```

### `DELETE /api/guests/{guestId}/media/{mediaId}`

**Auth:** admin (must own the guest)

**Response:** `204 No Content`

---

## 4. Seating

Every table belongs to a **side**: `"BRIDE"`, `"GROOM"`, or `"HEAD"` (the single, fixed bride-and-groom table). An admin can only assign guests to, or manage tables on, their own side — the head table is open to either side for their own guests. Table numbering restarts per side (a "1D" and a "1B" coexist — bride tables get the `D` suffix, groom tables get `B`, per this deployment's couple); the head table has `tableNumber: null` and is always labeled `"Head Table"`.

### `GET /api/seating/occupancy`

**Auth:** any admin

Seat counts only, no guest names — safe regardless of who owns what.

**Response `200`:**
```json
[
  { "tableId": "b2c3d4e5-...", "side": "BRIDE", "tableNumber": 1, "label": "1D", "capacity": 12, "seatsTaken": 5, "seatsLeft": 7 },
  { "tableId": "c3d4e5f6-...", "side": "GROOM", "tableNumber": 1, "label": "1B", "capacity": 12, "seatsTaken": 0, "seatsLeft": 12 },
  { "tableId": "d1e2f3a4-...", "side": "HEAD", "tableNumber": null, "label": "Head Table", "capacity": 2, "seatsTaken": 1, "seatsLeft": 1 }
]
```

### `GET /api/seating/chart`

**Auth:** any admin

Every table across all three sides. **Own guests show by name; every other admin's guest is anonymized.** This is the isolation behavior validated live against the database (see `get_seating_chart_for_admin` in `schema.sql`) — unchanged by the side model. Side only governs *who can assign guests where*, not *who can see what*.

**Response `200`:**
```json
[
  {
    "tableId": "b2c3d4e5-...",
    "side": "BRIDE",
    "tableNumber": 1,
    "label": "1D",
    "capacity": 12,
    "seatsLeft": 7,
    "guestId": "3fa85f64-...",
    "displayName": "The Miller Family",
    "partySize": 4,
    "ownGuest": true
  },
  {
    "tableId": "c3d4e5f6-...",
    "side": "GROOM",
    "tableNumber": 1,
    "label": "1B",
    "capacity": 12,
    "seatsLeft": 12,
    "guestId": null,
    "displayName": null,
    "partySize": null,
    "ownGuest": false
  }
]
```

(A table with no guests seated yet still appears, with `guestId`/`displayName`/`partySize` all `null`.)

### `GET /api/seating/hall`

**Auth:** any admin

Everything the hall-map page needs in a single call: the head table, every bride table, every groom table (each already carrying its guest list, isolation rules applied exactly as in `/chart`), plus the caller's own unassigned guests for populating a "drag from here" roster. Not paginated — returns all of the caller's unassigned guests at once.

**Response `200`:**
```json
{
  "headTable": {
    "id": "d1e2f3a4-...", "side": "HEAD", "tableNumber": null, "label": "Head Table",
    "capacity": 2, "seatsLeft": 1,
    "guests": [{ "guestId": "e5f6a1b2-...", "displayName": "Reserved (1 seats)", "partySize": 1, "ownGuest": false }]
  },
  "brideTables": [
    {
      "id": "b2c3d4e5-...", "side": "BRIDE", "tableNumber": 1, "label": "1D",
      "capacity": 12, "seatsLeft": 7,
      "guests": [{ "guestId": "3fa85f64-...", "displayName": "The Miller Family", "partySize": 4, "ownGuest": true }]
    }
  ],
  "groomTables": [
    { "id": "c3d4e5f6-...", "side": "GROOM", "tableNumber": 1, "label": "1B", "capacity": 12, "seatsLeft": 12, "guests": [] }
  ],
  "unassignedGuests": [
    { "id": "f6a1b2c3-...", "displayName": "Jane Doe", "partySize": 1, "isGroup": false }
  ]
}
```

### `POST /api/seating/tables`

**Auth:** admin (not super admin — a super admin has no side, and `403`s here)

Adds a table on the **caller's own side**. The table number is auto-assigned (next available for that side) — never client-supplied, so two admins can't collide.

**Request:**
```json
{ "capacity": 12 }
```
(`capacity` optional, defaults to 12.)

**Response `201`:**
```json
{ "id": "a1b2c3d4-...", "side": "BRIDE", "tableNumber": 3, "label": "3D", "capacity": 12, "seatsLeft": 12 }
```

### `DELETE /api/seating/tables/{tableId}`

**Auth:** admin — must own the table's side. `403` if it's another side's table, `403` if it's the head table (can't be removed at all).

**Response `409`** if guests are still seated there:
```json
{
  "timestamp": "2026-09-03T10:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Move the 2 guest(s) at 3B to another table first",
  "details": []
}
```

**Response:** `204 No Content` on success.


---

## 5. Bulk import

### `POST /api/imports`

**Auth:** admin
**Content-Type:** `multipart/form-data`, field name `file`

**Expected `.txt` format** — one guest per line:

| Line | Result |
|---|---|
| `Jane Doe` | Single guest, party size 1 |
| `The Miller Family;4` | Group of 4, no individual names stored |
| `The Miller Family;4;Tom,Ann,Lucy,Ben` | Group of 4 with names |

Blank lines are skipped (not counted as rows). A line that fails to parse doesn't fail the whole file — it's recorded with its error and the rest of the file keeps processing.

**curl:**
```bash
curl -X POST http://localhost:8080/api/imports \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@guestlist.txt"
```

**Response `201`:**
```json
{
  "batchId": "e5f6a1b2-c3d4-e5f6-a1b2-c3d4e5f6a1b2",
  "filename": "guestlist.txt",
  "status": "COMPLETED",
  "totalRows": 3,
  "successRows": 2,
  "errorRows": 1,
  "rows": [
    { "rowNumber": 1, "rawLine": "Jane Doe", "guestId": "3fa85f64-...", "errorMessage": null },
    { "rowNumber": 2, "rawLine": "The Miller Family;4;Tom,Ann,Lucy,Ben", "guestId": "d4e5f6a1-...", "errorMessage": null },
    { "rowNumber": 3, "rawLine": "Bad Row;notanumber", "guestId": null, "errorMessage": "Party size must be a whole number, got 'notanumber'" }
  ]
}
```

### `GET /api/imports`

**Auth:** admin

List of past batches for the calling admin, newest first. `rows` is omitted (empty array) in the list view — fetch a single batch for row-level detail.

### `GET /api/imports/{batchId}`

**Auth:** admin (must own the batch)

Same shape as the `POST` response, including the full `rows` array.

---

## 6. Super admin

Requires a `super_admin` token. A regular admin's token gets a blanket `403` on all of these before any handler runs.

### `GET /api/super-admin/admins`

**Response `200`:**
```json
[
  {
    "id": "8a1e2b3c-...",
    "username": "bride_side",
    "role": "ADMIN",
    "side": "BRIDE",
    "active": true,
    "guestCount": 12,
    "createdAt": "2026-09-01T09:00:00Z"
  },
  {
    "id": "9b2f3c4d-...",
    "username": "super_admin",
    "role": "SUPER_ADMIN",
    "side": null,
    "active": true,
    "guestCount": 0,
    "createdAt": "2026-09-01T09:00:00Z"
  }
]
```

### `POST /api/super-admin/admins`

**Request:**
```json
{
  "username": "groom_side",
  "password": "a-real-password-here",
  "side": "GROOM"
}
```
(`username`: 3–64 chars; `password`: 8–128 chars, validated but **not** hashed client-side — the server hashes it. `side`: `"BRIDE"` or `"GROOM"`, required — `400` on anything else.)

**Response `201`:** an `AdminSummaryResponse`. New admins are always created with role `ADMIN` — only a database operator can create another `SUPER_ADMIN`.

### `PATCH /api/super-admin/admins/{adminId}/active`

**Query param:** `active` (boolean, required)

```bash
curl -X PATCH "http://localhost:8080/api/super-admin/admins/$ADMIN_ID/active?active=false" \
  -H "Authorization: Bearer $SUPER_TOKEN"
```

Disables (or re-enables) an admin's login without deleting them or their guests.

### `GET /api/super-admin/admins/{adminId}/guests`

Same paginated `GuestResponse` shape as `GET /api/guests`, but for the chosen admin — this is the "click into an admin, see their guest list" drill-down. Same `page`/`size`/`sort` query params apply.

---

## 7. Public invitations (guest-facing — no login)

Reached by the guest clicking the link on their invitation. **The slug in the URL is the credential** — there's no guest account or password. Never share a guest's `id`; only the `landingSlug`/`invitationUrl`.

### `GET /api/public/invitations/{slug}`

**curl:**
```bash
curl http://localhost:8080/api/public/invitations/a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4
```

**Response `200`:**
```json
{
  "displayName": "The Miller Family",
  "isGroup": true,
  "groupMembers": ["Tom Miller", "Ann Miller", "Lucy Miller", "Ben Miller"],
  "greetingMessage": "So excited to celebrate with you!",
  "language": "en",
  "tableNumber": 1,
  "tableLabel": "1D",
  "photosRemaining": 15,
  "videosRemaining": 4
}
```

`tableLabel` is the side-qualified form guests actually recognize — bride tables are `"{n}D"`, groom tables `"{n}B"`, the head table is `"Head Table"` (see the seating section below). Both `tableNumber` and `tableLabel` are `null` until the guest has a table assigned.

`language` drives which of the three languages (`en`/`ru`/`uz`) `invitation.html` actually renders in for this guest — see this project's frontend README.md.

First call marks `first_viewed_at` on the guest record server-side (not returned in this response, but visible to the admin via `GET /api/guests/{id}`).

**Response `404`** for an unknown or deleted slug:
```json
{
  "timestamp": "2026-09-02T14:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Invitation not found",
  "details": []
}
```

### `GET /api/public/invitations/{slug}/media`

Returns the same `MediaResponse[]` shape as the admin-side media list, scoped to this guest.

### `POST /api/public/invitations/{slug}/media/photos` and `.../videos`

**Content-Type:** `multipart/form-data`, field name `file`

```bash
curl -X POST http://localhost:8080/api/public/invitations/a1b2c3d4.../media/photos \
  -F "file=@my-photo.jpg"
```

**Response `201`:** a `MediaResponse`. **Response `409`** once the guest hits their cap — same shape as the admin-side upload.

### `DELETE /api/public/invitations/{slug}/media/{mediaId}`

Lets a guest remove their own upload (e.g. wrong photo). Scoped to that slug's guest — a guest can't delete another guest's media even if they guess a `mediaId`, because the lookup is always `(mediaId, guestId-resolved-from-slug)`, never a bare `mediaId`.

**Response:** `204 No Content`

---

## Quick reference — all routes

| Method | Path | Auth |
|---|---|---|
| POST | `/api/auth/login` | none |
| GET | `/api/guests` | admin |
| POST | `/api/guests` | admin |
| GET | `/api/guests/{id}` | admin |
| PATCH | `/api/guests/{id}` | admin |
| DELETE | `/api/guests/{id}` | admin |
| POST | `/api/guests/{id}/regenerate-page` | admin |
| PUT | `/api/guests/{id}/table` | admin |
| DELETE | `/api/guests/{id}/table` | admin |
| GET | `/api/guests/{id}/media` | admin |
| GET | `/api/guests/{id}/media/allowance` | admin |
| POST | `/api/guests/{id}/media/photos` | admin |
| POST | `/api/guests/{id}/media/videos` | admin |
| DELETE | `/api/guests/{id}/media/{mediaId}` | admin |
| GET | `/api/seating/occupancy` | admin |
| GET | `/api/seating/chart` | admin |
| GET | `/api/seating/hall` | admin |
| POST | `/api/seating/tables` | admin |
| DELETE | `/api/seating/tables/{id}` | admin |
| POST | `/api/imports` | admin |
| GET | `/api/imports` | admin |
| GET | `/api/imports/{id}` | admin |
| GET | `/api/super-admin/admins` | super admin |
| POST | `/api/super-admin/admins` | super admin |
| PATCH | `/api/super-admin/admins/{id}/active` | super admin |
| GET | `/api/super-admin/admins/{id}/guests` | super admin |
| GET | `/api/public/invitations/{slug}` | none |
| GET | `/api/public/invitations/{slug}/media` | none |
| POST | `/api/public/invitations/{slug}/media/photos` | none |
| POST | `/api/public/invitations/{slug}/media/videos` | none |
| DELETE | `/api/public/invitations/{slug}/media/{mediaId}` | none |
