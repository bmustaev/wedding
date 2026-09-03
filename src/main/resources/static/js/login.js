// login.js
import { login } from './api.js';
import { saveSession, isLoggedIn } from './auth.js';
import { showError, clearBanner } from './ui.js';
import { applyStaticTranslations, initLanguageSwitcher, t } from './admin-i18n.js';

applyStaticTranslations();
initLanguageSwitcher(document.getElementById('lang-switcher'));

if (isLoggedIn()) {
  location.href = 'dashboard.html';
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
    location.href = 'dashboard.html';
  } catch (err) {
    showError(errorBanner, err);
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = t('login-submit');
  }
});
