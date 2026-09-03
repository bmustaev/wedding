// invitation.js — the guest-facing page. No auth: the slug in the URL is
// the credential (see API.md, section 7). Reads it from the query string
// or the /i/{slug} path — see this project's README.md for the details,
// and what to set app.invitation.base-url to on the backend to match.
import * as api from './api.js';
import { showError, clearBanner, escapeHtml } from './ui.js';

const pathMatch = location.pathname.match(/^\/i\/([^/]+)\/?$/);
const slug = new URLSearchParams(location.search).get('slug')
  || (pathMatch ? decodeURIComponent(pathMatch[1]) : null);

const loadingEl = document.getElementById('invite-loading');
const errorStateEl = document.getElementById('invite-error-state');
const contentEl = document.getElementById('invite-content');
const uploadError = document.getElementById('upload-error');

// -----------------------------------------------------------------------
// Fixed event details — same for every guest, so they don't come from the
// API. Keep this in sync with the copy in the page's markup if the venue
// or date ever changes.
// -----------------------------------------------------------------------
const WEDDING_DATE = new Date('2026-10-02T18:00:00+05:00');
const MAP_QUERY = 'Ресторан Santini, Ташкент, улица Чинабад, 61/1';
const GALLERY = [
  { src: '', caption: 'Первые кольца были из скрепок' },
  { src: '', caption: 'Бегаем вместе по утрам' },
  { src: '', caption: 'И гуляем, когда есть время' },
];

async function init() {
  if (!slug) {
    showInvalidLink();
    return;
  }
  try {
    const invitation = await api.getPublicInvitation(slug);
    renderInvitation(invitation);
    loadingEl.hidden = true;
    contentEl.hidden = false;
    loadGallery();
  } catch {
    showInvalidLink();
  }
}

function showInvalidLink() {
  loadingEl.hidden = true;
  errorStateEl.hidden = false;
}

function renderInvitation(invitation) {
  document.title = `${invitation.displayName} — приглашение на свадьбу Бобиржона и Дилнозы`;

  const greetingText = invitation.greetingMessage || 'Дорогие гости!';
  document.querySelectorAll('[data-greeting]').forEach((el) => { el.textContent = greetingText; });

  document.getElementById('seat-name').textContent = invitation.displayName;

  const membersEl = document.getElementById('seat-members');
  if (invitation.isGroup && invitation.groupMembers && invitation.groupMembers.length > 0) {
    membersEl.textContent = `Вместе с вами: ${invitation.groupMembers.join(', ')}`;
    membersEl.hidden = false;
  }

  const tableEl = document.getElementById('seat-table');
  if (invitation.tableLabel != null) {
    document.getElementById('seat-table-num').textContent = invitation.tableLabel;
    tableEl.hidden = false;
  }

  updateRemaining('photos-remaining', invitation.photosRemaining, 'фото');
  updateRemaining('videos-remaining', invitation.videosRemaining, 'видео');
  toggleFileInput('photo-btn', 'photo-input', invitation.photosRemaining);
  toggleFileInput('video-btn', 'video-input', invitation.videosRemaining);
}

function updateRemaining(elementId, remaining, label) {
  const el = document.getElementById(elementId);
  el.textContent = remaining > 0 ? `Осталось ${remaining} ${label}` : `Лимит ${label} исчерпан`;
  el.classList.toggle('at-cap', remaining <= 0);
}

function toggleFileInput(btnId, inputId, remaining) {
  const disabled = remaining <= 0;
  document.getElementById(inputId).disabled = disabled;
  document.getElementById(btnId).classList.toggle('is-disabled', disabled);
}

// -----------------------------------------------------------------------
// Uploaded photo/video gallery
// -----------------------------------------------------------------------

async function loadGallery() {
  try {
    const items = await api.listPublicMedia(slug);
    renderUploadedMedia(items);
  } catch (err) {
    showError(uploadError, err);
  }
}

function renderUploadedMedia(items) {
  const photoGallery = document.getElementById('photo-gallery');
  const videoGallery = document.getElementById('video-gallery');
  photoGallery.innerHTML = '';
  videoGallery.innerHTML = '';

  for (const item of items) {
    const tile = document.createElement('div');
    tile.className = 'gallery-tile';
    tile.innerHTML = `
      <span class="file-label">${escapeHtml(item.originalFilename)}</span>
      <button type="button" aria-label="Удалить">&times;</button>`;
    tile.querySelector('button').addEventListener('click', async () => {
      try {
        await api.deletePublicMedia(slug, item.id);
        const invitation = await api.getPublicInvitation(slug);
        updateRemaining('photos-remaining', invitation.photosRemaining, 'фото');
        updateRemaining('videos-remaining', invitation.videosRemaining, 'видео');
        toggleFileInput('photo-btn', 'photo-input', invitation.photosRemaining);
        toggleFileInput('video-btn', 'video-input', invitation.videosRemaining);
        loadGallery();
      } catch (err) {
        showError(uploadError, err);
      }
    });
    (item.mediaType === 'PHOTO' ? photoGallery : videoGallery).appendChild(tile);
  }
}

