// invitation.js — the guest-facing page. No auth: the slug in the URL is
// the credential (see API.md, section 7). Reads it from the query string
// or the /i/{slug} path — see this project's README.md for the details,
// and what to set app.invitation.base-url to on the backend to match.
import * as api from './api.js';
import { showError, clearBanner, escapeHtml } from './ui.js';

// The slug arrives either as ?slug=... (direct invitation.html link) or as
// the path segment of the pretty /i/{slug} URL, which the backend forwards
// here without rewriting (see InvitationRedirectController).
const pathMatch = location.pathname.match(/^\/i\/([^/]+)\/?$/);
const slug = new URLSearchParams(location.search).get('slug')
  || (pathMatch ? decodeURIComponent(pathMatch[1]) : null);

const loadingEl = document.getElementById('invite-loading');
const errorStateEl = document.getElementById('invite-error-state');
const contentEl = document.getElementById('invite-content');
const uploadError = document.getElementById('upload-error');

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
  document.title = `${invitation.displayName} — You're invited`;
  document.getElementById('invite-name').textContent = invitation.displayName;
  document.getElementById('invite-greeting').textContent =
    invitation.greetingMessage || 'We can\u2019t wait to celebrate with you.';

  const membersEl = document.getElementById('invite-members');
  if (invitation.isGroup && invitation.groupMembers && invitation.groupMembers.length > 0) {
    membersEl.innerHTML = invitation.groupMembers.map((m) => `<li>${escapeHtml(m)}</li>`).join('');
    membersEl.hidden = false;
  }

  const tableEl = document.getElementById('invite-table');
  if (invitation.tableNumber != null) {
    tableEl.innerHTML = `You're seated at <strong>Table ${invitation.tableNumber}</strong>`;
    tableEl.hidden = false;
  }

  updateRemaining('photos-remaining', invitation.photosRemaining, 'photo');
  updateRemaining('videos-remaining', invitation.videosRemaining, 'video');
  togglePhotoInput(invitation.photosRemaining);
  toggleVideoInput(invitation.videosRemaining);
}

function updateRemaining(elementId, remaining, label) {
  const el = document.getElementById(elementId);
  el.textContent = remaining > 0 ? `${remaining} ${label}${remaining === 1 ? '' : 's'} left` : 'None left';
  el.classList.toggle('at-cap', remaining <= 0);
}

function togglePhotoInput(remaining) {
  document.getElementById('photo-input').disabled = remaining <= 0;
}
function toggleVideoInput(remaining) {
  document.getElementById('video-input').disabled = remaining <= 0;
}

// -----------------------------------------------------------------------
// Gallery
// -----------------------------------------------------------------------

async function loadGallery() {
  try {
    const items = await api.listPublicMedia(slug);
    renderGallery(items);
  } catch (err) {
    showError(uploadError, err);
  }
}

function renderGallery(items) {
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
        updateRemaining('photos-remaining', invitation.photosRemaining, 'photo');
        updateRemaining('videos-remaining', invitation.videosRemaining, 'video');
        togglePhotoInput(invitation.photosRemaining);
        toggleVideoInput(invitation.videosRemaining);
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
    updateRemaining('photos-remaining', invitation.photosRemaining, 'photo');
    togglePhotoInput(invitation.photosRemaining);
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
    updateRemaining('videos-remaining', invitation.videosRemaining, 'video');
    toggleVideoInput(invitation.videosRemaining);
    loadGallery();
  } catch (err) {
    showError(uploadError, err);
  }
  e.target.value = '';
});

init();
