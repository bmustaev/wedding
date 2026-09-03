// guest-editor.js — shared add/edit guest modal, used by dashboard.js (the
// guest list) and hall.js (double-click a chip). The modal's HTML markup
// must already exist in the page — identical block in dashboard.html and
// hall.html (same element ids) — this module only supplies the behavior.
import * as api from './api.js';
import { getSide, isSuperAdmin } from './auth.js';
import { showError, clearBanner, openModal, closeModal, escapeHtml, copyToClipboard } from './ui.js';
import { t, getAdminLanguage } from './admin-i18n.js';

const guestModalBackdrop = document.getElementById('guest-modal-backdrop');
const guestModalTitle = document.getElementById('guest-modal-title');
const guestModalError = document.getElementById('guest-modal-error');
const guestForm = document.getElementById('guest-form');
const guestIsGroup = document.getElementById('guest-is-group');
const guestPartySizeField = document.getElementById('guest-party-size-field');
const guestPartySize = document.getElementById('guest-party-size');
const guestLanguage = document.getElementById('guest-language');
const guestMembersField = document.getElementById('guest-members-field');
const guestMembersList = document.getElementById('guest-members-list');
const guestLinkField = document.getElementById('guest-link-field');
const guestInvitationUrl = document.getElementById('guest-invitation-url');
const guestMediaSection = document.getElementById('guest-media-section');
const guestDeleteBtn = document.getElementById('guest-delete-btn');
const guestTableSelect = document.getElementById('guest-table');
const guestMediaGrid = document.getElementById('guest-media-grid');
const guestMediaAllowance = document.getElementById('guest-media-allowance');

let editingGuestId = null;
let editingGuestTableId = null;
let onSavedCallback = () => {};
let onDeletedCallback = () => {};

function toggleGroupFields() {
  const isGroup = guestIsGroup.checked;
  guestPartySizeField.hidden = !isGroup;
  guestMembersField.hidden = !isGroup;
  if (!isGroup) {
    guestPartySize.value = 1;
    guestMembersList.innerHTML = '';
  }
}
guestIsGroup.addEventListener('change', toggleGroupFields);

function addMemberRow(value = '') {
  const row = document.createElement('div');
  row.className = 'member-input-row';
  row.innerHTML = `
    <input type="text" class="member-input" value="${escapeHtml(value)}" placeholder="${t('member-name-placeholder')}" />
    <button type="button" class="btn btn-sm remove-member-btn">${t('remove-member-btn')}</button>`;
  row.querySelector('.remove-member-btn').addEventListener('click', () => row.remove());
  guestMembersList.appendChild(row);
}
document.getElementById('guest-add-member-btn').addEventListener('click', () => addMemberRow());

function collectMembers() {
  return Array.from(guestMembersList.querySelectorAll('.member-input'))
    .map((i) => i.value.trim())
    .filter((v) => v.length > 0);
}

async function populateTableSelect(selectedTableId) {
  guestTableSelect.innerHTML = `<option value="">${t('not-assigned')}</option>`;
  try {
    const tables = await api.getSeatingOccupancy();
    const callerSide = getSide();
    // Only real numbered tables are pickable — never the head table as a
    // new choice — but if this guest is already seated there (assigned via
    // the hall page), keep showing it so an untouched save doesn't
    // silently unseat them.
    const usable = tables.filter((table) => {
      if (table.side === 'HEAD') return table.tableId === selectedTableId;
      return isSuperAdmin() || table.side === callerSide;
    });
    for (const table of usable) {
      const opt = document.createElement('option');
      opt.value = table.tableId;
      const isCurrent = table.tableId === selectedTableId;
      opt.textContent = `${table.label} — ${table.seatsLeft}${isCurrent ? ' + this guest' : ''} seat(s) left`;
      opt.selected = isCurrent;
      guestTableSelect.appendChild(opt);
    }
  } catch {
    // Non-fatal — the select just stays at "Not assigned" plus whatever we already have.
  }
}

/**
 * Call once per page, before the first openGuestEditor(). The callbacks let
 * each page decide what "refresh after a change" means for it — reload the
 * guest list on dashboard.html, reload the hall map on hall.html.
 */
export function initGuestEditor({ onSaved, onDeleted } = {}) {
  onSavedCallback = onSaved || (() => {});
  onDeletedCallback = onDeleted || onSavedCallback;
}

