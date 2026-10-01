# Wedding Backend — API Documentation

Base URL (local dev): `http://localhost:8080`

All request/response bodies are JSON unless noted (guest media uploads send raw chunks as `application/octet-stream`; the super admin's gallery upload and the guest-list import use `multipart/form-data`). All timestamps are ISO-8601 UTC (e.g. `2026-09-02T14:30:00Z`). All IDs are UUIDs.

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
| 413 | Uploaded file exceeds the size limit |
| 415 | A media upload isn't a photo/video format the server accepts |
| 507 | The media disk is too full to accept new uploads |
| 500 | Unexpected server error |

### Media upload errors

Rejected media uploads (section 3a) use a machine-readable code in `error` instead of the reason phrase, so the guest page can show its own message in the guest's language (`js/media-i18n.js`); `message` is the English fallback.

| Status | `error` | When |
|---|---|---|
| 400 | `VIDEO_TOO_LONG` | Video over 60 seconds (`app.media.max-video-seconds`, ~0.5 s tolerance). Checked when the upload starts (from the duration the browser measured) and again on completion (ffprobe on the real file) |
| 413 | `FILE_TOO_LARGE` | Over `app.media.max-photo-size` (40 MB) / `max-video-size` (500 MB), or a chunk larger than `chunkSize` |
| 415 | `UNSUPPORTED_FORMAT` | Photo isn't JPEG/HEIC/PNG/WebP, or video isn't MOV/MP4 — decided by the file's content, never its name or `Content-Type` |
| 507 | `STORAGE_FULL` | Free disk space would drop below `app.media.min-free-disk` (30 GB) |
| 409 | `Conflict` | Photo/video cap reached (15 / 4, counting uploads in progress) |

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
  "side": "BRIDE",
  "hall": null
}
```

`side` is `"BRIDE"` or `"GROOM"` for a regular admin, `null` for a super admin. It's not read from a JWT claim — every request re-derives it fresh from the admin's row via `AdminPrincipal.getSide()`, so a side change takes effect on the very next request without needing a new token.

`hall` is set only for a **hall admin** (e.g. `sam_hall`: `"GROOM"`, `"SAMARKAND"`) — one limited to that single hall. Everywhere else in this API, a hall admin behaves as if the other hall didn't exist (`404`), an omitted `hall` parameter means their own hall instead of `TASHKENT`, and their "own guests" are **every guest their side has in that hall**, whoever added them (see section 2). Like `side`, it's re-derived from the admin's row on every request.

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

Every endpoint in this section is scoped to the calling admin — `admin_id` on the JWT determines which guests are visible, regardless of what ID appears in the URL. A guest the caller can't manage is a `404`, never a `403`.

The one widening is a **hall admin** (`hall` set, see section 1): their list — and every guest/media/table-assignment endpoint below — covers their own guests **plus every guest their side has in their hall**, including ones another admin of that side added. Those other admins keep full access to their own guests too.

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
      "hall": "TASHKENT",
      "landingSlug": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4",
      "invitationUrl": "http://localhost:8080/i/a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4",
      "tableId": "b2c3d4e5-f6a1-b2c3-d4e5-f6a1b2c3d4e5",
      "tableNumber": 1,
      "tableLabel": "1D",
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
| `hall` | no (default `"TASHKENT"`) | `"TASHKENT"` or `"SAMARKAND"` — which celebration the guest is invited to (see section 4). Decides the invitation's venue/date/time and which tables they can sit at. `"SAMARKAND"` is groom side (and super admin) only — `404` for the bride side, as if the hall didn't exist. |

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

Changing `hall` also clears the guest's table (it was in the other hall) — re-seat them in the new one. Same access rule as on create; a super admin additionally gets `403` moving a bride-side admin's guest to Samarkand.

**Response `200`:** the updated `GuestResponse`. Editing content bumps `pageGeneratedAt` (treated as a regeneration of the invitation page).

### `DELETE /api/guests/{guestId}`

**Auth:** admin (must own the guest)

Soft delete — the row is kept (marked `is_deleted`), not removed. Also clears any table assignment.

**Response:** `204 No Content`

### `POST /api/guests/{guestId}/regenerate-page`

**Auth:** admin (must own the guest)

Bumps `pageGeneratedAt` to now without changing any content. Returns the updated `GuestResponse`.

### `PUT /api/guests/{guestId}/table`

**Auth:** admin (must own the guest). The target table must be on the admin's own side, or be the head table — a `403` otherwise. It must also be in the guest's own `hall` — `403` otherwise (`trg_guests_table_capacity_*` enforce the same in the database). A table in a hall the caller can't access is `404`.

**Request:**
```json
{
  "tableId": "b2c3d4e5-f6a1-b2c3-d4e5-f6a1b2c3d4e5"
}
```

**Response `200`:** the updated `GuestResponse` with `tableId`/`tableNumber`/`tableLabel` set.

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

Removes the guest's table assignment. Returns the updated `GuestResponse` (with `tableId`/`tableNumber`/`tableLabel` now `null`).

---

## 3. Media — admin side

For an admin managing a guest they own (e.g. removing inappropriate content, uploading on a guest's behalf). Guests upload their own media through the **public** endpoints in section 7 instead; `GET /api/media` below is the hall-wide view behind `media-admin.html`.

Every upload is kept as the untouched **original** and converted server-side into copies every browser can show (iPhone HEIC photos and HEVC/HDR videos included): photos → metadata-stripped JPEG (≤ 2560 px) + thumbnail (≤ 480 px); videos → H.264 MP4 (≤ 1080p, SDR) + poster frame. Conversion runs in the background, so a new item starts as `PROCESSING`.

### `MediaResponse`

```json
{
  "id": "c3d4e5f6-a1b2-c3d4-e5f6-a1b2c3d4e5f6",
  "mediaType": "VIDEO",
  "visibility": "PRIVATE",
  "status": "READY",
  "processingPercent": null,
  "queuePosition": null,
  "processingError": null,
  "originalFilename": "IMG_0042.MOV",
  "sizeBytes": 187654321,
  "durationSeconds": 42,
  "widthPx": 1080,
  "heightPx": 1920,
  "uploadedAt": "2026-10-02T20:00:00Z",
  "guestId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "guestName": "The Miller Family",
  "tableLabel": "2D",
  "own": false,
  "fileUrl": "/api/public/media/c3d4e5f6-.../display?exp=1790942400&sig=…",
  "thumbUrl": "/api/public/media/c3d4e5f6-.../thumb?exp=1790942400&sig=…",
  "originalUrl": "/api/public/media/c3d4e5f6-.../original?exp=1790942400&sig=…"
}
```

- `visibility`: `PUBLIC` (shown in the shared feed to every guest in the uploader's hall) or `PRIVATE` (only the uploader and the admins). The guest picks it when uploading and can switch it later (section 7). Admins see both kinds everywhere in this section.
- `status`: `PROCESSING` → `READY`, or `FAILED` (`processingError` says why — admins only). `fileUrl`/`thumbUrl` are `null` until `READY`; for a video, `thumbUrl` is the poster frame.
- While `PROCESSING`, the uploader and admins get `processingPercent` (video being converted now) or `queuePosition` (waiting; `0` = next).
- The `*Url`s are signed, expiring links — see section 7a. `originalUrl`, `guestId` and `processingError` are admin-only.
- `widthPx`/`heightPx` are as displayed (portrait phone video is taller than wide).

### `GET /api/guests/{guestId}/media`

**Auth:** admin (must own the guest)

**Response `200`:** `MediaResponse[]` — all of this guest's photos and videos, any status.

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

### Chunked uploads: `POST /api/guests/{guestId}/media/uploads` …

**Auth:** admin (must own the guest). Same three-step flow as the guest side — see **section 3a**, with `/api/guests/{guestId}/media` in place of `/api/public/invitations/{slug}/media`.

### `DELETE /api/guests/{guestId}/media/{mediaId}`

**Auth:** admin (must own the guest). Deletes the original and its converted copies.

**Response:** `204 No Content`

### `GET /api/media?type=PHOTO|VIDEO&hall=TASHKENT|SAMARKAND`

**Auth:** admin. Every photo (default) or video of the guests the caller manages in one hall — the same ownership rule as the guest list (own guests; a hall admin's whole side in their hall; super admin everyone) — grouped by table. `hall` works as in `GET /api/seating/hall`: absent = the caller's default hall, a hall the caller can't access = `404`.

**Response `200`:**
```json
[
  { "tableLabel": "Head Table", "tableSide": "HEAD", "tableNumber": null, "items": [ /* MediaResponse */ ] },
  { "tableLabel": "1D", "tableSide": "BRIDE", "tableNumber": 1, "items": [ … ] },
  { "tableLabel": "1B", "tableSide": "GROOM", "tableNumber": 1, "items": [ … ] },
  { "tableLabel": null, "tableSide": null, "tableNumber": null, "items": [ … ] }
]
```
Groups come head table first, then bride tables, then groom tables, by number; guests without a table come last (`tableLabel: null`). Within a table: by guest name, then upload time. Tables with nothing uploaded are left out.

### `GET /api/media/storage`

**Auth:** admin. Free space on the media disk.

```json
{ "freeBytes": 412316860416, "totalBytes": 429496729600, "minFreeBytes": 32212254720 }
```

---

## 3a. Chunked uploads (guest and admin)

Uploads are **resumable**: a 60-second 4K iPhone clip is ~400 MB, sent over venue Wi-Fi. The browser sends the file in chunks (8 MB), each at an explicit offset; after a dropped connection it asks how much arrived and continues from there. `js/uploader.js` implements the client side.

Paths below are for a guest (`/api/public/invitations/{slug}/media`); the admin equivalents live under `/api/guests/{guestId}/media`.

**1. Start** — `POST …/uploads`

```json
{ "mediaType": "VIDEO", "filename": "IMG_0042.MOV", "sizeBytes": 187654321, "durationSeconds": 42.4, "visibility": "PRIVATE" }
```
`visibility` (optional, `PUBLIC` | `PRIVATE`) is who besides the admins sees the finished file — absent means photos `PUBLIC`, videos `PRIVATE`. `durationSeconds` (videos, optional) is what the browser measured — over the limit fails here, before any bytes are sent. Also checked here: file size, the guest's cap (counting other uploads in progress), and free disk space.

**Response `201`:**
```json
{ "uploadId": "9b2c…", "mediaType": "VIDEO", "sizeBytes": 187654321, "receivedBytes": 0, "chunkSize": 8388608 }
```

**2. Send chunks** — `PUT …/uploads/{uploadId}?offset={receivedBytes}`, body = the raw bytes (`Content-Type: application/octet-stream`, at most `chunkSize`). Returns the same session object with the new `receivedBytes`. `offset` must equal the server's `receivedBytes`, otherwise `409` — re-read the status and continue from there.

`GET …/uploads/{uploadId}` returns the session's current state (used to resume).

**3. Complete** — `POST …/uploads/{uploadId}/complete`. The server checks the real file (format by content; a video's true length via ffprobe), stores it and queues its conversion. **Response `201`:** a `MediaResponse` with `status: "PROCESSING"`. A rejection here (see "Media upload errors") ends the session.

`DELETE …/uploads/{uploadId}` cancels. Sessions untouched for 24 h (`app.media.upload-session-ttl`) are cleaned up automatically.

```bash
SLUG=a1b2c3d4...; B=http://localhost:8080/api/public/invitations/$SLUG/media
SIZE=$(stat -f %z clip.mov)   # Linux: stat -c %s
ID=$(curl -s -X POST $B/uploads -H 'Content-Type: application/json' \
  -d "{\"mediaType\":\"VIDEO\",\"filename\":\"clip.mov\",\"sizeBytes\":$SIZE}" | jq -r .uploadId)
