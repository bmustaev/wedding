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

A **DJ** token (role `DJ`) reaches only `/api/playlist/**` (section 10), and a **banker** token (role `BANKER`) only `/api/bank/**` (section 11) — every other authenticated endpoint answers them with `403`, also before any controller code runs.

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
| 429 | Too many wrong bank PINs on one guest's phone (see "Bank errors") |
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

### Bank errors

The bank table (section 11) does the same, for the guest's quests page (`js/guest-i18n.js`) and the banker's page (`js/admin-i18n.js`).

| Status | `error` | When |
|---|---|---|
| 400 | `CODE_INVALID` | The scanned payout code is malformed, forged or expired (codes live 15 minutes; the guest page keeps showing a fresh one) |
| 400 | `WRONG_PIN` | No active banker/admin of the guest's hall has this PIN. Counts towards the lock |
| 429 | `PIN_LOCKED` | 5 wrong PINs in a row on this guest's phone — the PIN path is locked for 10 minutes (the QR code still works) |
| 409 | `PIN_TAKEN` | Setting a PIN someone else already uses |

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

`role` is `"SUPER_ADMIN"`, `"ADMIN"`, `"DJ"` or `"BANKER"`. `side` is `"BRIDE"` or `"GROOM"` for a regular admin, `null` for a super admin, a DJ and a banker. It's not read from a JWT claim — every request re-derives it fresh from the admin's row via `AdminPrincipal.getSide()`, so a side change takes effect on the very next request without needing a new token.

