// media.js — the guest media page, linked from invitation.js's photo/video
// buttons (media.html?slug=…#photos | #videos | #feed). No auth: same
// slug-as-credential model as invitation.js (see API.md, section 7).
//
// Three tabs:
// - My photos / My videos: upload (chunked + resumable, js/uploader.js;
//   new photos go to all guests in the hall = PUBLIC, new videos are
//   PRIVATE = only the guest and admins), watch the upload and then the
//   server-side conversion progress, view, switch visibility, delete.
// - Guests' feed: every guest's PUBLIC photos and videos in this guest's
//   hall, grouped by table (GET /feed). The guest can delete only their own.
//
// File links in API responses are signed and stable for hours (see
// MediaUrlSigner), so re-rendering with fresh data reuses cached images.
import * as api from './api.js';
import { escapeHtml } from './ui.js';
import { normalizeLanguage, applyStaticTranslations } from './i18n.js';
import { createUploader, formatBytes } from './uploader.js';
import { setMediaLanguage, mt, applyMediaTranslations, formatEta, uploadErrorText } from './media-i18n.js';
import { setGuestLanguage } from './guest-i18n.js';
import { initGuestNav } from './guest-nav.js';

/** Mirrors app.media.max-video-seconds — the server enforces it either way. */
const MAX_VIDEO_SECONDS = 60;
const PROCESSING_POLL_MS = 2000;
const FEED_REFRESH_MS = 30000;

const slug = new URLSearchParams(location.search).get('slug');

const loadingEl = document.getElementById('loading');
const errorStateEl = document.getElementById('error-state');
const contentEl = document.getElementById('content');
const pageError = document.getElementById('page-error');

const TYPES = {
  PHOTO: {
    grid: document.getElementById('photos-grid'),
    empty: document.getElementById('photos-empty'),
    uploads: document.getElementById('photos-uploads'),
    quota: document.getElementById('photos-quota'),
    picker: document.getElementById('pick-photos'),
    pickBtn: document.getElementById('pick-photos-btn'),
    defaultVisibility: 'PUBLIC',
    visibilityNote: document.getElementById('photos-vis-note'),
    max: 15,
  },
  VIDEO: {
    grid: document.getElementById('videos-grid'),
    empty: document.getElementById('videos-empty'),
    uploads: document.getElementById('videos-uploads'),
    quota: document.getElementById('videos-quota'),
    picker: document.getElementById('pick-videos'),
    pickBtn: document.getElementById('pick-videos-btn'),
    defaultVisibility: 'PRIVATE',
    visibilityNote: document.getElementById('videos-vis-note'),
    max: 4,
  },
};

let ownMedia = [];
let feedGroups = [];
let feedSignature = '';
let pollTimer = 0;
let feedTimer = 0;
const batches = { PHOTO: newBatch(), VIDEO: newBatch() };

// Best guess until the guest's own `language` comes back from the API.
setMediaLanguage(navigator.language);
applyStaticTranslations(normalizeLanguage(navigator.language));
applyMediaTranslations();

const uploader = slug ? createUploader({
  endpoints: api.publicUploadEndpoints(slug),
  scope: slug,
  concurrency: 2,
  onChange: renderUploads,
  onComplete: (task) => {
    const batch = batches[task.mediaType];
    batch.done++;
    batch.doneBytes += task.total;
    refreshOwnMedia();
  },
}) : null;

async function init() {
  if (!slug) {
    showInvalidLink();
    return;
  }
  try {
    const invitation = await api.getPublicInvitation(slug);
    setMediaLanguage(invitation.language);
    applyStaticTranslations(normalizeLanguage(invitation.language), invitation.hall);
    applyMediaTranslations();
    setGuestLanguage(invitation.language);
    initGuestNav(slug, 'media');

    ownMedia = await api.listPublicMedia(slug);
    // The server knows the real ceiling; remaining + already used = max.
    TYPES.PHOTO.max = countOwn('PHOTO') + invitation.photosRemaining;
    TYPES.VIDEO.max = countOwn('VIDEO') + invitation.videosRemaining;
    document.getElementById('photos-hint').textContent = mt('photos-hint', { max: TYPES.PHOTO.max });
    document.getElementById('videos-hint').textContent = mt('videos-hint', { max: TYPES.VIDEO.max, seconds: MAX_VIDEO_SECONDS });
    renderVisibilityNotes();

    loadingEl.hidden = true;
    contentEl.hidden = false;
    renderOwnMedia();
    showTab(tabFromHash());
  } catch {
    showInvalidLink();
  }
}

