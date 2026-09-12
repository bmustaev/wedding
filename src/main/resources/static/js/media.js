// media.js — placeholder page linked from invitation.js's photo/video
// buttons. No auth, same slug-as-credential model as invitation.js (see
// API.md section 7): the slug just resolves the guest's language here and
// builds the "back to invitation" link, since real upload management on
// this page doesn't exist yet.
import * as api from './api.js';
import { normalizeLanguage, applyStaticTranslations } from './i18n.js';

const slug = new URLSearchParams(location.search).get('slug');

const loadingEl = document.getElementById('loading');
const errorStateEl = document.getElementById('error-state');
const contentEl = document.getElementById('content');

// Best guess until the guest's own `language` comes back from the API.
applyStaticTranslations(normalizeLanguage(navigator.language));

async function init() {
  if (!slug) {
    showInvalidLink();
    return;
  }
  try {
    const invitation = await api.getPublicInvitation(slug);
    applyStaticTranslations(normalizeLanguage(invitation.language));
    document.getElementById('back-link').href = `/i/${encodeURIComponent(slug)}`;
    loadingEl.hidden = true;
    contentEl.hidden = false;
  } catch {
    showInvalidLink();
  }
}

function showInvalidLink() {
  loadingEl.hidden = true;
  errorStateEl.hidden = false;
}

init();
