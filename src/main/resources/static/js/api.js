// api.js — one fetch wrapper, one function per backend endpoint. All paths
// are relative (same-origin deployment — see README.md), so no base URL
// constant is needed. Every function throws the backend's own
// {status, error, message, details} shape on failure (see API.md's
// "Error format" section) — render `err.message` (and `err.details` for
// validation errors) rather than inventing new error copy per call site.

import { getToken, clearSession } from './auth.js';

async function request(path, { method = 'GET', body, isForm = false, auth = true } = {}) {
  const headers = {};
  if (!isForm && body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (auth) {
    const token = getToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
  }

  let res;
  try {
    res = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : (isForm ? body : JSON.stringify(body)),
    });
  } catch {
    throw { status: 0, error: 'Network Error', message: 'Could not reach the server. Check your connection and try again.', details: [] };
  }

  if (res.status === 204) return null;

  const text = await res.text();
  let payload = null;
  if (text) {
    try { payload = JSON.parse(text); } catch { payload = null; }
  }

  if (!res.ok) {
    if (res.status === 401 && auth) {
      clearSession();
      if (!location.pathname.endsWith('login.html')) {
        // A banker whose session ran out mid-scan comes back to the bank (bank.js keeps the scanned code).
        location.href = location.pathname.endsWith('bank.html') ? 'login.html?next=bank.html' : 'login.html';
      }
    }
    throw payload || { status: res.status, error: res.statusText, message: 'Something went wrong.', details: [] };
  }

  return payload;
}

function toQueryString(params) {
  const usp = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      usp.set(key, value);
    }
  }
  const qs = usp.toString();
  return qs ? `?${qs}` : '';
}

/**
 * PUTs one upload chunk with XMLHttpRequest — fetch() can't report upload
 * progress. Resolves/rejects exactly like request(): the parsed JSON, or
 * the backend's error shape ({status: 0} for a dropped connection).
 */