function showInvalidLink() {
  loadingEl.hidden = true;
  errorStateEl.hidden = false;
}

// -----------------------------------------------------------------------
// Tabs (#photos | #videos | #feed — the invitation links straight to one)
// -----------------------------------------------------------------------

function tabFromHash() {
  const tab = location.hash.replace('#', '');
  return ['photos', 'videos', 'feed'].includes(tab) ? tab : 'photos';
}

function showTab(tab) {
  document.querySelectorAll('.media-tabs [data-tab]').forEach((btn) => {
    btn.setAttribute('aria-selected', String(btn.dataset.tab === tab));
  });
  document.querySelectorAll('.media-panel').forEach((panel) => {
    panel.hidden = panel.id !== `panel-${tab}`;
  });
  clearTimeout(feedTimer);
  if (tab === 'feed') loadFeed();
}

document.querySelectorAll('.media-tabs [data-tab]').forEach((btn) => {
  btn.addEventListener('click', () => {
    history.replaceState(null, '', `#${btn.dataset.tab}`);
    showTab(btn.dataset.tab);
  });
});
window.addEventListener('hashchange', () => showTab(tabFromHash()));

// -----------------------------------------------------------------------
// Picking files + quota
// -----------------------------------------------------------------------

function countOwn(type) {
  return ownMedia.filter((m) => m.mediaType === type).length;
}

function inFlight(type) {
  return uploader ? uploader.tasks().filter((t) => t.mediaType === type && t.state !== 'error').length : 0;
}

function remaining(type) {
  return TYPES[type].max - countOwn(type) - inFlight(type);
}

function renderQuota() {
  for (const [type, ui] of Object.entries(TYPES)) {
    const left = Math.max(0, remaining(type));
    ui.quota.textContent = left > 0
      ? mt('remaining', { n: left, max: ui.max })
      : mt(`limit-reached-${type.toLowerCase()}`);
    ui.quota.classList.toggle('at-cap', left <= 0);
    ui.pickBtn.classList.toggle('is-disabled', left <= 0);
  }
}

/** Above the picker: what the tiles' corner icon means and that tapping it switches. */
function renderVisibilityNotes() {
  for (const [type, ui] of Object.entries(TYPES)) {
    ui.visibilityNote.innerHTML = `
      <span class="vis-key"><span class="vis-icon">${peopleIcon()}</span>${escapeHtml(mt('vis-note-public'))}</span>
      <span class="vis-key"><span class="vis-icon is-private">${lockIcon()}</span>${escapeHtml(mt('vis-note-private'))}</span>
      <span class="vis-tap">${escapeHtml(mt(`vis-note-tap-${type}`))}</span>`;
  }
}

for (const [type, ui] of Object.entries(TYPES)) {
  ui.picker.addEventListener('change', () => {
    const files = Array.from(ui.picker.files || []);
    ui.picker.value = '';
    if (!files.length) return;
    hideError();
    const room = Math.max(0, remaining(type));
    if (files.length > room) {
      showError(mt(`err-LIMIT-${type}`) + ' ' + mt('remaining', { n: room, max: ui.max }));
    }
    const accepted = files.slice(0, room);
    if (!accepted.length) return;
    const batch = batches[type];
    if (!uploader.tasks().some((t) => t.mediaType === type)) Object.assign(batch, newBatch());
    batch.total += accepted.length;
    batch.totalBytes += accepted.reduce((sum, f) => sum + f.size, 0);
    uploader.add(accepted, type, ui.defaultVisibility);
  });
}

window.addEventListener('beforeunload', (e) => {
  if (uploader?.isBusy()) {
    e.preventDefault();
    e.returnValue = mt('leave-warning');
  }
});

// -----------------------------------------------------------------------
// Upload progress rows
// -----------------------------------------------------------------------

function newBatch() {
  return { total: 0, done: 0, totalBytes: 0, doneBytes: 0 };
}

const uploadRows = new Map(); // task.id → row element

