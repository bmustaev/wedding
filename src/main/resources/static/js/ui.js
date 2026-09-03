// ui.js — small shared rendering helpers used across dashboard.js,
// super-admin.js, and invitation.js, so error/pagination/modal handling
// isn't reimplemented per page. renderPager is admin-only (invitation.js
// has no pagination), so it's the one function here that pulls in
// admin-i18n.js — everything else stays audience-agnostic.
import { t } from './admin-i18n.js';

// Inline icons for compact row-action buttons (no icon font/library in this
// no-build-step frontend — see README.md). currentColor lets .btn's color
// rules keep applying.
export const ICON_COPY_LINK = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M10 13a5 5 0 0 0 7.07 0l2.83-2.83a5 5 0 0 0-7.07-7.07l-1.42 1.42"/><path d="M14 11a5 5 0 0 0-7.07 0L4.1 13.83a5 5 0 0 0 7.07 7.07l1.41-1.41"/></svg>';
export const ICON_EDIT = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>';
export const ICON_CHECK = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="20 6 9 17 4 12"/></svg>';

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

/**
 * Copies `text`, briefly swapping `btn`'s label to `copiedLabel` as feedback.
 *
 * navigator.clipboard is only defined in a secure context (HTTPS or
 * localhost) — this app is sometimes deployed over plain HTTP, where it's
 * undefined and would otherwise fail silently (an unawaited rejected
 * promise). Falls back to the legacy execCommand('copy') path via a
 * temporary off-screen textarea.
 *
 * Both of those are gated on the click's "user activation" — mobile
 * browsers in particular revoke it the moment any async work (e.g. a
 * network call) is awaited first, which silently breaks the whole chain.
 * So `text` must already be known synchronously when the caller invokes
 * this — never `await` something to produce it and pass the result in.
 *
 * Throws if every approach fails so callers can surface an error instead of
 * the button just doing nothing.
 */
export async function copyToClipboard(text, btn, copiedLabel = 'Copied') {
  let copied = false;
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      copied = true;
    } catch {
      // fall through to the legacy path below
    }
  }
  if (!copied) {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.top = '-1000px';
    textarea.style.left = '-1000px';
    document.body.appendChild(textarea);
    textarea.focus();
    textarea.select();
    try {
      copied = document.execCommand('copy');
    } catch {
      copied = false;
    } finally {
      textarea.remove();
    }
  }
  if (!copied) {
    throw new Error('Copy failed — copy the link manually.');
  }
  const original = btn.innerHTML;
  btn.innerHTML = copiedLabel;
  setTimeout(() => { btn.innerHTML = original; }, 1500);
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
