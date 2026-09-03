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
        location.href = 'login.html';
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

const mediaPath = (mediaType) => (mediaType === 'PHOTO' ? 'photos' : 'videos');

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

export function uploadGuestMedia(guestId, mediaType, file) {
  const form = new FormData();
  form.append('file', file);
  return request(`/api/guests/${guestId}/media/${mediaPath(mediaType)}`, { method: 'POST', body: form, isForm: true });
}

export function deleteGuestMedia(guestId, mediaId) {
  return request(`/api/guests/${guestId}/media/${mediaId}`, { method: 'DELETE' });
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

export function getHallView() {
  return request('/api/seating/hall');
}

export function createTable(capacity, side) {
  return request('/api/seating/tables', { method: 'POST', body: { capacity: capacity ?? null, side: side ?? null } });
}

export function deleteTable(tableId) {
  return request(`/api/seating/tables/${tableId}`, { method: 'DELETE' });
}

// -----------------------------------------------------------------------
// Bulk import
// -----------------------------------------------------------------------

export function importGuestsFile(file) {
  const form = new FormData();
  form.append('file', file);
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
// Public invitations (no auth)
// -----------------------------------------------------------------------

export function getPublicInvitation(slug) {
  return request(`/api/public/invitations/${slug}`, { auth: false });
}

export function listPublicMedia(slug) {
  return request(`/api/public/invitations/${slug}/media`, { auth: false });
}

export function uploadPublicMedia(slug, mediaType, file) {
  const form = new FormData();
  form.append('file', file);
  return request(`/api/public/invitations/${slug}/media/${mediaPath(mediaType)}`, { method: 'POST', body: form, isForm: true, auth: false });
}

export function deletePublicMedia(slug, mediaId) {
  return request(`/api/public/invitations/${slug}/media/${mediaId}`, { method: 'DELETE', auth: false });
}
