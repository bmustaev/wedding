// super-admin.js — admin management + read-only drill-down into any admin's guest list.
import * as api from './api.js';
import { requireSuperAdmin, getUsername, logout } from './auth.js';
import {
  showError, clearBanner, setLoading, setEmpty,
  renderPager, openModal, closeModal, escapeHtml,
} from './ui.js';
import { applyStaticTranslations, initLanguageSwitcher, t } from './admin-i18n.js';

requireSuperAdmin();
applyStaticTranslations();
initLanguageSwitcher(document.getElementById('lang-switcher'));

document.getElementById('sidebar-username').textContent = getUsername() || '';
document.getElementById('logout-btn').addEventListener('click', logout);
document.getElementById('nav-back').addEventListener('click', () => { location.href = 'dashboard.html'; });

const panels = document.querySelectorAll('section[data-panel]');
function showPanel(name) {
  panels.forEach((p) => p.classList.toggle('active', p.dataset.panel === name));
}

// -----------------------------------------------------------------------
// Admin list
// -----------------------------------------------------------------------

const adminsContainer = document.getElementById('admins-container');
const adminsError = document.getElementById('admins-error');

async function loadAdmins() {
  clearBanner(adminsError);
  setLoading(adminsContainer, t('admins-loading'));
  try {
    const admins = await api.listAdmins();
    renderAdmins(admins);
  } catch (err) {
    showError(adminsError, err);
    adminsContainer.innerHTML = '';
  }
}

function renderAdmins(admins) {
  if (admins.length === 0) {
    setEmpty(adminsContainer, t('admins-empty'));
    return;
  }

  const rows = admins.map((a) => `
    <tr data-id="${a.id}">
      <td>${escapeHtml(a.username)} ${a.role === 'SUPER_ADMIN' ? `<span class="badge badge-super">${t('badge-super-admin')}</span>` : ''}</td>
      <td>${a.side ? escapeHtml(a.side) : '—'}</td>
      <td>${a.guestCount}</td>
      <td><span class="badge ${a.active ? 'badge-active' : 'badge-inactive'}">${a.active ? t('status-active') : t('status-disabled')}</span></td>
      <td class="cell-actions">
        <button type="button" class="btn btn-sm view-guests-btn">${t('view-guests-btn')}</button>
        ${a.role === 'SUPER_ADMIN' ? '' : `<button type="button" class="btn btn-sm toggle-active-btn">${a.active ? t('disable-btn') : t('enable-btn')}</button>`}
      </td>
    </tr>`).join('');

  adminsContainer.innerHTML = `
    <div class="table-wrap">
      <table>
        <thead><tr><th>${t('th-username')}</th><th>${t('th-side')}</th><th>${t('th-guests')}</th><th>${t('th-status')}</th><th></th></tr></thead>
        <tbody>${rows}</tbody>
      </table>
    </div>`;

  adminsContainer.querySelectorAll('.view-guests-btn').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const row = e.target.closest('tr');
      openAdminGuests(row.dataset.id, row.querySelector('td').textContent.trim());
    });
  });

  adminsContainer.querySelectorAll('.toggle-active-btn').forEach((btn) => {
    btn.addEventListener('click', async (e) => {
      const row = e.target.closest('tr');
      const admin = admins.find((a) => a.id === row.dataset.id);
      try {
        await api.setAdminActive(admin.id, !admin.active);
        loadAdmins();
      } catch (err) {
        showError(adminsError, err);
      }
    });
  });
}

document.getElementById('add-admin-btn').addEventListener('click', () => {
  document.getElementById('admin-form').reset();
  clearBanner(document.getElementById('admin-modal-error'));
  openModal(document.getElementById('admin-modal-backdrop'));
});
document.getElementById('admin-modal-close').addEventListener('click', () => closeModal(document.getElementById('admin-modal-backdrop')));
document.getElementById('admin-cancel-btn').addEventListener('click', () => closeModal(document.getElementById('admin-modal-backdrop')));

document.getElementById('admin-save-btn').addEventListener('click', async () => {
  const errorBanner = document.getElementById('admin-modal-error');
  clearBanner(errorBanner);
  const username = document.getElementById('admin-username').value.trim();
  const password = document.getElementById('admin-password').value;
  const side = document.getElementById('admin-side').value;

  try {
    await api.createAdmin({ username, password, side });
    closeModal(document.getElementById('admin-modal-backdrop'));
    loadAdmins();
  } catch (err) {
    showError(errorBanner, err);
  }
});

// -----------------------------------------------------------------------
// Drill-down: an admin's guests (read-only)
// -----------------------------------------------------------------------

let currentAdminId = null;
let currentAdminPage = 0;
const adminGuestsContainer = document.getElementById('admin-guests-container');
const adminGuestsError = document.getElementById('admin-guests-error');
const adminGuestsPager = document.getElementById('admin-guests-pager');

function openAdminGuests(adminId, adminLabel) {
  currentAdminId = adminId;
  document.getElementById('admin-guests-title').textContent = t('admin-guests-title-template', { name: adminLabel });
  showPanel('admin-guests');
  loadAdminGuests(0);
}

document.getElementById('back-to-admins-btn').addEventListener('click', () => showPanel('admins'));

async function loadAdminGuests(page) {
  currentAdminPage = page;
  clearBanner(adminGuestsError);
  setLoading(adminGuestsContainer, t('guests-loading'));
  try {
    const result = await api.getAdminGuests(currentAdminId, { page, size: 20, sort: 'displayName,asc' });
    renderAdminGuests(result);
    renderPager(adminGuestsPager, result, loadAdminGuests);
  } catch (err) {
    showError(adminGuestsError, err);
    adminGuestsContainer.innerHTML = '';
  }
}

function renderAdminGuests(pageResponse) {
  if (pageResponse.content.length === 0) {
    setEmpty(adminGuestsContainer, t('guests-empty-title'));
    return;
  }
  const rows = pageResponse.content.map((g) => `
    <tr>
      <td>${escapeHtml(g.displayName)}${g.isGroup ? ` <span class="badge">${t('badge-group')}</span>` : ''}</td>
      <td>${g.partySize}</td>
      <td>${g.tableNumber != null ? t('table-n', { n: g.tableNumber }) : '—'}</td>
      <td>${g.firstViewedAt ? t('status-viewed') : t('status-not-viewed')}</td>
    </tr>`).join('');

  adminGuestsContainer.innerHTML = `
    <div class="table-wrap">
      <table>
        <thead><tr><th>${t('th-name')}</th><th>${t('th-party')}</th><th>${t('th-table')}</th><th>${t('th-status')}</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>
    </div>`;
}

// -----------------------------------------------------------------------
// Init
// -----------------------------------------------------------------------

loadAdmins();