function renderUploads(tasks) {
  for (const [type, ui] of Object.entries(TYPES)) {
    const mine = tasks.filter((t) => t.mediaType === type);
    const live = new Set(mine.map((t) => t.id));
    for (const [id, row] of uploadRows) {
      if (row.dataset.type === type && !live.has(id)) {
        URL.revokeObjectURL(row.dataset.preview || '');
        row.remove();
        uploadRows.delete(id);
      }
    }

    let overall = ui.uploads.querySelector('.upload-overall-wrap');
    const batch = batches[type];
    if (batch.total > 1 && mine.length) {
      if (!overall) {
        overall = document.createElement('div');
        overall.className = 'upload-overall-wrap';
        overall.innerHTML = '<div class="upload-overall"><span></span><span></span></div><div class="bar"><div class="bar-fill"></div></div>';
        ui.uploads.prepend(overall);
      }
      const sentBytes = batch.doneBytes + mine.reduce((sum, t) => sum + (t.state === 'error' ? 0 : t.loaded), 0);
      const pct = batch.totalBytes ? Math.min(100, Math.round(sentBytes * 100 / batch.totalBytes)) : 0;
      const [left, right] = overall.querySelectorAll('.upload-overall span');
      left.textContent = mt('overall', { done: batch.done, total: batch.total });
      right.textContent = `${pct}%`;
      overall.querySelector('.bar-fill').style.width = `${pct}%`;
    } else if (overall) {
      overall.remove();
    }

    for (const task of mine) {
      let row = uploadRows.get(task.id);
      if (!row) {
        row = createUploadRow(task);
        uploadRows.set(task.id, row);
        ui.uploads.appendChild(row);
      }
      updateUploadRow(row, task);
    }
  }
  renderQuota();
}

function createUploadRow(task) {
  const row = document.createElement('div');
  row.className = 'upload-row';
  row.dataset.type = task.mediaType;
  row.innerHTML = `
    <div class="upload-thumb">${task.mediaType === 'VIDEO' ? videoIcon() : cameraIcon()}</div>
    <div class="upload-body">
      <div class="upload-name"></div>
      <div class="bar"><div class="bar-fill"></div></div>
      <div class="upload-status"><span class="stat-left"></span><span class="stat-right"></span></div>
    </div>
    <div class="upload-actions"></div>`;
  row.querySelector('.upload-name').textContent = task.file.name;
  if (task.visibility === 'PRIVATE') {
    row.querySelector('.upload-name').insertAdjacentHTML('beforeend',
      `<span class="upload-vis">${lockIcon()}${escapeHtml(mt('vis-private'))}</span>`);
  }

  // Local preview where the browser can decode the file (not HEIC on Chrome — the icon stays).
  if (task.mediaType === 'PHOTO') {
    const url = URL.createObjectURL(task.file);
    row.dataset.preview = url;
    const img = new Image();
    img.className = 'upload-thumb';
    img.alt = '';
    img.onload = () => row.querySelector('.upload-thumb').replaceWith(img);
    img.src = url;
  }
  return row;
}

function updateUploadRow(row, task) {
  const bar = row.querySelector('.bar');
  const fill = row.querySelector('.bar-fill');
  const left = row.querySelector('.stat-left');
  const right = row.querySelector('.stat-right');
  const pct = task.total ? Math.round(task.loaded * 100 / task.total) : 0;
  row.classList.toggle('is-error', task.state === 'error');
  bar.hidden = task.state === 'queued' || task.state === 'error';
  bar.classList.toggle('is-indeterminate', task.state === 'checking' || task.state === 'finishing');
  fill.style.width = task.state === 'uploading' ? `${pct}%` : '';
  right.textContent = '';

  switch (task.state) {
    case 'queued':
      left.textContent = mt('st-queued');
      break;
    case 'checking':
      left.textContent = mt('st-checking');
      break;
    case 'uploading':
      if (task.retrying) {
        left.textContent = mt('st-retrying');
      } else {
        left.textContent = `${pct}% · ${mt('st-progress', { loaded: formatBytes(task.loaded), total: formatBytes(task.total) })}`;
        right.textContent = formatEta(task.etaSeconds);
      }
      break;
    case 'finishing':
      left.textContent = mt('st-finishing');
      break;
    case 'error':
      left.textContent = uploadErrorText(task.error, task.mediaType, MAX_VIDEO_SECONDS);
      break;
    default:
      left.textContent = '';
  }

  const actions = row.querySelector('.upload-actions');
  const wanted = task.state === 'error'
    ? (isRetryable(task.error) ? ['retry', 'remove'] : ['remove'])
    : ['cancel'];
  if (actions.dataset.kind !== wanted.join()) {
    actions.dataset.kind = wanted.join();
    actions.innerHTML = '';
    for (const action of wanted) {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'mini-btn';
      btn.textContent = mt(action);
      btn.addEventListener('click', () => {
        if (action === 'retry') uploader.retry(task);
        else if (action === 'cancel') uploader.cancel(task);
        else uploader.remove(task);
      });
      actions.appendChild(btn);
    }
  }
}

