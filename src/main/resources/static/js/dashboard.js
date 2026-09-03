// dashboard.js — admin dashboard: guest CRUD, seating, bulk import.
import * as api from './api.js';
import { requireAuth, getRole, getUsername, isSuperAdmin, logout } from './auth.js';
import {
  showError, clearBanner, setLoading, setEmpty,
  renderPager, escapeHtml,
} from './ui.js';
import { initGuestEditor, openGuestEditor } from './guest-editor.js';

requireAuth();

// -----------------------------------------------------------------------
// Sidebar / nav
// -----------------------------------------------------------------------

document.getElementById('sidebar-role').textContent =
  getRole() === 'SUPER_ADMIN' ? 'Super admin' : 'Admin';
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
  setLoading(guestsContainer, 'Loading guests…');
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
    setEmpty(guestsContainer, 'No guests yet', 'Add your first guest, or import a list from a .txt file.');
    return;
  }

  const rows = pageResponse.content.map((g) => `
    <tr data-id="${g.id}">
      <td>${escapeHtml(g.displayName)}${g.isGroup ? ' <span class="badge">Group</span>' : ''}</td>
      <td>${g.partySize}</td>
      <td>${g.tableNumber != null ? `Table ${g.tableNumber}` : '—'}</td>
      <td>${g.firstViewedAt ? 'Viewed' : 'Not viewed yet'}</td>
      <td class="cell-actions">
        <button type="button" class="btn btn-sm edit-guest-btn">Edit</button>
      </td>
    </tr>
  `).join('');

  guestsContainer.innerHTML = `
    <div class="table-wrap">
      <table>
        <thead>
          <tr><th>Name</th><th>Party</th><th>Table</th><th>Status</th><th></th></tr>
        </thead>
        <tbody>${rows}</tbody>
      </table>
    </div>`;

  guestsContainer.querySelectorAll('.edit-guest-btn').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const id = e.target.closest('tr').dataset.id;
      openGuestEditor(id);
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
    showError(importError, { message: 'Choose a .txt file first.', details: [] });
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
      <td>${r.errorMessage ? escapeHtml(r.errorMessage) : 'Added'}</td>
    </tr>`).join('');

  importResult.innerHTML = `
    <div class="banner banner-success">
      ${result.successRows} of ${result.totalRows} guest(s) added from ${escapeHtml(result.filename)}.
    </div>
    <div class="table-wrap">
      <table>
        <thead><tr><th>Line</th><th>Content</th><th>Result</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>
    </div>`;
}

async function loadImportHistory() {
  setLoading(importHistory, 'Loading import history…');
  try {
    const batches = await api.listImportBatches();
    if (batches.length === 0) {
      setEmpty(importHistory, 'No imports yet');
      return;
    }
    const rows = batches.map((b) => `
      <tr>
        <td>${escapeHtml(b.filename)}</td>
        <td>${b.successRows}/${b.totalRows} added</td>
        <td>${b.status}</td>
      </tr>`).join('');
    importHistory.innerHTML = `
      <div class="table-wrap">
        <table>
          <thead><tr><th>File</th><th>Result</th><th>Status</th></tr></thead>
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
