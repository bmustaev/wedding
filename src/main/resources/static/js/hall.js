// hall.js — the wedding-hall seating map. Native HTML5 drag-and-drop, no
// library. Correctness-first approach: every successful drop just reloads
// the whole hall view from the server rather than patching local state,
// since this isn't a high-frequency interaction and the server is the
// only source of truth for capacity/side rules anyway.
import * as api from './api.js';
import { requireAuth, getRole, getUsername, getSide, isSuperAdmin, logout } from './auth.js';
import { showError, clearBanner, escapeHtml, copyToClipboard } from './ui.js';
import { initGuestEditor, openGuestEditor } from './guest-editor.js';

requireAuth();

const callerSide = getSide(); // 'BRIDE' | 'GROOM' | null (null shouldn't reach this page in practice)

// -----------------------------------------------------------------------
// Sidebar
// -----------------------------------------------------------------------

document.getElementById('sidebar-role').textContent =
  getRole() === 'SUPER_ADMIN' ? 'Super admin' : 'Admin';
document.getElementById('sidebar-username').textContent = getUsername() || '';
document.getElementById('logout-btn').addEventListener('click', logout);
document.getElementById('nav-dashboard').addEventListener('click', () => { location.href = 'dashboard.html'; });

if (isSuperAdmin()) {
  const link = document.getElementById('nav-super-admin');
  link.hidden = false;
  link.addEventListener('click', () => { location.href = 'super-admin.html'; });
}

// -----------------------------------------------------------------------
// Load + render
// -----------------------------------------------------------------------

const hallError = document.getElementById('hall-error');
const hallLoading = document.getElementById('hall-loading');
const hallMap = document.getElementById('hall-map');

async function loadHall() {
  clearBanner(hallError);
  hallLoading.hidden = false;
  hallMap.hidden = true;
  try {
    const view = await api.getHallView();
    renderHall(view);
    hallLoading.hidden = true;
    hallMap.hidden = false;
  } catch (err) {
    hallLoading.hidden = true;
    showError(hallError, err);
  }
}

function renderHall(view) {
  document.getElementById('hall-head-zone').innerHTML = view.headTable ? tableCardHtml(view.headTable, isSuperAdmin()) : '';
  document.getElementById('hall-bride-column').innerHTML =
    view.brideTables.map((t) => tableCardHtml(t, isSuperAdmin() || callerSide === 'BRIDE')).join('') + addTableButtonHtml('BRIDE');
  document.getElementById('hall-groom-column').innerHTML =
    view.groomTables.map((t) => tableCardHtml(t, isSuperAdmin() || callerSide === 'GROOM')).join('') + addTableButtonHtml('GROOM');

  renderUnassignedTray(view.unassignedGuests);
  wireTableCardEvents();
  wireAddTableButtons();
  wireCopyButtons();
}

function addTableButtonHtml(side) {
  if (!isSuperAdmin() && callerSide !== side) return '';
  return `<button type="button" class="btn btn-sm hall-add-table-btn" data-side="${side}">+ Add table</button>`;
}

function tableCardHtml(table, isOwnSide) {
  const chips = table.guests.map((g) => guestChipHtml(g)).join('');
  const removeBtn = isOwnSide && table.side !== 'HEAD'
    ? `<button type="button" class="hall-table-remove" data-table-id="${table.id}">Remove</button>`
    : '';
  return `
    <div class="hall-table-card" data-table-id="${table.id}" data-side="${table.side}">
      <div class="hall-table-header">
        <h3>${escapeHtml(table.label)}</h3>
        ${removeBtn}
      </div>
      <div class="hall-table-seats ${table.seatsLeft === 0 ? 'full' : ''}">${table.seatsLeft} of ${table.capacity} seats left</div>
      <div class="hall-chip-list" data-table-id="${table.id}">${chips}</div>
    </div>`;
}

function copyBtnHtml(guestId) {
  return `<button type="button" class="chip-copy-btn" draggable="true" data-guest-id="${guestId}">Copy link</button>`;
}