/** Network trouble and server hiccups are worth retrying; a rejected file isn't. */
function isRetryable(err) {
  return !err || err.status === 0 || err.status >= 500 || err.status === 409 && !/maximum/i.test(err.message || '');
}

// -----------------------------------------------------------------------
// Own media grid (+ conversion progress)
// -----------------------------------------------------------------------

async function refreshOwnMedia() {
  try {
    ownMedia = await api.listPublicMedia(slug);
    renderOwnMedia();
  } catch {
    // keep what's shown; the next poll or action retries
  }
}

function renderOwnMedia() {
  for (const [type, ui] of Object.entries(TYPES)) {
    const items = ownMedia.filter((m) => m.mediaType === type);
    reconcileTiles(ui.grid, items, ownTile);
    ui.empty.hidden = items.length > 0;
  }
  renderQuota();

  clearTimeout(pollTimer);
  if (ownMedia.some((m) => m.status === 'PROCESSING')) {
    pollTimer = setTimeout(refreshOwnMedia, PROCESSING_POLL_MS);
  }
}

/**
 * Rebuilds a grid in the new order but reuses tiles whose content didn't
 * change, so polling every 2 s never makes images flicker or re-download.
 */
function reconcileTiles(grid, items, build) {
  const existing = new Map(Array.from(grid.children).map((el) => [el.dataset.key, el]));
  const fragment = document.createDocumentFragment();
  for (const item of items) {
    const key = `${item.id}|${item.status}|${item.visibility}|${item.processingPercent}|${item.queuePosition}|${currentLangKey()}`;
    fragment.appendChild(existing.get(key) || build(item));
    fragment.lastChild.dataset.key = key;
  }
  grid.replaceChildren(fragment);
}

function currentLangKey() {
  return document.documentElement.lang;
}

function ownTile(item) {
  const tile = document.createElement('div');
  tile.className = 'tile';

  if (item.status === 'READY') {
    const open = document.createElement('button');
    open.type = 'button';
    open.className = 'tile-open';
    open.setAttribute('aria-label', item.mediaType === 'VIDEO' ? mt('play') : item.originalFilename || '');
    open.innerHTML = `<img src="${escapeHtml(item.thumbUrl)}" alt="" loading="lazy" decoding="async">`;
    if (item.mediaType === 'VIDEO') {
      open.insertAdjacentHTML('beforeend', `<span class="tile-play"></span><span class="tile-badge">${formatDuration(item.durationSeconds)}</span>`);
    }
    open.addEventListener('click', () => {
      // Looked up at click time: tiles are reused across polls, the list around them isn't.
      const ready = ownMedia.filter((m) => m.mediaType === item.mediaType && m.status === 'READY');
      openLightbox(ready.map((m) => viewerItem(m, mt('you'))), ready.findIndex((m) => m.id === item.id));
    });
    tile.appendChild(open);
  } else if (item.status === 'PROCESSING') {
    tile.appendChild(processingState(item));
  } else {
    tile.classList.add('is-failed');
    tile.innerHTML = `<div class="tile-state">${escapeHtml(mt('failed'))}</div>`;
  }

  tile.appendChild(visibilityButton(item));
  tile.appendChild(deleteButton(item));
  return tile;
}

/** Lock = only me (and the admins), people = everyone in the hall; tapping switches. */
function visibilityButton(item) {
  const isPrivate = item.visibility === 'PRIVATE';
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = `tile-visibility${isPrivate ? ' is-private' : ''}`;
  btn.title = mt(isPrivate ? 'vis-is-private' : 'vis-is-public');
  btn.setAttribute('aria-label', btn.title);
  btn.innerHTML = isPrivate ? lockIcon() : peopleIcon();
  btn.addEventListener('click', async (e) => {
    e.stopPropagation();
    btn.disabled = true;
    hideError();
    try {
      const updated = await api.setPublicMediaVisibility(slug, item.id, isPrivate ? 'PUBLIC' : 'PRIVATE');
      ownMedia = ownMedia.map((m) => (m.id === item.id ? { ...m, visibility: updated.visibility } : m));
      renderOwnMedia();
    } catch {
      btn.disabled = false;
      showError(mt('err-visibility'));
    }
  });
  return btn;
}

