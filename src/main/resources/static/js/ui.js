// ui.js — small shared rendering helpers used across dashboard.js,
// super-admin.js, and invitation.js, so error/pagination/modal handling
// isn't reimplemented per page. renderPager is admin-only (invitation.js
// has no pagination), so it's the one function here that pulls in
// admin-i18n.js — everything else stays audience-agnostic.
import { t } from './admin-i18n.js';

/** Renders the backend's {message, details} error shape into a banner element. */
export function showError(container, err) {
  const message = err?.message || 'Something went wrong.';
  const details = Array.isArray(err?.details) ? err.details : [];
  container.innerHTML = '';
  container.className = 'banner banner-error';
  const p = document.createElement('p');
  p.textContent = message;
  p.style.margin = details.length ? '0 0 0.3em' : '0';
  container.appendChild(p);
  if (details.length) {
    const ul = document.createElement('ul');
    for (const d of details) {
      const li = document.createElement('li');
      li.textContent = d;
      ul.appendChild(li);
    }
    container.appendChild(ul);
  }
  container.hidden = false;
}

export function showSuccess(container, message) {
  container.className = 'banner banner-success';
  container.textContent = message;
  container.hidden = false;
}

export function clearBanner(container) {
  container.hidden = true;
  container.textContent = '';
}

export function setLoading(container, label = 'Loading…') {
  container.innerHTML = `<div class="loading-state"><span class="spinner"></span>${escapeHtml(label)}</div>`;
}

export function setEmpty(container, title, hint = '') {
  container.innerHTML = `
    <div class="empty-state">
      <p>${escapeHtml(title)}</p>
      ${hint ? `<p>${escapeHtml(hint)}</p>` : ''}
    </div>`;
}

/** Renders a "Page X of Y" pager with prev/next buttons into `container`, calling onChange(newPage). */
export function renderPager(container, pageResponse, onChange) {
  const { page, totalPages } = pageResponse;
  container.innerHTML = '';
  if (totalPages <= 1) return;

  const label = document.createElement('span');
  label.textContent = t('pager-label', { page: page + 1, total: totalPages });

  const prev = document.createElement('button');
  prev.className = 'btn btn-sm';
  prev.textContent = t('pager-prev');
  prev.disabled = page <= 0;
  prev.addEventListener('click', () => onChange(page - 1));

  const next = document.createElement('button');
  next.className = 'btn btn-sm';
  next.textContent = t('pager-next');
  next.disabled = page >= totalPages - 1;
  next.addEventListener('click', () => onChange(page + 1));

  container.append(prev, label, next);
}

export function openModal(modalEl) {
  modalEl.hidden = false;
}

export function closeModal(modalEl) {
  modalEl.hidden = true;
}

/** Copies `text`, briefly swapping `btn`'s label to `copiedLabel` as feedback. */
export async function copyToClipboard(text, btn, copiedLabel = 'Copied') {
  await navigator.clipboard.writeText(text);
  const original = btn.textContent;
  btn.textContent = copiedLabel;
  setTimeout(() => { btn.textContent = original; }, 1500);
}

export function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str ?? '';
  return div.innerHTML;
}

export function formatDate(iso) {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
  } catch {
    return iso;
  }
}