function guestChipHtml(guest) {
  if (!guest.ownGuest) {
    return `<div class="guest-chip reserved">${escapeHtml(guest.displayName)}</div>`;
  }
  return `
    <div class="guest-chip own" draggable="true" data-guest-id="${guest.guestId}">
      <span class="guest-chip-info">
        <span>${escapeHtml(guest.displayName)}</span>
        <span class="party-size">${guest.partySize}</span>
      </span>
      ${copyBtnHtml(guest.guestId)}
    </div>`;
}

function renderUnassignedTray(guests) {
  document.getElementById('unassigned-count').textContent = guests.length;
  const body = document.getElementById('unassigned-tray-body');
  body.innerHTML = guests.map((g) => `
    <div class="guest-chip own" draggable="true" data-guest-id="${g.id}">
      <span class="guest-chip-info">
        <span>${escapeHtml(g.displayName)}${g.ownerUsername ? ` <span class="field-hint" style="display:inline;">(${escapeHtml(g.ownerUsername)})</span>` : ''}</span>
        <span class="party-size">${g.partySize}</span>
      </span>
      ${copyBtnHtml(g.id)}
    </div>`).join('');
}

// -----------------------------------------------------------------------
// Tray collapse/expand
// -----------------------------------------------------------------------

const tray = document.getElementById('unassigned-tray');
const trayToggle = document.getElementById('unassigned-tray-toggle');
const trayCaret = document.getElementById('unassigned-tray-caret');
trayToggle.addEventListener('click', () => {
  tray.classList.toggle('collapsed');
  trayCaret.innerHTML = tray.classList.contains('collapsed') ? '&#9660;' : '&#9650;';
});

// -----------------------------------------------------------------------
// Drag and drop
// -----------------------------------------------------------------------

let draggedGuestId = null;

function wireTableCardEvents() {
  document.querySelectorAll('.guest-chip.own').forEach((chip) => {
    chip.addEventListener('dragstart', (e) => {
      draggedGuestId = chip.dataset.guestId;
      chip.classList.add('dragging');
      e.dataTransfer.effectAllowed = 'move';
      // Required for drag-and-drop to work reliably across browsers — Firefox
      // in particular won't fire drop events on the target at all without at
      // least one setData() call here. dragover/drop can't read this payload
      // back (browsers restrict that to the actual 'drop' event for security),
      // so the closure variable above is still what drives the visual feedback
      // and the actual assign/unassign call — this setData is purely what
      // makes the browser treat the gesture as a valid drag in the first place.
      e.dataTransfer.setData('text/plain', chip.dataset.guestId);
    });
    chip.addEventListener('dragend', () => {
      chip.classList.remove('dragging');
      draggedGuestId = null;
      document.querySelectorAll('.droppable-active, .droppable-denied')
        .forEach((el) => el.classList.remove('droppable-active', 'droppable-denied'));
    });
    chip.addEventListener('dblclick', () => {
      openGuestEditor(chip.dataset.guestId);
    });
  });

  document.querySelectorAll('.hall-table-card').forEach((card) => {
    const side = card.dataset.side;
    const allowed = isSuperAdmin() || side === 'HEAD' || side === callerSide;

    card.addEventListener('dragover', (e) => {
      if (!draggedGuestId) return;
      e.preventDefault();
      e.dataTransfer.dropEffect = allowed ? 'move' : 'none';
      card.classList.add(allowed ? 'droppable-active' : 'droppable-denied');
    });
    card.addEventListener('dragleave', () => {
      card.classList.remove('droppable-active', 'droppable-denied');
    });
    card.addEventListener('drop', async (e) => {
      e.preventDefault();
      card.classList.remove('droppable-active', 'droppable-denied');
      if (!draggedGuestId) return;
      if (!allowed) {
        showError(hallError, {
          message: 'You can only seat your own guests at tables on your own side.',
          details: [],
        });
        return;
      }
      await handleAssign(draggedGuestId, card.dataset.tableId);
    });
  });

  document.querySelectorAll('.hall-table-remove').forEach((btn) => {
    btn.addEventListener('click', async () => {
      if (!confirm('Remove this table? It must be empty first.')) return;
      try {
        await api.deleteTable(btn.dataset.tableId);
        loadHall();
      } catch (err) {
        showError(hallError, err);
      }
    });
  });
}