function processingState(item) {
  const state = document.createElement('div');
  state.className = 'tile-state';
  let label;
  let bar = '<div class="bar is-indeterminate"><div class="bar-fill"></div></div>';
  if (item.processingPercent != null && item.mediaType === 'VIDEO') {
    label = mt('processing', { percent: item.processingPercent });
    bar = `<div class="bar"><div class="bar-fill" style="width:${item.processingPercent}%"></div></div>`;
  } else if (item.queuePosition != null) {
    label = item.queuePosition > 0 ? mt('processing-ahead', { n: item.queuePosition }) : mt('processing-waiting');
  } else {
    label = mt('processing-photo');
  }
  state.innerHTML = `<span>${escapeHtml(label)}</span>${bar}`;
  return state;
}

function deleteButton(item) {
  const del = document.createElement('button');
  del.type = 'button';
  del.className = 'tile-delete';
  del.setAttribute('aria-label', mt('delete'));
  del.innerHTML = '&times;';
  del.addEventListener('click', async (e) => {
    e.stopPropagation();
    if (!confirm(mt(item.mediaType === 'VIDEO' ? 'confirm-delete-video' : 'confirm-delete-photo'))) return;
    try {
      await api.deletePublicMedia(slug, item.id);
      ownMedia = ownMedia.filter((m) => m.id !== item.id);
      renderOwnMedia();
      if (!document.getElementById('panel-feed').hidden) loadFeed();
    } catch (err) {
      showError(uploadErrorText(err, item.mediaType, MAX_VIDEO_SECONDS));
    }
  });
  return del;
}

// -----------------------------------------------------------------------
// Everyone's photos, by table
// -----------------------------------------------------------------------

async function loadFeed() {
  clearTimeout(feedTimer);
  try {
    const groups = await api.getFeed(slug);
    const signature = groups.map((g) => `${g.tableLabel}:${g.items.map((i) => i.id).join(',')}`).join('|');
    if (signature !== feedSignature || !document.getElementById('feed-groups').childElementCount) {
      feedSignature = signature;
      feedGroups = groups;
      renderFeed();
    }
  } catch {
    showError(mt('err-load'));
  }
  if (!document.getElementById('panel-feed').hidden) {
    feedTimer = setTimeout(() => {
      if (document.visibilityState === 'visible') loadFeed();
      else feedTimer = setTimeout(loadFeed, FEED_REFRESH_MS);
    }, FEED_REFRESH_MS);
  }
}

function tableTitle(group) {
  if (group.tableLabel == null) return mt('no-table');
  if (group.tableSide === 'HEAD') return mt('table-head');
  return mt('table', { label: group.tableLabel });
}

function renderFeed() {
  const feedEl = document.getElementById('feed-groups');
  feedEl.innerHTML = '';
  document.getElementById('feed-empty').hidden = feedGroups.length > 0;

  const flat = [];
  for (const group of feedGroups) {
    for (const item of group.items) flat.push({ item, title: tableTitle(group) });
  }
  const viewerItems = flat.map(({ item, title }) => viewerItem(item, `${item.own ? mt('you') : item.guestName} · ${title}`));

  let index = 0;
  for (const group of feedGroups) {
    const section = document.createElement('section');
    section.className = 'feed-group';
    section.innerHTML = `<h2><span>${escapeHtml(tableTitle(group))}</span><small>${group.items.length}</small></h2><div class="media-grid"></div>`;
    const grid = section.querySelector('.media-grid');
    for (const item of group.items) {
      const at = index++;
      const tile = document.createElement('div');
      tile.className = 'tile';
      tile.innerHTML = `
        <button type="button" class="tile-open" aria-label="${escapeHtml(item.guestName)}">
          <img src="${escapeHtml(item.thumbUrl)}" alt="" loading="lazy" decoding="async">
          ${item.mediaType === 'VIDEO' ? `<span class="tile-play"></span><span class="tile-badge is-top">${formatDuration(item.durationSeconds)}</span>` : ''}
          <span class="tile-name">${escapeHtml(item.own ? mt('you') : item.guestName)}</span>
        </button>`;
      tile.querySelector('.tile-open').addEventListener('click', () => openLightbox(viewerItems, at));
      if (item.own) tile.appendChild(deleteButton(item));
      grid.appendChild(tile);
    }
    feedEl.appendChild(section);
  }
}

