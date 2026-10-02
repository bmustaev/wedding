// auth.js — session storage and role guards. Token lives in localStorage;
// see README.md for the tradeoffs of that choice vs. a cookie-based session.

const STORAGE_KEY = 'wedding_admin_session';

export function saveSession({ token, adminId, username, role, side, hall }) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({ token, adminId, username, role, side, hall }));
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

/** The one hall a hall admin (e.g. sam_hall) is limited to; null for everyone else. */
export function getHall() {
  return getSession()?.hall ?? null;
}

export function isSuperAdmin() {
  return getRole() === 'SUPER_ADMIN';
}

/** A DJ login: one hall's playlist (dj.html) and nothing else. */
export function isDj() {
  return getRole() === 'DJ';
}

/** A banker login: one hall's bank table (bank.html) and nothing else. */
export function isBanker() {
  return getRole() === 'BANKER';
}

/** Where a signed-in user lands: a DJ on their playlist, a banker at the bank, everyone else on the guest list. */
export function homePage() {
  if (isDj()) return 'dj.html';
  if (isBanker()) return 'bank.html';
  return 'dashboard.html';
}

// Which sides have tables and guests in each hall — mirrors Hall.java.
// Samarkand is the groom side's alone; the bride side never sees it.
export const HALL_SIDES = { TASHKENT: ['BRIDE', 'GROOM'], SAMARKAND: ['GROOM'] };

/** Halls this admin can open, invite guests to and seat guests in (super admin: all of them; hall admin, DJ and banker: just theirs). */
export function getAccessibleHalls() {
  if (isDj() || isBanker()) return getHall() ? [getHall()] : [];
  return Object.keys(HALL_SIDES).filter((hall) => isSuperAdmin()
    || (HALL_SIDES[hall].includes(getSide()) && (!getHall() || getHall() === hall)));
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
 *  should never happen post-fix, but fail safe rather than silently misbehave).
 *  A DJ or banker is sent to their own page — every other admin page is off limits to them. */
export function requireAuth() {
  if (!isLoggedIn()) {
    location.href = 'login.html';
    return;
  }
  if (isDj() || isBanker()) {
    location.href = homePage();
    return;
  }
  if (!isSuperAdmin() && !getSide()) {
    clearSession();
    location.href = 'login.html';
  }
}

/** Call at the top of dj.html: admins and DJs (a DJ always has a hall). A banker goes to the bank. */
export function requireStaff() {
  if (!isLoggedIn()) {
    location.href = 'login.html';
    return;
  }
  if (isBanker()) {
    location.href = 'bank.html';
    return;
  }
  if (isDj() ? !getHall() : !isSuperAdmin() && !getSide()) {
    clearSession();
    location.href = 'login.html';
  }
}

/**
 * Call at the top of bank.html: admins and bankers (a banker always has a
 * hall). Not signed in: off to login, which comes back here afterwards —
 * a banker may arrive straight from scanning a guest's QR code.
 */
export function requireBankStaff() {
  if (!isLoggedIn()) {
    location.href = 'login.html?next=bank.html';
    return false;
  }
  if (isDj()) {
    location.href = 'dj.html';
    return false;
  }
  if (isBanker() ? !getHall() : !isSuperAdmin() && !getSide()) {
    clearSession();
    location.href = 'login.html?next=bank.html';
    return false;
  }
  return true;
}

/** Call at the top of super-admin.html. Redirects a non-super-admin back to their own dashboard. */
export function requireSuperAdmin() {
  requireAuth();
  if (!isSuperAdmin()) {
    location.href = 'dashboard.html';
  }
}