document.getElementById('photo-input').addEventListener('change', async (e) => {
  const file = e.target.files[0];
  if (!file) return;
  clearBanner(uploadError);
  try {
    await api.uploadPublicMedia(slug, 'PHOTO', file);
    const invitation = await api.getPublicInvitation(slug);
    updateRemaining('photos-remaining', invitation.photosRemaining, 'фото');
    toggleFileInput('photo-btn', 'photo-input', invitation.photosRemaining);
    loadGallery();
  } catch (err) {
    showError(uploadError, err);
  }
  e.target.value = '';
});

document.getElementById('video-input').addEventListener('change', async (e) => {
  const file = e.target.files[0];
  if (!file) return;
  clearBanner(uploadError);
  try {
    await api.uploadPublicMedia(slug, 'VIDEO', file);
    const invitation = await api.getPublicInvitation(slug);
    updateRemaining('videos-remaining', invitation.videosRemaining, 'видео');
    toggleFileInput('video-btn', 'video-input', invitation.videosRemaining);
    loadGallery();
  } catch (err) {
    showError(uploadError, err);
  }
  e.target.value = '';
});

// -----------------------------------------------------------------------
// Static content — doesn't depend on the guest, so it doesn't wait on init()
// -----------------------------------------------------------------------

function buildAboutGallery() {
  const box = document.getElementById('gallery');
  GALLERY.forEach((item) => {
    const fig = document.createElement('figure');
    const frame = document.createElement('div');
    frame.className = 'frame' + (item.src ? '' : ' empty');
    if (item.src) {
      const img = document.createElement('img');
      img.src = item.src;
      img.alt = item.caption;
      img.loading = 'lazy';
      img.onerror = () => { frame.classList.add('empty'); img.remove(); };
      frame.appendChild(img);
    }
    const cap = document.createElement('figcaption');
    cap.textContent = item.caption;
    fig.append(frame, cap);
    box.appendChild(fig);
  });
}

function buildMapLinks() {
  document.getElementById('map-ya').href = 'https://yandex.uz/maps/?text=' + encodeURIComponent(MAP_QUERY);
  document.getElementById('map-gg').href = 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent(MAP_QUERY);
}

function startCountdown() {
  const cd = document.getElementById('cd');
  const today = document.getElementById('cd-today');
  const el = {
    d: document.getElementById('cd-d'), dl: document.getElementById('cd-dl'),
    h: document.getElementById('cd-h'), hl: document.getElementById('cd-hl'),
    m: document.getElementById('cd-m'), ml: document.getElementById('cd-ml'),
    s: document.getElementById('cd-s'), sl: document.getElementById('cd-sl'),
  };

  function plural(n, forms) {
    const a = n % 10, b = n % 100;
    if (a === 1 && b !== 11) return forms[0];
    if (a >= 2 && a <= 4 && (b < 10 || b >= 20)) return forms[1];
    return forms[2];
  }

  let timer = null;

  function tick() {
    const left = WEDDING_DATE - new Date();
    if (left <= 0) {
      cd.hidden = true;
      today.hidden = false;
      if (timer) clearInterval(timer);
      return;
    }
    const s = Math.floor(left / 1000);
    const d = Math.floor(s / 86400);
    const h = Math.floor(s % 86400 / 3600);
    const m = Math.floor(s % 3600 / 60);
    const sec = s % 60;

    el.d.textContent = d;   el.dl.textContent = plural(d, ['день', 'дня', 'дней']);
    el.h.textContent = h;   el.hl.textContent = plural(h, ['час', 'часа', 'часов']);
    el.m.textContent = m;   el.ml.textContent = plural(m, ['минута', 'минуты', 'минут']);
    el.s.textContent = sec; el.sl.textContent = plural(sec, ['секунда', 'секунды', 'секунд']);
  }

  tick();
  if (today.hidden) timer = setInterval(tick, 1000);
}

buildAboutGallery();
buildMapLinks();
startCountdown();
init();