// -----------------------------------------------------------------------
// Lightbox
// -----------------------------------------------------------------------

const lightbox = document.getElementById('lightbox');
const stage = document.getElementById('lightbox-stage');
const caption = document.getElementById('lightbox-caption');
const downloadLink = document.getElementById('lightbox-download');
const prevBtn = document.getElementById('lightbox-prev');
const nextBtn = document.getElementById('lightbox-next');
let viewerList = [];
let viewerIndex = 0;
let lastFocus = null;

function viewerItem(media, label) {
  return { type: media.mediaType, src: media.fileUrl, poster: media.thumbUrl, caption: label };
}

function openLightbox(items, index) {
  viewerList = items;
  lastFocus = document.activeElement;
  lightbox.hidden = false;
  document.body.style.overflow = 'hidden';
  showViewerItem(Math.max(0, index));
  document.getElementById('lightbox-close').focus();
}

function closeLightbox() {
  stage.innerHTML = '';
  lightbox.hidden = true;
  document.body.style.overflow = '';
  lastFocus?.focus?.();
}

function showViewerItem(index) {
  viewerIndex = index;
  const item = viewerList[index];
  if (item.type === 'VIDEO') {
    stage.innerHTML = `<video src="${escapeHtml(item.src)}" poster="${escapeHtml(item.poster)}" controls autoplay playsinline preload="metadata"></video>`;
  } else {
    stage.innerHTML = `<img src="${escapeHtml(item.src)}" alt="">`;
  }
  caption.textContent = item.caption || '';
  downloadLink.href = item.src;
  prevBtn.disabled = index <= 0;
  nextBtn.disabled = index >= viewerList.length - 1;
  prevBtn.hidden = nextBtn.hidden = viewerList.length < 2;
}

function step(delta) {
  const next = viewerIndex + delta;
  if (next >= 0 && next < viewerList.length) showViewerItem(next);
}

prevBtn.setAttribute('aria-label', mt('prev'));
nextBtn.setAttribute('aria-label', mt('next'));
prevBtn.addEventListener('click', () => step(-1));
nextBtn.addEventListener('click', () => step(1));
document.getElementById('lightbox-close').addEventListener('click', closeLightbox);
lightbox.addEventListener('click', (e) => {
  if (e.target === stage) closeLightbox();
});
document.addEventListener('keydown', (e) => {
  if (lightbox.hidden) return;
  if (e.key === 'Escape') closeLightbox();
  else if (e.key === 'ArrowLeft') step(-1);
  else if (e.key === 'ArrowRight') step(1);
});
let touchStartX = null;
stage.addEventListener('touchstart', (e) => { touchStartX = e.touches[0].clientX; }, { passive: true });
stage.addEventListener('touchend', (e) => {
  if (touchStartX == null) return;
  const dx = e.changedTouches[0].clientX - touchStartX;
  touchStartX = null;
  if (Math.abs(dx) > 50) step(dx < 0 ? 1 : -1);
});

// -----------------------------------------------------------------------
// Small helpers
// -----------------------------------------------------------------------

function showError(message) {
  pageError.textContent = message;
  pageError.hidden = false;
}

function hideError() {
  pageError.hidden = true;
}

function formatDuration(seconds) {
  if (seconds == null) return '';
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

function cameraIcon() {
  return '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" aria-hidden="true"><path d="M3 8.5h3l1.5-2h9L18 8.5h3v11H3z"/><circle cx="12" cy="13.5" r="3.6"/></svg>';
}

function lockIcon() {
  return '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="5" y="11" width="14" height="10" rx="1.5"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/></svg>';
}

function peopleIcon() {
  return '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="9" cy="8" r="3.2"/><path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6"/><circle cx="17" cy="9" r="2.6"/><path d="M16 14.2c2.8-.4 5 1.9 5 5.3"/></svg>';
}

function videoIcon() {
  return '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" aria-hidden="true"><rect x="3" y="6.5" width="12.5" height="11" rx="1"/><path d="M15.5 11l5.5-3v8l-5.5-3z"/></svg>';
}

init();