/** Opens the modal in-place — pass a guest id to edit, or null/undefined to add a new guest. */
export async function openGuestEditor(guestId) {
  editingGuestId = guestId || null;
  clearBanner(guestModalError);
  guestForm.reset();
  guestMembersList.innerHTML = '';
  guestIsGroup.checked = false;
  toggleGroupFields();
  guestDeleteBtn.hidden = !editingGuestId;
  guestLinkField.hidden = !editingGuestId;
  guestMediaSection.hidden = !editingGuestId;
  guestModalTitle.textContent = editingGuestId ? t('guest-modal-title-edit') : t('guest-modal-title-add');

  // New guests default to the admin's own current dashboard language
  // rather than always "en" — an admin working in Russian is far more
  // likely to be inviting Russian-speaking guests than English ones.
  if (!editingGuestId) {
    guestLanguage.value = getAdminLanguage();
  }

  editingGuestTableId = null;
  await populateTableSelect(null);
  openModal(guestModalBackdrop);

  if (editingGuestId) {
    try {
      const guest = await api.getGuest(editingGuestId);
      document.getElementById('guest-display-name').value = guest.displayName;
      guestIsGroup.checked = guest.isGroup;
      toggleGroupFields();
      guestPartySize.value = guest.partySize;
      (guest.groupMembers || []).forEach((m) => addMemberRow(m));
      document.getElementById('guest-greeting').value = guest.greetingMessage || '';
      guestLanguage.value = guest.language || 'en';
      guestInvitationUrl.value = guest.invitationUrl;
      editingGuestTableId = guest.tableId;
      await populateTableSelect(guest.tableId);
      await loadGuestMedia(editingGuestId);
    } catch (err) {
      showError(guestModalError, err);
    }
  }
}

document.getElementById('guest-modal-close').addEventListener('click', () => closeModal(guestModalBackdrop));
document.getElementById('guest-cancel-btn').addEventListener('click', () => closeModal(guestModalBackdrop));

document.getElementById('guest-copy-link-btn').addEventListener('click', async (e) => {
  try {
    await copyToClipboard(guestInvitationUrl.value, e.target, t('copied'));
  } catch (err) {
    showError(guestModalError, err);
  }
});

document.getElementById('guest-save-btn').addEventListener('click', async () => {
  clearBanner(guestModalError);
  const displayName = document.getElementById('guest-display-name').value.trim();
  if (!displayName) {
    showError(guestModalError, { message: t('display-name-required'), details: [] });
    return;
  }

  const payload = {
    displayName,
    isGroup: guestIsGroup.checked,
    partySize: guestIsGroup.checked ? Number(guestPartySize.value) || 1 : 1,
    groupMembers: guestIsGroup.checked ? collectMembers() : [],
    greetingMessage: document.getElementById('guest-greeting').value.trim() || null,
    language: guestLanguage.value.trim() || 'en',
  };

  try {
    let guest;
    if (editingGuestId) {
      guest = await api.updateGuest(editingGuestId, payload);
    } else {
      guest = await api.createGuest(payload);
    }

    const selectedTableId = guestTableSelect.value || null;
    if (selectedTableId !== editingGuestTableId) {
      if (selectedTableId) {
        await api.assignGuestTable(guest.id, selectedTableId);
      } else if (editingGuestTableId) {
        await api.unassignGuestTable(guest.id);
      }
    }

    closeModal(guestModalBackdrop);
    onSavedCallback();
  } catch (err) {
    showError(guestModalError, err);
  }
});

guestDeleteBtn.addEventListener('click', async () => {
  if (!editingGuestId) return;
  if (!confirm(t('confirm-delete-guest'))) return;
  try {
    await api.deleteGuest(editingGuestId);
    closeModal(guestModalBackdrop);
    onDeletedCallback();
  } catch (err) {
    showError(guestModalError, err);
  }
});

// --- guest media (within the modal) ------------------------------------

async function loadGuestMedia(guestId) {
  guestMediaGrid.innerHTML = '';
  try {
    const [items, allowance] = await Promise.all([
      api.listGuestMedia(guestId),
      api.getGuestMediaAllowance(guestId),
    ]);
    guestMediaAllowance.textContent = t('media-allowance-template', {
      photosUsed: allowance.photosUsed,
      photosTotal: allowance.photosUsed + allowance.photosRemaining,
      videosUsed: allowance.videosUsed,
      videosTotal: allowance.videosUsed + allowance.videosRemaining,
    });

    if (items.length === 0) {
      guestMediaGrid.innerHTML = `<p class="field-hint">${t('no-media')}</p>`;
      return;
    }
    for (const item of items) {
      const tile = document.createElement('div');
      tile.className = 'media-tile';
      tile.innerHTML = `${escapeHtml(item.originalFilename)}<button type="button" aria-label="${t('delete-aria')}">&times;</button>`;
      tile.querySelector('button').addEventListener('click', async () => {
        try {
          await api.deleteGuestMedia(guestId, item.id);
          loadGuestMedia(guestId);
        } catch (err) {
          showError(guestModalError, err);
        }
      });
      guestMediaGrid.appendChild(tile);
    }
  } catch (err) {
    showError(guestModalError, err);
  }
}

document.getElementById('guest-upload-photo').addEventListener('change', async (e) => {
  const file = e.target.files[0];
  if (!file || !editingGuestId) return;
  try {
    await api.uploadGuestMedia(editingGuestId, 'PHOTO', file);
    loadGuestMedia(editingGuestId);
  } catch (err) {
    showError(guestModalError, err);
  }
  e.target.value = '';
});

document.getElementById('guest-upload-video').addEventListener('change', async (e) => {
  const file = e.target.files[0];
  if (!file || !editingGuestId) return;
  try {
    await api.uploadGuestMedia(editingGuestId, 'VIDEO', file);
    loadGuestMedia(editingGuestId);
  } catch (err) {
    showError(guestModalError, err);
  }
  e.target.value = '';
});
