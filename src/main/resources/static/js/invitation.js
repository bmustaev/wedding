// invitation.js — the guest-facing page. No auth: the slug in the URL is
// the credential (see API.md, section 7). Reads it from the query string
// or the /i/{slug} path — see this project's README.md for the details,
// and what to set app.invitation.base-url to on the backend to match.
//
// Supports Russian, Uzbek, and English (see i18n.js) — driven by the
// guest's own `language` field (set by the admin), with the browser's
// language as a best guess for the loading/invalid-link screens shown
// before that's known.
import * as api from './api.js';
import { showError, escapeHtml } from './ui.js';
import {
  normalizeLanguage, applyStaticTranslations, countdownWord,
  mapQueryFor, defaultGreetingFor, seatMembersText,
  remainingMediaText, documentTitleFor,
} from './i18n.js';

const pathMatch = location.pathname.match(/^\/i\/([^/]+)\/?$/);
const slug = new URLSearchParams(location.search).get('slug')
  || (pathMatch ? decodeURIComponent(pathMatch[1]) : null);

const loadingEl = document.getElementById('invite-loading');
const errorStateEl = document.getElementById('invite-error-state');
const contentEl = document.getElementById('invite-content');
const uploadError = document.getElementById('upload-error');

// Uploading isn't live yet (see media.html) — both buttons just link there,
// carrying the slug along so that page knows which guest this is.
if (slug) {
  const mediaHref = `/media.html?slug=${encodeURIComponent(slug)}`;
  document.getElementById('photo-btn').href = mediaHref;
  document.getElementById('video-btn').href = mediaHref;
}

// -----------------------------------------------------------------------
// Fixed event details — same for every guest, so they don't come from the
// API. Keep this in sync with the copy in the page's markup (and i18n.js)
// if the venue or date ever changes.
// -----------------------------------------------------------------------
const WEDDING_DATE = new Date('2026-10-02T18:00:00+05:00');

// Best guess until the guest's own `language` comes back from the API —
// used only for the loading/invalid-link screens, since nothing else is
// visible until then (#invite-content stays hidden).
let currentLang = normalizeLanguage(navigator.language);

async function init() {
  if (!slug) {
    showInvalidLink();
    return;
  }
  try {
    const invitation = await api.getPublicInvitation(slug);
    applyLanguage(invitation.language);
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

/** Re-applies every translated string for `lang`, including the pieces i18n.js can't drive via plain data-i18n (captions, map links). */
function applyLanguage(lang) {
  currentLang = normalizeLanguage(lang);
  applyStaticTranslations(currentLang);

  updateAboutGalleryCaptions();

  const query = mapQueryFor(currentLang);
  document.getElementById('map-ya').href = 'https://yandex.uz/maps/?text=' + encodeURIComponent(query);
  document.getElementById('map-gg').href = 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent(query);
}

function renderInvitation(invitation) {
  document.title = documentTitleFor(currentLang, invitation.displayName);

  const greetingText = invitation.greetingMessage || defaultGreetingFor(currentLang);
  document.querySelectorAll('[data-greeting]').forEach((el) => { el.textContent = greetingText; });

  document.getElementById('seat-name').textContent = invitation.displayName;

  const membersEl = document.getElementById('seat-members');
  if (invitation.isGroup && invitation.groupMembers && invitation.groupMembers.length > 0) {
    membersEl.textContent = seatMembersText(currentLang, invitation.groupMembers.join(', '));
    membersEl.hidden = false;
  }

  const tableEl = document.getElementById('seat-table');
  if (invitation.tableLabel != null) {
    document.getElementById('seat-table-num').textContent = invitation.tableLabel;
    tableEl.hidden = false;
  }

  updateRemaining('photos-remaining', 'photo', invitation.photosRemaining);
  updateRemaining('videos-remaining', 'video', invitation.videosRemaining);
  toggleFileButton('photo-btn', invitation.photosRemaining);
  toggleFileButton('video-btn', invitation.videosRemaining);
}

function updateRemaining(elementId, kind, remaining) {
  const el = document.getElementById(elementId);
  el.textContent = remainingMediaText(currentLang, kind, remaining);
  el.classList.toggle('at-cap', remaining <= 0);
}

function toggleFileButton(btnId, remaining) {
  document.getElementById(btnId).classList.toggle('is-disabled', remaining <= 0);
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
      <button type="button" aria-label="Delete">&times;</button>`;
    tile.querySelector('button').addEventListener('click', async () => {
      try {
        await api.deletePublicMedia(slug, item.id);
        const invitation = await api.getPublicInvitation(slug);
        updateRemaining('photos-remaining', 'photo', invitation.photosRemaining);
        updateRemaining('videos-remaining', 'video', invitation.videosRemaining);
        toggleFileButton('photo-btn', invitation.photosRemaining);
        toggleFileButton('video-btn', invitation.videosRemaining);
        loadGallery();
      } catch (err) {
        showError(uploadError, err);
      }
    });
    (item.mediaType === 'PHOTO' ? photoGallery : videoGallery).appendChild(tile);
  }
}

// -----------------------------------------------------------------------
// "About us" gallery — managed by the super admin (see super-admin.js),
// read here with no auth. Doesn't depend on the guest, so it doesn't wait
// on init(). Captions carry all three languages already, so a language
// switch (applyLanguage) only needs to re-run updateAboutGalleryCaptions,
// not re-fetch or rebuild the images.
// -----------------------------------------------------------------------

let aboutGalleryImages = [];

async function loadAboutGallery() {
  try {
    aboutGalleryImages = await api.getGalleryImages();
  } catch {
    aboutGalleryImages = [];
  }
  buildAboutGallery();
}

function captionFor(image, lang) {
  const code = normalizeLanguage(lang);
  const caption = code === 'ru' ? image.captionRu : code === 'uz' ? image.captionUz : image.captionEn;
  return caption || '';
}

function buildAboutGallery() {
  const section = document.querySelector('.about');
  const box = document.getElementById('gallery');
  section.hidden = aboutGalleryImages.length === 0;
  box.innerHTML = '';
  aboutGalleryImages.forEach((image) => {
    const fig = document.createElement('figure');
    const frame = document.createElement('div');
    frame.className = 'frame';
    const img = document.createElement('img');
    img.src = image.imageUrl;
    img.loading = 'lazy';
    img.onerror = () => { frame.classList.add('empty'); img.remove(); };
    frame.appendChild(img);
    const figcaption = document.createElement('figcaption');
    figcaption.textContent = captionFor(image, currentLang);
    fig.append(frame, figcaption);
    box.appendChild(fig);
  });
}

function updateAboutGalleryCaptions() {
  document.querySelectorAll('#gallery figcaption').forEach((el, i) => {
    el.textContent = captionFor(aboutGalleryImages[i], currentLang);
  });
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

    // currentLang is read fresh each tick, so a language resolved after
    // this timer started (see applyLanguage()) still takes effect within
    // a second, without needing to restart the interval.
    el.d.textContent = d;   el.dl.textContent = countdownWord(currentLang, 'day', d);
    el.h.textContent = h;   el.hl.textContent = countdownWord(currentLang, 'hour', h);
    el.m.textContent = m;   el.ml.textContent = countdownWord(currentLang, 'minute', m);
    el.s.textContent = sec; el.sl.textContent = countdownWord(currentLang, 'second', sec);
  }

  tick();
  if (today.hidden) timer = setInterval(tick, 1000);
}

loadAboutGallery();
applyLanguage(currentLang);
startCountdown();
init();
