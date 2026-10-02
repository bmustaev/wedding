// login.js
import { login } from './api.js';
import { saveSession, isLoggedIn, homePage } from './auth.js';
import { showError, clearBanner } from './ui.js';
import { applyStaticTranslations, initLanguageSwitcher, t } from './admin-i18n.js';

applyStaticTranslations();
initLanguageSwitcher(document.getElementById('lang-switcher'));

// Where to go after signing in: ?next= (only a page of this site, e.g. bank.html after a banker scanned a QR code), else the role's home page.
const next = new URLSearchParams(location.search).get('next');
const destination = () => (next && /^[a-z-]+\.html$/.test(next) ? next : homePage());

if (isLoggedIn()) {
  location.href = destination();
}

const form = document.getElementById('login-form');
const errorBanner = document.getElementById('error-banner');
const submitBtn = document.getElementById('login-submit');

form.addEventListener('submit', async (e) => {
  e.preventDefault();
  clearBanner(errorBanner);

  const username = document.getElementById('username').value.trim();
  const password = document.getElementById('password').value;

  submitBtn.disabled = true;
  submitBtn.textContent = t('login-submitting');

  try {
    const result = await login(username, password);
    saveSession(result);
    location.href = destination();
  } catch (err) {
    showError(errorBanner, err);
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = t('login-submit');
  }
});