function sendChunk(path, blob, { auth, onProgress, signal }) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', path);
    xhr.setRequestHeader('Content-Type', 'application/octet-stream');
    if (auth) {
      const token = getToken();
      if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`);
    }
    xhr.upload.onprogress = (e) => onProgress?.(e.loaded);
    xhr.onload = () => {
      let payload = null;
      try { payload = xhr.responseText ? JSON.parse(xhr.responseText) : null; } catch { payload = null; }
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(payload);
        return;
      }
      if (xhr.status === 401 && auth) {
        clearSession();
        location.href = 'login.html';
      }
      reject(payload || { status: xhr.status, error: xhr.statusText, message: 'Something went wrong.', details: [] });
    };
    const networkError = () => reject({ status: 0, error: 'Network Error', message: 'Could not reach the server. Check your connection and try again.', details: [] });
    xhr.onerror = networkError;
    xhr.ontimeout = networkError;
    xhr.onabort = () => reject({ status: 0, error: 'Aborted', message: 'Cancelled', details: [] });
    signal?.addEventListener('abort', () => xhr.abort(), { once: true });
    xhr.send(blob);
  });
}

/** The start → chunks → complete calls under `base` (see uploader.js), for either audience. */
function uploadEndpoints(base, auth) {
  return {
    start: (body) => request(`${base}/uploads`, { method: 'POST', body, auth }),
    status: (uploadId) => request(`${base}/uploads/${uploadId}`, { auth }),
    putChunk: (uploadId, offset, blob, onProgress, signal) =>
      sendChunk(`${base}/uploads/${uploadId}?offset=${offset}`, blob, { auth, onProgress, signal }),
    complete: (uploadId) => request(`${base}/uploads/${uploadId}/complete`, { method: 'POST', auth }),
    cancel: (uploadId) => request(`${base}/uploads/${uploadId}`, { method: 'DELETE', auth }),
  };
}

// -----------------------------------------------------------------------
// Auth
// -----------------------------------------------------------------------

export function login(username, password) {
  return request('/api/auth/login', { method: 'POST', body: { username, password }, auth: false });
}

// -----------------------------------------------------------------------
// Guests (admin's own list)
// -----------------------------------------------------------------------

export function listMyGuests({ page = 0, size = 20, sort } = {}) {
  return request(`/api/guests${toQueryString({ page, size, sort })}`);
}

export function createGuest(payload) {
  return request('/api/guests', { method: 'POST', body: payload });
}

export function getGuest(guestId) {
  return request(`/api/guests/${guestId}`);
}

export function updateGuest(guestId, payload) {
  return request(`/api/guests/${guestId}`, { method: 'PATCH', body: payload });
}

export function deleteGuest(guestId) {
  return request(`/api/guests/${guestId}`, { method: 'DELETE' });
}

export function regenerateGuestPage(guestId) {
  return request(`/api/guests/${guestId}/regenerate-page`, { method: 'POST' });
}

export function assignGuestTable(guestId, tableId) {
  return request(`/api/guests/${guestId}/table`, { method: 'PUT', body: { tableId } });
}

export function unassignGuestTable(guestId) {
  return request(`/api/guests/${guestId}/table`, { method: 'DELETE' });
}

// -----------------------------------------------------------------------
// Media — admin side
// -----------------------------------------------------------------------

export function listGuestMedia(guestId) {
  return request(`/api/guests/${guestId}/media`);
}

export function getGuestMediaAllowance(guestId) {
  return request(`/api/guests/${guestId}/media/allowance`);
}

export function adminUploadEndpoints(guestId) {
  return uploadEndpoints(`/api/guests/${guestId}/media`, true);
}

export function deleteGuestMedia(guestId, mediaId) {
  return request(`/api/guests/${guestId}/media/${mediaId}`, { method: 'DELETE' });
}

/** Every photo or video of the guests this admin manages in one hall, grouped by table. */
export function listAllMedia(type, hall) {
  return request(`/api/media${toQueryString({ type, hall })}`);
}

export function getMediaStorage() {
  return request('/api/media/storage');
}

// -----------------------------------------------------------------------
// Seating
// -----------------------------------------------------------------------

export function getSeatingOccupancy() {
  return request('/api/seating/occupancy');
}

export function getSeatingChart() {
  return request('/api/seating/chart');
}

export function getHallView(hall) {
  return request(`/api/seating/hall${toQueryString({ hall })}`);
}

export function createTable(capacity, side, hall) {
  return request('/api/seating/tables', { method: 'POST', body: { capacity: capacity ?? null, side: side ?? null, hall: hall ?? null } });
}

export function deleteTable(tableId) {
  return request(`/api/seating/tables/${tableId}`, { method: 'DELETE' });
}

// -----------------------------------------------------------------------
// Bulk import
// -----------------------------------------------------------------------

export function importGuestsFile(file, hall) {
  const form = new FormData();
  form.append('file', file);
  if (hall) form.append('hall', hall);
  return request('/api/imports', { method: 'POST', body: form, isForm: true });
}

export function listImportBatches() {
  return request('/api/imports');
}

export function getImportBatch(batchId) {
  return request(`/api/imports/${batchId}`);
}

// -----------------------------------------------------------------------
// Super admin
// -----------------------------------------------------------------------

export function listAdmins() {
  return request('/api/super-admin/admins');
}

export function createAdmin(payload) {
  return request('/api/super-admin/admins', { method: 'POST', body: payload });
}

export function setAdminActive(adminId, active) {
  return request(`/api/super-admin/admins/${adminId}/active${toQueryString({ active })}`, { method: 'PATCH' });
}

export function getAdminGuests(adminId, { page = 0, size = 20, sort } = {}) {
  return request(`/api/super-admin/admins/${adminId}/guests${toQueryString({ page, size, sort })}`);
}

// -----------------------------------------------------------------------
// Super admin — invitation page's "about us" gallery
// -----------------------------------------------------------------------

export function listGalleryImagesAdmin() {
  return request('/api/super-admin/gallery-images');
}

export function createGalleryImage(file, { captionRu = '', captionUz = '', captionEn = '' } = {}) {
  const form = new FormData();
  form.append('file', file);
  form.append('captionRu', captionRu);
  form.append('captionUz', captionUz);
  form.append('captionEn', captionEn);
  return request('/api/super-admin/gallery-images', { method: 'POST', body: form, isForm: true });
}

export function updateGalleryImageCaptions(imageId, { captionRu, captionUz, captionEn }) {
  return request(`/api/super-admin/gallery-images/${imageId}`, { method: 'PATCH', body: { captionRu, captionUz, captionEn } });
}

export function moveGalleryImage(imageId, direction) {
  return request(`/api/super-admin/gallery-images/${imageId}/move-${direction}`, { method: 'PATCH' });
}

export function deleteGalleryImage(imageId) {
  return request(`/api/super-admin/gallery-images/${imageId}`, { method: 'DELETE' });
}

// -----------------------------------------------------------------------
// Quests and bets (admin side, one hall at a time — see activities.js)
// -----------------------------------------------------------------------

export function listQuests(hall) {
  return request(`/api/quests${toQueryString({ hall })}`);
}

export function createQuest(hall, payload) {
  return request(`/api/quests${toQueryString({ hall })}`, { method: 'POST', body: payload });
}

export function updateQuest(questId, payload) {
  return request(`/api/quests/${questId}`, { method: 'PUT', body: payload });
}

export function deleteQuest(questId) {
  return request(`/api/quests/${questId}`, { method: 'DELETE' });
}

export function listBets(hall) {
  return request(`/api/bets${toQueryString({ hall })}`);
}

export function createBet(hall, payload) {
  return request(`/api/bets${toQueryString({ hall })}`, { method: 'POST', body: payload });
}

export function updateBet(betId, payload) {
  return request(`/api/bets/${betId}`, { method: 'PUT', body: payload });
}

export function setBetStatus(betId, status) {
  return request(`/api/bets/${betId}/status`, { method: 'PATCH', body: { status } });
}

/** optionId null takes the result back. */
export function settleBet(betId, optionId) {
  return request(`/api/bets/${betId}/settle`, { method: 'PATCH', body: { optionId } });
}

export function deleteBet(betId) {
  return request(`/api/bets/${betId}`, { method: 'DELETE' });
}

export function getBetLeaderboard(hall) {
  return request(`/api/bets/leaderboard${toQueryString({ hall })}`);
}

// -----------------------------------------------------------------------
// Playlist — staff side (admins and the hall's DJ — see dj.js)
// -----------------------------------------------------------------------

export function listSongs(hall) {
  return request(`/api/playlist/songs${toQueryString({ hall })}`);
}

export function createSong(hall, payload) {
  return request(`/api/playlist/songs${toQueryString({ hall })}`, { method: 'POST', body: payload });
}

export function updateSong(songId, payload) {
  return request(`/api/playlist/songs/${songId}`, { method: 'PUT', body: payload });
}

export function deleteSong(songId) {
  return request(`/api/playlist/songs/${songId}`, { method: 'DELETE' });
}

export function getPlaylistBoard(hall) {
  return request(`/api/playlist/board${toQueryString({ hall })}`);
}

export function setSongOrderStatus(orderId, status) {
  return request(`/api/playlist/orders/${orderId}/status`, { method: 'PATCH', body: { status } });
}

/** likesCloseAt: venue wall-clock time, "YYYY-MM-DDTHH:mm" (UTC+5, no offset). */
export function updatePlaylistSettings(hall, likesCloseAt) {
  return request(`/api/playlist/settings${toQueryString({ hall })}`, { method: 'PUT', body: { likesCloseAt } });
}

// -----------------------------------------------------------------------
// Bank — quest payouts (admins and the hall's bankers — see bank.js)
// -----------------------------------------------------------------------

export function getBankBoard(hall) {
  return request(`/api/bank${toQueryString({ hall })}`);
}

/** The done quests (with their photos/videos) of the guest whose QR code was scanned, to check before paying. */
export function reviewBankCode(code) {
  return request('/api/bank/review', { method: 'POST', body: { code } });
}

/** The banker's verdict for that guest: pays out the `approved` quest ids, re-opens the `rejected` ones. */
export function payBankCode(code, approved, rejected) {
  return request('/api/bank/payouts', { method: 'POST', body: { code, approved, rejected } });
}

export function setBankPin(pin) {
  return request('/api/bank/pin', { method: 'PUT', body: { pin } });
}

// -----------------------------------------------------------------------
// Public invitations (no auth)
// -----------------------------------------------------------------------

export function getPublicInvitation(slug) {
  return request(`/api/public/invitations/${slug}`, { auth: false });
}

export function getGalleryImages() {
  return request('/api/public/gallery-images', { auth: false });
}

export function listPublicMedia(slug) {
  return request(`/api/public/invitations/${slug}/media`, { auth: false });
}

export function getFeed(slug) {
  return request(`/api/public/invitations/${slug}/feed`, { auth: false });
}

export function setPublicMediaVisibility(slug, mediaId, visibility) {
  return request(`/api/public/invitations/${slug}/media/${mediaId}`, { method: 'PATCH', body: { visibility }, auth: false });
}

export function publicUploadEndpoints(slug) {
  return uploadEndpoints(`/api/public/invitations/${slug}/media`, false);
}

export function deletePublicMedia(slug, mediaId) {
  return request(`/api/public/invitations/${slug}/media/${mediaId}`, { method: 'DELETE', auth: false });
}

export function getPublicQuests(slug) {
  return request(`/api/public/invitations/${slug}/quests`, { auth: false });
}

/** The bank QR code (an SVG) for <img src>; `bust` makes the browser fetch a fresh, unexpired one. */
export function publicBankQrUrl(slug, bust) {
  return `/api/public/invitations/${slug}/bank/qr?t=${encodeURIComponent(bust)}`;
}

/** A banker's PIN typed on the guest's phone, with their verdict: pays out `approved`, re-opens `rejected`. */
export function payWithBankPin(slug, pin, approved, rejected) {
  return request(`/api/public/invitations/${slug}/bank/pin`, { method: 'POST', body: { pin, approved, rejected }, auth: false });
}

export function getPublicBets(slug) {
  return request(`/api/public/invitations/${slug}/bets`, { auth: false });
}

export function voteBet(slug, betId, optionId) {
  return request(`/api/public/invitations/${slug}/bets/${betId}/vote`, { method: 'PUT', body: { optionId }, auth: false });
}

export function getPublicPlaylist(slug) {
  return request(`/api/public/invitations/${slug}/playlist`, { auth: false });
}

export function likeSong(slug, songId) {
  return request(`/api/public/invitations/${slug}/playlist/songs/${songId}/like`, { method: 'PUT', auth: false });
}

export function unlikeSong(slug, songId) {
  return request(`/api/public/invitations/${slug}/playlist/songs/${songId}/like`, { method: 'DELETE', auth: false });
}

export function orderSong(slug, songId) {
  return request(`/api/public/invitations/${slug}/playlist/orders`, { method: 'POST', body: { songId }, auth: false });
}

export function cancelSongOrder(slug, orderId) {
  return request(`/api/public/invitations/${slug}/playlist/orders/${orderId}`, { method: 'DELETE', auth: false });
}
