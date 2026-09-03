// auth.js — session storage and role guards. Token lives in localStorage;
// see README.md for the tradeoffs of that choice vs. a cookie-based session.

const STORAGE_KEY = 'wedding_admin_session';

export function saveSession({ token, adminId, username, role, side }) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({ token, adminId, username, role, side }));
}

export function getSession() {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function getToken() {
  return getSession()?.token ?? null;
}

export function getRole() {
  return getSession()?.role ?? null;
}

export function getUsername() {
  return getSession()?.username ?? null;
}

export function getAdminId() {
  return getSession()?.adminId ?? null;
}

export function isLoggedIn() {
  return !!getToken();
}

export function getSide() {
  return getSession()?.side ?? null;
}

export function isSuperAdmin() {
  return getRole() === 'SUPER_ADMIN';
}

export function clearSession() {
  localStorage.removeItem(STORAGE_KEY);
}

export function logout() {
  clearSession();
  location.href = 'login.html';
}

/** Call at the top of any admin-only page. Redirects to login if not authenticated
 *  or if the session is malformed (e.g. a regular admin somehow missing a side —
 *  should never happen post-fix, but fail safe rather than silently misbehave). */
export function requireAuth() {
  if (!isLoggedIn()) {
    location.href = 'login.html';
    return;
  }
  if (!isSuperAdmin() && !getSide()) {
    clearSession();
    location.href = 'login.html';
  }
}

/** Call at the top of super-admin.html. Redirects a non-super-admin back to their own dashboard. */
export function requireSuperAdmin() {
  requireAuth();
  if (!isSuperAdmin()) {
    location.href = 'dashboard.html';
  }
}