// Each chip is itself draggable="true" for seating; a copy button nested
// inside needs its own draggable="true" + dragstart guard so grabbing the
// button doesn't hijack the parent chip's drag gesture (a nested
// draggable="false" element doesn't stop that — dragstart still targets
// the nearest draggable ancestor).
function wireCopyButtons() {
  document.querySelectorAll('.chip-copy-btn').forEach((btn) => {
    btn.addEventListener('dragstart', (e) => {
      e.preventDefault();
      e.stopPropagation();
    });
    btn.addEventListener('click', async (e) => {
      e.stopPropagation();
      try {
        const guest = await api.getGuest(btn.dataset.guestId);
        copyToClipboard(guest.invitationUrl, btn, 'Copied');
      } catch (err) {
        showError(hallError, err);
      }
    });
  });
}

const trayBody = document.getElementById('unassigned-tray-body');
trayBody.addEventListener('dragover', (e) => {
  if (!draggedGuestId) return;
  e.preventDefault();
  e.dataTransfer.dropEffect = 'move';
  trayBody.classList.add('droppable-active');
});
trayBody.addEventListener('dragleave', () => trayBody.classList.remove('droppable-active'));
trayBody.addEventListener('drop', async (e) => {
  e.preventDefault();
  trayBody.classList.remove('droppable-active');
  if (!draggedGuestId) return;
  await handleUnassign(draggedGuestId);
});

async function handleAssign(guestId, tableId) {
  try {
    await api.assignGuestTable(guestId, tableId);
    loadHall();
  } catch (err) {
    showError(hallError, err);
  }
}

async function handleUnassign(guestId) {
  try {
    await api.unassignGuestTable(guestId);
    loadHall();
  } catch (err) {
    showError(hallError, err);
  }
}

// -----------------------------------------------------------------------
// Add table
// -----------------------------------------------------------------------

function wireAddTableButtons() {
  document.querySelectorAll('.hall-add-table-btn').forEach((btn) => {
    btn.addEventListener('click', async () => {
      try {
        await api.createTable(undefined, btn.dataset.side);
        loadHall();
      } catch (err) {
        showError(hallError, err);
      }
    });
  });
}

// -----------------------------------------------------------------------
// Add guest — opens the same full editor used for double-click/edit
// -----------------------------------------------------------------------

document.getElementById('hall-add-guest-btn').addEventListener('click', () => {
  openGuestEditor(null);
});

// -----------------------------------------------------------------------
// Import
// -----------------------------------------------------------------------

const importBackdrop = document.getElementById('import-modal-backdrop');
const importError = document.getElementById('hall-import-error');
const importResultEl = document.getElementById('hall-import-result');

document.getElementById('hall-import-btn').addEventListener('click', () => {
  clearBanner(importError);
  importResultEl.innerHTML = '';
  document.getElementById('hall-import-file').value = '';
  importBackdrop.hidden = false;
});
document.getElementById('import-modal-close').addEventListener('click', () => { importBackdrop.hidden = true; });
document.getElementById('hall-import-close-btn').addEventListener('click', () => { importBackdrop.hidden = true; loadHall(); });

document.getElementById('hall-import-upload-btn').addEventListener('click', async () => {
  const file = document.getElementById('hall-import-file').files[0];
  clearBanner(importError);
  if (!file) {
    showError(importError, { message: 'Choose a .txt file first.', details: [] });
    return;
  }
  try {
    const result = await api.importGuestsFile(file);
    importResultEl.innerHTML = `<div class="banner banner-success">${result.successRows} of ${result.totalRows} guest(s) added.</div>`;
  } catch (err) {
    showError(importError, err);
  }
});

// -----------------------------------------------------------------------
// Init
// -----------------------------------------------------------------------

initGuestEditor({ onSaved: loadHall, onDeleted: loadHall });
loadHall();