curl -s -X PUT "$B/uploads/$ID?offset=0" -H 'Content-Type: application/octet-stream' --data-binary @clip.mov  # files ≤ 8 MB; larger: one PUT per chunk
curl -s -X POST $B/uploads/$ID/complete
```

---

## 4. Seating

Every table belongs to a **side**: `"BRIDE"`, `"GROOM"`, or `"HEAD"` (the single, fixed bride-and-groom table). An admin can only assign guests to, or manage tables on, their own side — the head table is open to either side for their own guests. Table numbering restarts per side (a "1D" and a "1B" coexist — bride tables get the `D` suffix, groom tables get `B`, per this deployment's couple); the head table has `tableNumber: null` and is always labeled `"Head Table"`.

Every table (and every guest) also belongs to a **hall**:

| Hall | Sides | Seeded tables |
|---|---|---|
| `"TASHKENT"` — Santini, 2 Oct 2026 18:00 | head, bride, groom | head + 1D–2D + 1B–2B |
| `"SAMARKAND"` — Bogishamol, 10 Oct 2026 14:00 | groom only | 1B–8B |

Numbering restarts per hall too, so both halls have their own "1B". The bride side can't see Samarkand at all: its tables are left out of every listing below, and any direct reference to one (or `?hall=SAMARKAND`) is a `404`, not a `403`. Each response row carries `"hall"`.

### `GET /api/seating/occupancy`

**Auth:** any admin

Seat counts only, no guest names — safe regardless of who owns what. Every table in every hall the caller can access.

**Response `200`:**
```json
[
  { "tableId": "a9b8c7d6-...", "hall": "SAMARKAND", "side": "GROOM", "tableNumber": 1, "label": "1B", "capacity": 14, "seatsTaken": 0, "seatsLeft": 14 },
  { "tableId": "b2c3d4e5-...", "hall": "TASHKENT", "side": "BRIDE", "tableNumber": 1, "label": "1D", "capacity": 12, "seatsTaken": 5, "seatsLeft": 7 },
  { "tableId": "c3d4e5f6-...", "hall": "TASHKENT", "side": "GROOM", "tableNumber": 1, "label": "1B", "capacity": 12, "seatsTaken": 0, "seatsLeft": 12 },
  { "tableId": "d1e2f3a4-...", "hall": "TASHKENT", "side": "HEAD", "tableNumber": null, "label": "Head Table", "capacity": 2, "seatsTaken": 1, "seatsLeft": 1 }
]
```

### `GET /api/seating/chart`

**Auth:** any admin

Every table across all three sides, in every hall the caller can access. **Own guests show by name; every other admin's guest is anonymized.** This is the isolation behavior validated live against the database (see `get_seating_chart_for_admin` in `schema.sql`) — unchanged by the side model. Side only governs *who can assign guests where*, not *who can see what*.

**Response `200`:**
```json
[
  {
    "tableId": "b2c3d4e5-...",
    "hall": "TASHKENT",
    "side": "BRIDE",
    "tableNumber": 1,
    "label": "1D",
    "capacity": 12,
    "seatsLeft": 7,
    "guestId": "3fa85f64-...",
    "displayName": "The Miller Family",
    "invitationUrl": "https://yourdomain.com/i/abc123",
    "partySize": 4,
    "ownGuest": true
  },
  {
    "tableId": "c3d4e5f6-...",
    "hall": "TASHKENT",
    "side": "GROOM",
    "tableNumber": 1,
    "label": "1B",
    "capacity": 12,
    "seatsLeft": 12,
    "guestId": null,
    "displayName": null,
    "invitationUrl": null,
    "partySize": null,
    "ownGuest": false
  }
]
```

(A table with no guests seated yet still appears, with `guestId`/`displayName`/`invitationUrl`/`partySize` all `null`. `invitationUrl` is also `null` whenever `ownGuest` is `false` — same anonymization as `displayName`.)

### `GET /api/seating/hall`

**Auth:** any admin

Everything the hall-map page needs for **one hall** in a single call: the head table, every bride table, every groom table (each already carrying its guest list, isolation rules applied exactly as in `/chart`), plus the caller's own unassigned guests invited to that hall, for populating a "drag from here" roster. Not paginated — returns all of them at once. Unassigned entries carry `ownerUsername` when the guest isn't the caller's own (super admin; a hall admin seeing another admin's guest), `null` otherwise.

**Query param:** `hall` — `TASHKENT` (default) or `SAMARKAND` (case-insensitive; `404` for the bride side). Samarkand has no head table (`headTable: null`) and no bride tables.

**Response `200`:**
```json
{
  "hall": "TASHKENT",
  "headTable": {
    "id": "d1e2f3a4-...", "side": "HEAD", "tableNumber": null, "label": "Head Table",
    "capacity": 2, "seatsLeft": 1,
    "guests": [{ "guestId": "e5f6a1b2-...", "displayName": "Reserved (1 seats)", "invitationUrl": null, "partySize": 1, "ownGuest": false }]
  },
  "brideTables": [
    {
      "id": "b2c3d4e5-...", "side": "BRIDE", "tableNumber": 1, "label": "1D",
      "capacity": 12, "seatsLeft": 7,
      "guests": [{ "guestId": "3fa85f64-...", "displayName": "The Miller Family", "invitationUrl": "https://yourdomain.com/i/abc123", "partySize": 4, "ownGuest": true }]
    }
  ],
  "groomTables": [
    { "id": "c3d4e5f6-...", "side": "GROOM", "tableNumber": 1, "label": "1B", "capacity": 12, "seatsLeft": 12, "guests": [] }
  ],
  "unassignedGuests": [
    { "id": "f6a1b2c3-...", "displayName": "Jane Doe", "partySize": 1, "isGroup": false, "ownerUsername": null, "invitationUrl": "https://yourdomain.com/i/def456" }
  ]
}
```

`invitationUrl` on a table guest is `null` whenever `ownGuest` is `false` (same anonymization as `displayName`); on an unassigned guest it's always populated — that list only ever contains guests the caller is allowed to manage.

### `POST /api/seating/tables`

**Auth:** admin (not super admin — a super admin has no side, and `403`s here)

Adds a table on the **caller's own side** of a hall. The table number is auto-assigned (next available for that hall and side) — never client-supplied, so two admins can't collide.

**Request:**
```json
{ "capacity": 14, "hall": "SAMARKAND" }
```
(`capacity` optional, defaults to 12 in Tashkent and 14 in Samarkand. `hall` optional, defaults to `"TASHKENT"`; `404` for a hall the caller can't access, `403` for a side that hall doesn't have — e.g. a super admin asking for a bride table in Samarkand.)

**Response `201`:**
```json
{ "id": "a1b2c3d4-...", "hall": "SAMARKAND", "side": "GROOM", "tableNumber": 9, "label": "9B", "capacity": 14, "seatsLeft": 14 }
```

### `DELETE /api/seating/tables/{tableId}`

**Auth:** admin — must own the table's side. `403` if it's another side's table, `403` if it's the head table (can't be removed at all), `404` if it's in a hall the caller can't access.

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

Optional form field `hall` (`TASHKENT` default, or `SAMARKAND`) invites every guest in the file to that hall — same access rule as `POST /api/guests`. The hall page sends whichever hall it's showing.

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
    "hall": null,
    "active": true,
    "guestCount": 12,
    "createdAt": "2026-09-01T09:00:00Z"
  },
  {
    "id": "9b2f3c4d-...",
    "username": "super_admin",
    "role": "SUPER_ADMIN",
    "side": null,
    "hall": null,
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
  "username": "sam_hall",
  "password": "a-real-password-here",
  "side": "GROOM",
  "hall": "SAMARKAND"
}
```
(`username`: 3–64 chars; `password`: 8–128 chars, validated but **not** hashed client-side — the server hashes it. `side`: `"BRIDE"` or `"GROOM"`, required — `400` on anything else. `hall`: optional — `"TASHKENT"` or `"SAMARKAND"` makes a hall admin (see section 1), omitted/`null` gives access to every hall of that side; `400` for an unknown hall or one the side doesn't have, e.g. a bride-side Samarkand admin.)

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

### `GET /api/super-admin/gallery-images`

The invitation page's "about us" gallery, in display order. Same response shape as the public `GET /api/public/gallery-images` (see section 8) — this is the same list, just reachable while signed in as super admin.

### `POST /api/super-admin/gallery-images`

**Content-Type:** `multipart/form-data` — field `file` (the photo) plus optional text fields `captionRu`, `captionUz`, `captionEn` (each defaults to `""` if omitted).

```bash
curl -X POST http://localhost:8080/api/super-admin/gallery-images \
  -H "Authorization: Bearer $SUPER_TOKEN" \
  -F "file=@our-photo.jpg" \
  -F "captionRu=Наша первая поездка" \
  -F "captionUz=Birinchi sayohatimiz" \
  -F "captionEn=Our first trip together"
```

**Response `201`:** a `GalleryImageResponse` (see section 8), appended to the end of the gallery (`displayOrder` = current max + 1).

### `PATCH /api/super-admin/gallery-images/{id}`

**Request:**
```json
{ "captionRu": "...", "captionUz": "...", "captionEn": "..." }
```
All three are required fields (an empty string is fine — it just means no caption in that language), max 255 chars each.

**Response `200`:** the updated `GalleryImageResponse`. **Response `404`** if `id` doesn't exist.

### `PATCH /api/super-admin/gallery-images/{id}/move-up` and `.../move-down`

Swaps this photo's `displayOrder` with its neighbor on that side. A no-op (still `204`) if it's already first/last — the admin UI just disables that button rather than erroring.

**Response:** `204 No Content`

### `DELETE /api/super-admin/gallery-images/{id}`

Deletes the row and the underlying file. **Response:** `204 No Content`. **Response `404`** if `id` doesn't exist.

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
  "hall": "TASHKENT",
  "tableNumber": 1,
  "tableLabel": "1D",
  "photosRemaining": 15,
  "videosRemaining": 4
}
```

`tableLabel` is the side-qualified form guests actually recognize — bride tables are `"{n}D"`, groom tables `"{n}B"`, the head table is `"Head Table"` (see the seating section below). Both `tableNumber` and `tableLabel` are `null` until the guest has a table assigned.

`language` drives which of the three languages (`en`/`ru`/`uz`) `invitation.html` actually renders in for this guest — see this project's frontend README.md.

`hall` picks which celebration the page describes — `"TASHKENT"` (Santini, 2 Oct 18:00) or `"SAMARKAND"` (Bogishamol, 10 Oct 14:00). Same design either way; only venue, date, time, map link and the copy mentioning them differ (`HALL_STRINGS` in `js/i18n.js`).

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

**Response `200`:** `MediaResponse[]` — this guest's own photos and videos, any status (with conversion progress while `PROCESSING`). No `guestName`/`tableLabel`, `originalUrl`, `guestId` or `processingError`.

### `GET /api/public/invitations/{slug}/feed`

Every guest's **READY `PUBLIC` photos and videos** in this guest's own hall (a Samarkand guest sees Samarkand, a Tashkent guest Tashkent), grouped by table exactly like `GET /api/media` (section 3). **Never `PRIVATE` items** — not even this guest's own (those are in `GET …/media` above); only their uploader and the admins see them. Other guests' items carry `guestName` but not `originalFilename`; `own: true` marks this guest's own items.

### `PATCH /api/public/invitations/{slug}/media/{mediaId}`

Lets a guest share or hide one of their own uploads. Scoped to that slug's guest like `DELETE` below — someone else's `mediaId` is a `404`.

```json
{ "visibility": "PUBLIC" }
```

**Response `200`:** the updated `MediaResponse`. `400` if `visibility` isn't `PUBLIC` or `PRIVATE`.

### Uploads — `POST /api/public/invitations/{slug}/media/uploads` …

Chunked and resumable — see **section 3a**.

### `DELETE /api/public/invitations/{slug}/media/{mediaId}`

Lets a guest remove their own upload (e.g. wrong photo). Scoped to that slug's guest — a guest can't delete another guest's media even if they guess a `mediaId`, because the lookup is always `(mediaId, guestId-resolved-from-slug)`, never a bare `mediaId`.

**Response:** `204 No Content`

---

## 7a. Media files (signed links — no login)

### `GET /api/public/media/{mediaId}/{variant}?exp={epochSeconds}&sig={signature}`

Serves a guest media file. `variant`: `display` (converted JPEG / MP4), `thumb` (thumbnail / poster JPEG) or `original` (the untouched upload, as an attachment with its original file name). Never build these URLs yourself — use `fileUrl` / `thumbUrl` / `originalUrl` from a `MediaResponse`.

- **The signature is the permission.** Who may see which file is decided when the list containing the link is built (above); the link is an HMAC over media id + variant + expiry, so a `display` link can't be edited into an `original` one. `<img>`/`<video>` can use it directly — no Bearer token needed.
- Links are valid for 12–18 h (`app.media.url-ttl`) and stay identical for hours, so browsers cache the files across page refreshes. **`403`** once expired or tampered with — reload the list for fresh links. **`404`** for a variant that doesn't exist yet (still `PROCESSING`).
- Supports `Range` requests (`206`), so videos seek and large downloads resume.

---

## 8. Public gallery (guest-facing — no login)

The invitation page's "about us" section — a super-admin-managed list of photos with a caption per language (see section 6). Site-wide, not scoped to any guest's slug.

### `GET /api/public/gallery-images`

**Response `200`:**
```json
[
  {
    "id": "49b4d672-...",
    "imageUrl": "/api/public/gallery-images/49b4d672-.../file",
    "captionRu": "Первые кольца были из скрепок",
    "captionUz": "Birinchi uzuklarimiz skrepkadan edi",
    "captionEn": "Our first rings were made of paperclips",
    "displayOrder": 0
  }
]
```
An empty array is a normal response — the invitation page hides the whole "about us" section when there's nothing to show.

### `GET /api/public/gallery-images/{id}/file`

Streams the photo's bytes with its real `Content-Type` (e.g. `image/jpeg`) — this is exactly the URL `imageUrl` above already points to, so the frontend never needs to build it itself.

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
| POST | `/api/guests/{id}/media/uploads` | admin |
| GET | `/api/guests/{id}/media/uploads/{uploadId}` | admin |
| PUT | `/api/guests/{id}/media/uploads/{uploadId}?offset=` | admin |
| POST | `/api/guests/{id}/media/uploads/{uploadId}/complete` | admin |
| DELETE | `/api/guests/{id}/media/uploads/{uploadId}` | admin |
| DELETE | `/api/guests/{id}/media/{mediaId}` | admin |
| GET | `/api/media` | admin |
| GET | `/api/media/storage` | admin |
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
| GET | `/api/super-admin/gallery-images` | super admin |
| POST | `/api/super-admin/gallery-images` | super admin |
| PATCH | `/api/super-admin/gallery-images/{id}` | super admin |
| PATCH | `/api/super-admin/gallery-images/{id}/move-up` | super admin |
| PATCH | `/api/super-admin/gallery-images/{id}/move-down` | super admin |
| DELETE | `/api/super-admin/gallery-images/{id}` | super admin |
| GET | `/api/public/invitations/{slug}` | none |
| GET | `/api/public/invitations/{slug}/media` | none |
| GET | `/api/public/invitations/{slug}/feed` | none |
| POST | `/api/public/invitations/{slug}/media/uploads` | none |
| GET | `/api/public/invitations/{slug}/media/uploads/{uploadId}` | none |
| PUT | `/api/public/invitations/{slug}/media/uploads/{uploadId}?offset=` | none |
| POST | `/api/public/invitations/{slug}/media/uploads/{uploadId}/complete` | none |
| DELETE | `/api/public/invitations/{slug}/media/uploads/{uploadId}` | none |
| PATCH | `/api/public/invitations/{slug}/media/{mediaId}` | none |
| DELETE | `/api/public/invitations/{slug}/media/{mediaId}` | none |
| GET | `/api/public/media/{mediaId}/{variant}` | signed link |
| GET | `/api/public/gallery-images` | none |
| GET | `/api/public/gallery-images/{id}/file` | none |
