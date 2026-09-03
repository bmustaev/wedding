// dashboard.js — admin dashboard: guest CRUD, seating, bulk import.
import * as api from './api.js';
import { requireAuth, getRole, getUsername, isSuperAdmin, logout } from './auth.js';
import {
  showError, clearBanner, setLoading, setEmpty,
  renderPager, escapeHtml, copyToClipboard,
  ICON_COPY_LINK, ICON_EDIT, ICON_CHECK,
} from './ui.js';
import { initGuestEditor, openGuestEditor } from './guest-editor.js';
import { applyStaticTranslations, initLanguageSwitcher, t } from './admin-i18n.js';

requireAuth();
applyStaticTranslations();
initLanguageSwitcher(document.getElementById('lang-switcher'));

// -----------------------------------------------------------------------
// Sidebar / nav
// -----------------------------------------------------------------------

document.getElementById('sidebar-role').textContent =
  getRole() === 'SUPER_ADMIN' ? t('sidebar-role-super') : t('sidebar-role-admin');
document.getElementById('sidebar-username').textContent = getUsername() || '';
document.getElementById('logout-btn').addEventListener('click', logout);

if (isSuperAdmin()) {
  const link = document.getElementById('nav-super-admin');
  link.hidden = false;
  link.addEventListener('click', () => { location.href = 'super-admin.html'; });
}

document.getElementById('nav-hall').addEventListener('click', () => { location.href = 'hall.html'; });

const panels = document.querySelectorAll('section[data-panel]');
const navButtons = document.querySelectorAll('.sidebar-nav button[data-target]');
navButtons.forEach((btn) => {
  btn.addEventListener('click', () => {
    navButtons.forEach((b) => b.classList.remove('active'));
    btn.classList.add('active');
    panels.forEach((p) => p.classList.toggle('active', p.dataset.panel === btn.dataset.target));
    if (btn.dataset.target === 'import') loadImportHistory();
  });
});

// -----------------------------------------------------------------------
// Guests list
// -----------------------------------------------------------------------

let guestsPage = 0;
const guestsContainer = document.getElementById('guests-container');
const guestsError = document.getElementById('guests-error');
const guestsPager = document.getElementById('guests-pager');

async function loadGuests(page = 0) {
  guestsPage = page;
  clearBanner(guestsError);
  setLoading(guestsContainer, t('guests-loading'));
  try {
    const result = await api.listMyGuests({ page, size: 20, sort: 'displayName,asc' });
    renderGuestsTable(result);
    renderPager(guestsPager, result, loadGuests);
  } catch (err) {
    showError(guestsError, err);
    guestsContainer.innerHTML = '';
  }
}

function renderGuestsTable(pageResponse) {
  if (pageResponse.content.length === 0) {
    setEmpty(guestsContainer, t('guests-empty-title'), t('guests-empty-hint'));
    return;
  }

  const rows = pageResponse.content.map((g) => `
    <tr data-id="${g.id}">
      <td>${escapeHtml(g.displayName)}${g.isGroup ? ` <span class="badge">${t('badge-group')}</span>` : ''}</td>
      <td>${g.partySize}</td>
      <td>${g.tableNumber != null ? t('table-n', { n: g.tableNumber }) : '—'}</td>
      <td>${g.firstViewedAt ? t('status-viewed') : t('status-not-viewed')}</td>
      <td class="cell-actions">
        <button type="button" class="btn btn-sm btn-icon copy-link-btn" data-url="${escapeHtml(g.invitationUrl)}" title="${escapeHtml(t('copy-link-btn'))}" aria-label="${escapeHtml(t('copy-link-btn'))}">${ICON_COPY_LINK}</button>
        <button type="button" class="btn btn-sm btn-icon edit-guest-btn" title="${escapeHtml(t('edit-btn'))}" aria-label="${escapeHtml(t('edit-btn'))}">${ICON_EDIT}</button>
      </td>
    </tr>
  `).join('');

  guestsContainer.innerHTML = `
    <div class="table-wrap">
      <table>
        <thead>
          <tr><th>${t('th-name')}</th><th>${t('th-party')}</th><th>${t('th-table')}</th><th>${t('th-status')}</th><th></th></tr>
        </thead>
        <tbody>${rows}</tbody>
      </table>
    </div>`;

  guestsContainer.querySelectorAll('.edit-guest-btn').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const id = e.currentTarget.closest('tr').dataset.id;
      openGuestEditor(id);
    });
  });

  guestsContainer.querySelectorAll('.copy-link-btn').forEach((btn) => {
    btn.addEventListener('click', async (e) => {
      try {
        await copyToClipboard(e.currentTarget.dataset.url, e.currentTarget, ICON_CHECK);
      } catch (err) {
        showError(guestsError, err);
      }
    });
  });
}

document.getElementById('add-guest-btn').addEventListener('click', () => openGuestEditor(null));

// -----------------------------------------------------------------------
// Import
// -----------------------------------------------------------------------

const importError = document.getElementById('import-error');
const importResult = document.getElementById('import-result');
const importHistory = document.getElementById('import-history');

document.getElementById('import-submit-btn').addEventListener('click', async () => {
  const fileInput = document.getElementById('import-file');
  const file = fileInput.files[0];
  clearBanner(importError);
  importResult.innerHTML = '';
  if (!file) {
    showError(importError, { message: t('choose-file-first'), details: [] });
    return;
  }
  try {
    const result = await api.importGuestsFile(file);
    renderImportResult(result);
    fileInput.value = '';
    loadImportHistory();
  } catch (err) {
    showError(importError, err);
  }
});

function renderImportResult(result) {
  const rows = result.rows.map((r) => `
    <tr class="${r.errorMessage ? 'import-row-error' : 'import-row-ok'}">
      <td>${r.rowNumber}</td>
      <td>${escapeHtml(r.rawLine)}</td>
      <td>${r.errorMessage ? escapeHtml(r.errorMessage) : t('import-added')}</td>
    </tr>`).join('');

  importResult.innerHTML = `
    <div class="banner banner-success">
      ${t('import-success-banner', { success: result.successRows, total: result.totalRows, filename: escapeHtml(result.filename) })}
    </div>
    <div class="table-wrap">
      <table>
        <thead><tr><th>${t('import-result-th-line')}</th><th>${t('import-result-th-content')}</th><th>${t('import-result-th-result')}</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>
    </div>`;
}

async function loadImportHistory() {
  setLoading(importHistory, t('loading-generic'));
  try {
    const batches = await api.listImportBatches();
    if (batches.length === 0) {
      setEmpty(importHistory, t('import-empty'));
      return;
    }
    const rows = batches.map((b) => `
      <tr>
        <td>${escapeHtml(b.filename)}</td>
        <td>${t('import-history-result', { success: b.successRows, total: b.totalRows })}</td>
        <td>${b.status}</td>
      </tr>`).join('');
    importHistory.innerHTML = `
      <div class="table-wrap">
        <table>
          <thead><tr><th>${t('import-history-th-file')}</th><th>${t('import-history-th-result')}</th><th>${t('import-history-th-status')}</th></tr></thead>
          <tbody>${rows}</tbody>
        </table>
      </div>`;
  } catch (err) {
    importHistory.innerHTML = '';
    showError(importError, err);
  }
}

// -----------------------------------------------------------------------
// Init
// -----------------------------------------------------------------------

initGuestEditor({ onSaved: () => loadGuests(guestsPage) });
loadGuests(0);
