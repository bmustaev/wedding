// hall.js — the wedding-hall seating map. Native HTML5 drag-and-drop, no
// library. Correctness-first approach: every successful drop just reloads
// the whole hall view from the server rather than patching local state,
// since this isn't a high-frequency interaction and the server is the
// only source of truth for capacity/side rules anyway.
import * as api from './api.js';
import { requireAuth, getRole, getUsername, getSide, isSuperAdmin, logout } from './auth.js';
import { showError, clearBanner, escapeHtml, copyToClipboard } from './ui.js';
import { initGuestEditor, openGuestEditor } from './guest-editor.js';
import { applyStaticTranslations, initLanguageSwitcher, t } from './admin-i18n.js';

requireAuth();
applyStaticTranslations();
initLanguageSwitcher(document.getElementById('lang-switcher'));
document.getElementById('unassigned-tray-body').dataset.emptyLabel = t('everyone-seated');

const callerSide = getSide(); // 'BRIDE' | 'GROOM' | null (null shouldn't reach this page in practice)

// -----------------------------------------------------------------------
// Sidebar
// -----------------------------------------------------------------------

document.getElementById('sidebar-role').textContent =
  getRole() === 'SUPER_ADMIN' ? t('sidebar-role-super') : t('sidebar-role-admin');
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
  return `<button type="button" class="btn btn-sm hall-add-table-btn" data-side="${side}">${t('add-table-btn')}</button>`;
}

function tableCardHtml(table, isOwnSide) {
  const isHead = table.side === 'HEAD';
  const chips = table.guests.map((g) => guestChipHtml(g)).join('');
  const removeBtn = isOwnSide && !isHead
    ? `<button type="button" class="hall-table-remove" data-table-id="${table.id}">${t('remove-table-btn')}</button>`
    : '';
  // The head table is the couple's own — never a guest drop target (see
  // wireTableCardEvents' `allowed` check), so it gets its own copy instead
  // of "N of M seats left" / "Drop a guest here".
  const seatsLine = isHead
    ? t('head-table-reserved-label')
    : t('seats-left-template', { n: table.seatsLeft, capacity: table.capacity });
  const emptyLabel = isHead ? t('head-table-reserved-label') : t('drop-guest-here');
  return `
    <div class="hall-table-card" data-table-id="${table.id}" data-side="${table.side}">
      <div class="hall-table-header">
        <h3>${escapeHtml(table.label)}</h3>
        ${removeBtn}
      </div>
      <div class="hall-table-seats ${table.seatsLeft === 0 ? 'full' : ''}">${seatsLine}</div>
      <div class="hall-chip-list" data-table-id="${table.id}" data-empty-label="${emptyLabel}">${chips}</div>
    </div>`;
}

function copyBtnHtml(guestId) {
  return `<button type="button" class="chip-copy-btn" draggable="true" data-guest-id="${guestId}">${t('copy-link-btn')}</button>`;
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
// Tray collapse/expand + resize
// -----------------------------------------------------------------------

const tray = document.getElementById('unassigned-tray');
const trayToggle = document.getElementById('unassigned-tray-toggle');
const trayCaret = document.getElementById('unassigned-tray-caret');
const trayBody = document.getElementById('unassigned-tray-body');
const trayResizeHandle = document.getElementById('unassigned-tray-resize');
const hallContentEl = document.querySelector('.hall-content');

const TRAY_MIN_HEIGHT = 72; // px — roughly one row of chips
const TRAY_MAX_HEIGHT_RATIO = 0.7; // of viewport height, so it can't swallow the whole screen

// The tray occludes whatever's behind it (position:fixed), so hall-content
// needs matching bottom padding or the last table gets hidden under it.
// Collapsed shows only the header — .collapsed uses a transform, which
// doesn't change the element's own layout height, hence the branch below.
function updateContentPadding() {
  const occludingHeight = tray.classList.contains('collapsed')
    ? trayToggle.getBoundingClientRect().height
    : tray.getBoundingClientRect().height;
  hallContentEl.style.paddingBottom = `${occludingHeight + 32}px`;
}

trayToggle.addEventListener('click', () => {
  tray.classList.toggle('collapsed');
  trayCaret.innerHTML = tray.classList.contains('collapsed') ? '&#9660;' : '&#9650;';
  updateContentPadding();
});

trayResizeHandle.addEventListener('mousedown', (e) => {
  e.preventDefault();
  tray.classList.remove('collapsed');
  tray.classList.add('resizing');
  trayCaret.innerHTML = '&#9650;';
  const startY = e.clientY;
  const startHeight = trayBody.getBoundingClientRect().height;
  const maxHeight = window.innerHeight * TRAY_MAX_HEIGHT_RATIO;

  function onMove(moveEvent) {
    const delta = startY - moveEvent.clientY; // dragging up grows the tray
    const newHeight = Math.min(maxHeight, Math.max(TRAY_MIN_HEIGHT, startHeight + delta));
    trayBody.style.height = `${newHeight}px`;
    updateContentPadding();
  }
  function onUp() {
    tray.classList.remove('resizing');
    document.removeEventListener('mousemove', onMove);
    document.removeEventListener('mouseup', onUp);
  }
  document.addEventListener('mousemove', onMove);
  document.addEventListener('mouseup', onUp);
});

window.addEventListener('resize', updateContentPadding);
updateContentPadding();

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
    // The head table is reserved for the couple themselves — never a
    // valid seat for a guest, regardless of side or super-admin status.
    const isHead = side === 'HEAD';
    const allowed = !isHead && (isSuperAdmin() || side === callerSide);

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
          message: isHead ? t('confirm-head-table-reserved') : t('confirm-own-side-only'),
          details: [],
        });
        return;
      }
      await handleAssign(draggedGuestId, card.dataset.tableId);
    });
  });

  document.querySelectorAll('.hall-table-remove').forEach((btn) => {
    btn.addEventListener('click', async () => {
      if (!confirm(t('confirm-remove-table'))) return;
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
        await copyToClipboard(guest.invitationUrl, btn, t('copied'));
      } catch (err) {
        showError(hallError, err);
      }
    });
  });
}

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
    showError(importError, { message: t('choose-file-first'), details: [] });
    return;
  }
  try {
    const result = await api.importGuestsFile(file);
    importResultEl.innerHTML = `<div class="banner banner-success">${t('import-modal-success', { success: result.successRows, total: result.totalRows })}</div>`;
  } catch (err) {
    showError(importError, err);
  }
});

// -----------------------------------------------------------------------
// Init
// -----------------------------------------------------------------------

initGuestEditor({ onSaved: loadHall, onDeleted: loadHall });
loadHall();