`hall` is set only for a **hall admin** (e.g. `sam_hall`: `"GROOM"`, `"SAMARKAND"`) — one limited to that single hall. Everywhere else in this API, a hall admin behaves as if the other hall didn't exist (`404`), an omitted `hall` parameter means their own hall instead of `TASHKENT`, and their "own guests" are **every guest their side has in that hall**, whoever added them (see section 2). Like `side`, it's re-derived from the admin's row on every request. A DJ always has a `hall`: the one whose playlist they work; so does a banker: the one whose bank table they work.

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
  "originalUrl": "/api/public/media/c3d4e5f6-.../original?exp=1790942400&sig=…",
  "questId": null,
  "questTitle": null
}
```

- `visibility`: `PUBLIC` (shown in the shared feed to every guest in the uploader's hall) or `PRIVATE` (only the uploader and the admins). The guest picks it when uploading and can switch it later (section 7). Admins see both kinds everywhere in this section.
- `status`: `PROCESSING` → `READY`, or `FAILED` (`processingError` says why — admins only). `fileUrl`/`thumbUrl` are `null` until `READY`; for a video, `thumbUrl` is the poster frame.
- While `PROCESSING`, the uploader and admins get `processingPercent` (video being converted now) or `queuePosition` (waiting; `0` = next).
- The `*Url`s are signed, expiring links — see section 7a. `originalUrl`, `guestId` and `processingError` are admin-only.
- `widthPx`/`heightPx` are as displayed (portrait phone video is taller than wide).
- `questId`/`questTitle`: set when the photo is a guest's answer to a quest (section 9); `null` otherwise, and again once the quest is deleted.

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
`visibility` (optional, `PUBLIC` | `PRIVATE`) is who besides the admins sees the finished file — absent means photos `PUBLIC`, videos `PRIVATE`. `questId` (optional, photos only) makes the photo that quest's answer (section 9): `404` if the quest isn't active in the guest's hall, `409` if the guest already has a photo for it, `400` for a video — checked again on complete. `durationSeconds` (videos, optional) is what the browser measured — over the limit fails here, before any bytes are sent. Also checked here: file size, the guest's cap (counting other uploads in progress), and free disk space.

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
  "hall": "SAMARKAND",
  "role": "ADMIN"
}
```
(`username`: 3–64 chars; `password`: 8–128 chars, validated but **not** hashed client-side — the server hashes it. `role`: `"ADMIN"` (default when absent), `"DJ"` or `"BANKER"`. For an admin, `side`: `"BRIDE"` or `"GROOM"`, required — `400` on anything else — and `hall`: optional — `"TASHKENT"` or `"SAMARKAND"` makes a hall admin (see section 1), omitted/`null` gives access to every hall of that side; `400` for an unknown hall or one the side doesn't have, e.g. a bride-side Samarkand admin. For a DJ or a banker, `side` is ignored and `hall` is required.)

```json
{ "username": "dj_tashkent", "password": "a-real-password-here", "role": "DJ", "hall": "TASHKENT" }
```

**Response `201`:** an `AdminSummaryResponse`. Only `ADMIN`, `DJ` and `BANKER` accounts can be created here — only a database operator can create another `SUPER_ADMIN`.

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

## 9. Quests and bets

Games for the guests of one hall. Both are **hall-wide content**: every admin who can open a hall (same rule as its seating map — section 4) manages that hall's quests and bets, whoever created them; a hall the caller can't open is a `404`, as is any quest or bet in it. Admin routes take `?hall=TASHKENT|SAMARKAND` like `GET /api/seating/hall` (absent = the caller's default hall). Guests reach them through their slug (section 7) and only ever see their own hall's.

### Quests — photo challenges

A guest completes a quest by uploading a photo for it: a normal chunked upload (section 3a) with `questId` in the start request. The photo counts towards the guest's 15-photo cap and follows the usual visibility rules (so by default it's in the hall's feed). One photo per quest and guest — deleting it (`DELETE …/media/{mediaId}`) re-opens the quest, unless it's already paid. Deleting a quest keeps its photos as ordinary photos (and its payouts). The database enforces photo-only, same-hall, one-per-guest (`trg_guest_media_quest_*`, `uk_guest_media_quest`).

Each quest is worth `reward` ducats, collected at the bank table (section 11). For a guest a quest is **`OPEN`** (no photo, or its conversion failed), **`DONE`** (photo in — ducats waiting at the bank) or **`PAID`** (collected). `PAID` is final: retaking the photo doesn't re-open it, and changing `reward` doesn't touch what was already paid.

#### `QuestResponse` (admins)

```json
{ "id": "5e1f…", "hall": "TASHKENT", "title": "Selfie with the groom", "description": "Both of you smiling", "active": true,
  "reward": 5, "completedCount": 12, "paidCount": 9, "createdAt": "2026-10-01T09:00:00Z" }
```
`completedCount`: guests in the hall with a photo for it (failed conversions don't count); `paidCount`: guests the bank paid it to. Inactive quests are hidden from guests but keep their photos.

#### `GET /api/quests?hall=` · `POST /api/quests?hall=` · `PUT /api/quests/{questId}` · `DELETE /api/quests/{questId}`

Create/update body (`active` optional: `true` on create, unchanged on update; `reward` likewise optional: `5` on create, unchanged on update):
```json
{ "title": "Selfie with the groom", "description": "Both of you smiling", "active": true, "reward": 5 }
```
`title` 1–150 chars, `description` ≤ 600, `reward` 1–1000. **Responses:** `QuestResponse[]`, `201 QuestResponse`, `200 QuestResponse`, `204`.

#### `GET /api/public/invitations/{slug}/quests`

The guest's ducats and the hall's **active** quests, oldest first, each with its `state`, the guest's own photo for it (`MediaResponse` as in `GET …/media`, or `null`) and, once paid, `paidAt` (`reward` is then what was paid):
```json
{
  "wallet": { "earned": 12, "collected": 7, "toCollect": 5 },
  "quests": [
    { "id": "5e1f…", "title": "Selfie with the groom", "description": "Both of you smiling", "reward": 7,
      "state": "PAID", "photo": { "id": "8e71…", "status": "READY", … }, "paidAt": "2026-10-02T15:33:00Z" },
    { "id": "9a02…", "title": "The cake before it's cut", "description": null, "reward": 5,
      "state": "DONE", "photo": { … }, "paidAt": null }
  ]
}
```
`wallet` in ducats: `earned` = `collected` + `toCollect`. `collected` counts every payout, also for quests hidden or deleted since; `toCollect` = the `DONE` quests.

### Bets — predictions, no money involved

A bet is a question with 2–10 options. Status: `OPEN` (guests pick, and may change their pick) → `CLOSED` (no more picks) → `SETTLED` (an admin marked the right option; every right pick scores a point on the hall's leaderboard). Settling closes the bet; reopening or taking the result back un-settles it. The database refuses picks on a bet that isn't open, from another hall, or for an option of another bet (`trg_bet_votes_*`, composite FK).

#### `BetResponse`

```json
{
  "id": "7a0c…", "hall": "TASHKENT", "question": "Who cries first?", "status": "SETTLED",
  "options": [
    { "id": "e1…", "label": "The bride", "votes": 14, "correct": true },
    { "id": "e2…", "label": "The groom", "votes": 9, "correct": false }
  ],
  "totalVotes": 23, "myOptionId": "e2…", "createdAt": "2026-10-01T09:00:00Z"
}
```
- `votes` per option: always for admins; for guests `null` while the bet is `OPEN` (so early picks don't steer later ones), filled once it's closed. `totalVotes` is always shown.
- `myOptionId`: the asking guest's pick (`null` if none, and always for admins).

#### Admin routes

| Route | Body | Response |
|---|---|---|
| `GET /api/bets?hall=` | — | `BetResponse[]`, oldest first |
| `POST /api/bets?hall=` | `{ "question": "Who cries first?", "options": ["The bride", "The groom"] }` | `201 BetResponse` (`OPEN`) |
| `PUT /api/bets/{betId}` | same as create | `200 BetResponse` |
| `PATCH /api/bets/{betId}/status` | `{ "status": "OPEN" }` or `"CLOSED"` | `200 BetResponse` |
| `PATCH /api/bets/{betId}/settle` | `{ "optionId": "e1…" }` — `null` takes the result back (→ `CLOSED`) | `200 BetResponse` |
| `DELETE /api/bets/{betId}` | — | `204` (picks go with it) |
| `GET /api/bets/leaderboard?hall=` | — | `LeaderboardResponse`, everyone with a right pick |

`PUT` renames the question and the options in order; adding or removing options is only allowed before anyone picked (`409` after). An `optionId` of another bet is a `404`.

#### `LeaderboardResponse`

```json
{ "settledBets": 3, "entries": [ { "rank": 1, "guestName": "The Miller Family", "correct": 3, "own": false } ], "me": null }
```
Guests with at least one right pick, best first; ties share a rank (1, 2, 2, 4). For guests, `entries` is the top 10, `own` marks their line and `me` is their own line (also when it's below the top 10; `null` without a right pick). Admins get everyone and `me: null`.

#### `GET /api/public/invitations/{slug}/bets`

Every bet in the guest's hall plus the leaderboard, in one call: `{ "bets": BetResponse[], "leaderboard": LeaderboardResponse }`.

#### `PUT /api/public/invitations/{slug}/bets/{betId}/vote`

```json
{ "optionId": "e2…" }
```
Picks, or changes the pick. **Response `200`:** the `BetResponse` (counts still hidden). `404` for a bet in another hall or an option of another bet, `409` once the bet isn't `OPEN`.

---

## 10. Playlist

The band's songs per hall, split by language and artist. **Hall-wide content** like quests and bets: every admin who can open the hall manages it, and so does that hall's **DJ** login (role `DJ`, created in section 6) — the only API a DJ can call. Inaccessible halls and their songs/orders are `404`. Staff routes take `?hall=` as in section 9 (absent = the DJ's own hall, or the caller's default hall).

- **Likes.** One per guest and song, until the hall's *likes close* time — 20:50 on each evening by default (`playlist_settings`, venue wall-clock time, UTC+5). The two most-liked songs at that moment are played after it; likes (and taking one back) are refused from then on, so the pair is final. Ties go to the song that got there first. Hidden songs and deleted guests' likes don't count.
- **Orders.** A guest buys a song for `app.playlist.song-price` (15) ducats. Ducats are paper props, so nothing is charged by the API: the order is `PENDING` until the guest pays the DJ in person, who marks it `PAID` (the band's queue) and later `PLAYED`; `CANCELLED` if it never happens. At most `app.playlist.max-pending-orders` (3) unpaid orders per guest.

The database enforces same hall, visible song, the freeze and the pending cap (`trg_song_likes_*`, `trg_song_orders_insert`, `uk_song_likes_song_guest`).

#### `SongResponse`

```json
{ "id": "53a0…", "artist": "AC/DC", "title": "Back in Black", "language": "EN", "active": true, "likeCount": 4, "likedByMe": true }
```
`language`: `EN`, `RU`, `UZ`, `TR`, `DE`, `FR` or `OTHER`. `likedByMe`: the asking guest's like (`null` for staff). Hidden songs (`active: false`) are left out for guests.

#### `SongOrderResponse`

```json
{ "id": "c41d…", "songId": "53a0…", "artist": "Quest Pistols", "title": "Белая стрекоза", "status": "PAID", "price": 15,
  "guestName": "Jane Doe", "tableLabel": "1D", "createdAt": "2026-10-02T15:10:00Z", "paidAt": "2026-10-02T15:14:00Z", "playedAt": null }
```
`guestName` / `tableLabel` (so the DJ can find the guest) are `null` for guests. `paidAt` / `playedAt` follow the status.

#### Staff routes (admins and the hall's DJ)

| Route | Body | Response |
|---|---|---|
| `GET /api/playlist/songs?hall=` | — | `SongResponse[]`, hidden ones too |
| `POST /api/playlist/songs?hall=` | `{ "artist": "AC/DC", "title": "Back in Black", "language": "EN", "active": true }` | `201 SongResponse` |
| `PUT /api/playlist/songs/{songId}` | same as create (`active` absent = unchanged) | `200 SongResponse` |
| `DELETE /api/playlist/songs/{songId}` | — | `204` (its likes and orders go with it — hide it to keep them) |
| `GET /api/playlist/board?hall=` | — | `PlaylistBoardResponse` |
| `PATCH /api/playlist/orders/{orderId}/status` | `{ "status": "PAID" }` — any of `PENDING`, `PAID`, `PLAYED`, `CANCELLED`, forwards or back | `200 SongOrderResponse` |
| `PUT /api/playlist/settings?hall=` | `{ "likesCloseAt": "2026-10-02T20:50" }` — venue time, no offset; later than now re-opens likes | `200 PlaylistBoardResponse` |

`artist` ≤ 150, `title` ≤ 200 chars; the same artist + title twice in a hall is a `409` (case-insensitive).

```json
{
  "hall": "TASHKENT", "songPrice": 15,
  "likesCloseAt": "2026-10-02T20:50:00+05:00", "likesOpen": true,
  "tonight": [ SongResponse, SongResponse ],
  "ranking": [ SongResponse, … ],
  "orders": [ SongOrderResponse, … ]
}
```
`tonight`: the (up to) two most-liked visible songs — still moving while `likesOpen`, final after. `ranking`: the top 10 by likes (hidden songs included, marked `active: false`). `orders`: every order in the hall, oldest first.

```bash
curl -X PATCH "http://localhost:8080/api/playlist/orders/$ORDER_ID/status" \
  -H "Authorization: Bearer $DJ_TOKEN" -H "Content-Type: application/json" -d '{"status":"PAID"}'
```

#### `GET /api/public/invitations/{slug}/playlist`

```json
{
  "songPrice": 15, "maxPendingOrders": 3,
  "likesCloseAt": "2026-10-02T20:50:00+05:00", "likesOpen": true,
  "tonight": [ SongResponse, … ],
  "songs": [ SongResponse, … ],
  "myOrders": [ SongOrderResponse, … ]
}
```
The guest's hall's visible songs with their likes, the current top two, and the guest's own orders (newest first).

#### `PUT` / `DELETE /api/public/invitations/{slug}/playlist/songs/{songId}/like`

Likes / takes the like back (repeating either is a no-op). **Response `200`:** the `SongResponse`. `404` for a song of another hall or a hidden one, `409` once likes are closed.

#### `POST /api/public/invitations/{slug}/playlist/orders`

```json
{ "songId": "53a0…" }
```
**Response `201`:** a `PENDING` `SongOrderResponse` — the guest now pays the DJ. Ordering a song that's already waiting for payment returns that order. `404` as for likes; `409` with more than 3 unpaid orders.

#### `DELETE /api/public/invitations/{slug}/playlist/orders/{orderId}`

Cancels the guest's own order while it's still `PENDING`. **Response `200`:** the `CANCELLED` order. `404` for someone else's order, `409` once paid (the DJ can still cancel it).

---

## 11. Bank — quest payouts

Where guests turn `DONE` quests (section 9) into ducats — the same paper coins the DJ takes for a song. A bank table with 1–2 trusted bankers per hall; each has a **banker** login (role `BANKER`, created in section 6, always one hall — the only API it can call is this one), and admins of the hall can do the same. Halls the caller can't open, and their guests, are `404`.

A visit pays out **every** `DONE` quest of the guest at once — they all become `PAID` — and the banker hands over the ducats the response shows. Either way in:

- **QR.** The guest's quests page shows a QR code (`GET …/bank/qr`) linking to `bank.html?code=…` on the invitation host (`app.invitation.base-url` with its last path segment swapped for `bank.html`). The banker scans it with their phone camera, signed in on that phone; the page calls `POST /api/bank/payouts`. The code names the guest, is HMAC-signed and expires after 15 minutes — it gives no access to the guest's pages.
- **PIN.** The banker types their own 4-digit PIN (set with `PUT /api/bank/pin`) on the guest's phone → `POST …/bank/pin`. The PIN says who paid. 5 wrong PINs in a row lock that guest's PIN path for 10 minutes.

Paying is idempotent: a second scan finds nothing left (`coins: 0`). The guest's row is locked while paying, so two bankers can't pay one quest twice; the database backs that up (`uk_quest_payouts_guest_quest`) and only accepts payouts of done quests of the guest's hall (`trg_quest_payouts_insert`).

#### `PayoutResponse`

```json
{ "guestName": "Jane Doe", "tableLabel": "1D", "hall": "TASHKENT", "coins": 12,
  "quests": ["Selfie with the groom", "The cake before it's cut"], "collected": 19,
  "paidBy": "bank_tashkent", "method": "QR", "paidAt": "2026-10-02T15:33:00Z" }
```
`coins`: ducats to hand over now — `0` = nothing new (then `quests` is empty, `paidAt` `null`). `collected`: everything the guest has collected, this visit included. `method`: `QR` or `PIN`.

#### Staff routes (admins and the hall's bankers)

| Route | Body | Response |
|---|---|---|
| `GET /api/bank?hall=` | — | `BankBoardResponse` (below) |
| `POST /api/bank/payouts` | `{ "code": "-k7hQqd_…" }` | `PayoutResponse`; `400 CODE_INVALID`; `404` for a guest of another hall |
| `PUT /api/bank/pin` | `{ "pin": "4821" }` (4 digits) | `204`; `409 PIN_TAKEN` |

```json
{ "hall": "TASHKENT", "pinSet": true, "paidOut": 340, "guestsPaid": 41, "outstanding": 55, "guestsWaiting": 9,
  "recent": [ { "id": "b7c2…", "guestName": "Jane Doe", "tableLabel": "1D", "quests": ["Selfie with the groom"],
                "coins": 7, "paidBy": "bank_tashkent", "method": "QR", "paidAt": "2026-10-02T15:33:00Z" } ] }
```
`pinSet`: whether the caller has a PIN. `paidOut`/`guestsPaid`: ducats handed out in the hall so far, and to how many guests; `outstanding`/`guestsWaiting`: ducats for done quests not collected yet. `recent`: the latest visits, newest first.

```bash
curl -X POST http://localhost:8080/api/bank/payouts \
  -H "Authorization: Bearer $BANKER_TOKEN" -H "Content-Type: application/json" -d '{"code":"-k7hQqd_…"}'
```

#### `GET /api/public/invitations/{slug}/bank/qr`

The QR code as `image/svg+xml` (`Cache-Control: no-store`), with a fresh payout code each time. The page reloads it every few minutes while there's something to collect.

#### `POST /api/public/invitations/{slug}/bank/pin`

```json
{ "pin": "4821" }
```
**Response `200`:** a `PayoutResponse`. `400 WRONG_PIN`, `429 PIN_LOCKED` (see "Bank errors"); `400` validation error if it isn't 4 digits.

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
| GET | `/api/quests?hall=` | admin |
| POST | `/api/quests?hall=` | admin |
| PUT | `/api/quests/{id}` | admin |
| DELETE | `/api/quests/{id}` | admin |
| GET | `/api/bets?hall=` | admin |
| POST | `/api/bets?hall=` | admin |
| PUT | `/api/bets/{id}` | admin |
| PATCH | `/api/bets/{id}/status` | admin |
| PATCH | `/api/bets/{id}/settle` | admin |
| DELETE | `/api/bets/{id}` | admin |
| GET | `/api/bets/leaderboard?hall=` | admin |
| GET | `/api/playlist/songs?hall=` | admin or DJ |
| POST | `/api/playlist/songs?hall=` | admin or DJ |
| PUT | `/api/playlist/songs/{id}` | admin or DJ |
| DELETE | `/api/playlist/songs/{id}` | admin or DJ |
| GET | `/api/playlist/board?hall=` | admin or DJ |
| PATCH | `/api/playlist/orders/{id}/status` | admin or DJ |
| PUT | `/api/playlist/settings?hall=` | admin or DJ |
| GET | `/api/bank?hall=` | admin or banker |
| POST | `/api/bank/payouts` | admin or banker |
| PUT | `/api/bank/pin` | admin or banker |
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
| GET | `/api/public/invitations/{slug}/quests` | none |
| GET | `/api/public/invitations/{slug}/bank/qr` | none |
| POST | `/api/public/invitations/{slug}/bank/pin` | none |
| GET | `/api/public/invitations/{slug}/bets` | none |
| PUT | `/api/public/invitations/{slug}/bets/{betId}/vote` | none |
| GET | `/api/public/invitations/{slug}/playlist` | none |
| PUT | `/api/public/invitations/{slug}/playlist/songs/{songId}/like` | none |
| DELETE | `/api/public/invitations/{slug}/playlist/songs/{songId}/like` | none |
| POST | `/api/public/invitations/{slug}/playlist/orders` | none |
| DELETE | `/api/public/invitations/{slug}/playlist/orders/{orderId}` | none |
| GET | `/api/public/media/{mediaId}/{variant}` | signed link |
| GET | `/api/public/gallery-images` | none |
| GET | `/api/public/gallery-images/{id}/file` | none |
