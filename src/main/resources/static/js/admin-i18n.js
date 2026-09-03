// admin-i18n.js — translations for the admin-facing pages (login,
// dashboard, hall, super-admin). Separate from i18n.js, which covers the
// guest-facing invitation.html (different audience, different language
// set — admins only get English/Russian, guests also get Uzbek).
//
// The admin's choice is a per-browser UI preference, not tied to any
// account field server-side, so it's persisted in localStorage (see
// getAdminLanguage/setAdminLanguage) rather than the database.
//
// Scope boundary: this translates the UI chrome only. Error messages
// that come back from the API (validation failures, "Guest not found",
// etc.) are generated server-side in English and are not translated —
// doing that properly would mean the backend negotiating language itself
// (e.g. via Accept-Language), which is a separate, larger change.

export const ADMIN_LANGUAGES = ['en', 'ru'];
const STORAGE_KEY = 'wedding_admin_ui_language';
const DEFAULT_LANGUAGE = 'ru';

export function getAdminLanguage() {
  const stored = localStorage.getItem(STORAGE_KEY);
  return ADMIN_LANGUAGES.includes(stored) ? stored : DEFAULT_LANGUAGE;
}

export function setAdminLanguage(lang) {
  if (!ADMIN_LANGUAGES.includes(lang)) return;
  localStorage.setItem(STORAGE_KEY, lang);
}

const DICT = {
  en: {
    'app-brand': 'Wedding Admin',
    'sidebar-role-admin': 'Admin',
    'sidebar-role-super': 'Super admin',
    'nav-guests': 'Guests',
    'nav-hall': 'Hall',
    'nav-import': 'Import',
    'nav-admins': 'Admins',
    'nav-back-to-dashboard': '← Back to my dashboard',
    'sign-out': 'Sign out',
    'language-label': 'Language',

    'login-doc-title': 'Sign in — Wedding Admin',
    'login-heading': 'Wedding Admin',
    'login-subtitle': 'Sign in to manage guests, seating, and invitations.',
    'login-username-label': 'Username',
    'login-password-label': 'Password',
    'login-submit': 'Sign in',
    'login-submitting': 'Signing in…',

    'dashboard-doc-title': 'Dashboard — Wedding Admin',
    'guests-heading': 'Guests',
    'guests-subtitle': 'Your invitation list — only guests you added are shown here.',
    'add-guest-btn': 'Add guest',
    'guests-loading': 'Loading guests…',
    'guests-empty-title': 'No guests yet',
    'guests-empty-hint': 'Add your first guest, or import a list from a .txt file.',
    'th-name': 'Name',
    'th-party': 'Party',
    'th-table': 'Table',
    'th-status': 'Status',
    'badge-group': 'Group',
    'table-n': 'Table {n}',
    'status-viewed': 'Viewed',
    'status-not-viewed': 'Not viewed yet',
    'copy-link-btn': 'Copy link',
    'copy-btn': 'Copy',
    'edit-btn': 'Edit',
    'copied': 'Copied',

    'import-heading': 'Import guests',
    'import-subtitle': 'Upload a .txt file — one guest per line. See format below.',
    'import-file-label': 'Guest list (.txt)',
    'import-file-hint': 'One guest per line: <code>Name</code>, or <code>Name;PartySize</code>, or <code>Name;PartySize;Member1,Member2</code>.',
    'import-upload-btn': 'Upload',
    'import-past-heading': 'Past imports',
    'import-empty': 'No imports yet',
    'import-success-banner': '{success} of {total} guest(s) added from {filename}.',
    'import-result-th-line': 'Line',
    'import-result-th-content': 'Content',
    'import-result-th-result': 'Result',
    'import-added': 'Added',
    'import-history-th-file': 'File',
    'import-history-th-result': 'Result',
    'import-history-th-status': 'Status',
    'import-history-result': '{success}/{total} added',
    'choose-file-first': 'Choose a .txt file first.',

    'guest-modal-title-add': 'Add guest',
    'guest-modal-title-edit': 'Edit guest',
    'guest-name-label': 'Display name',
    'guest-name-hint': 'Shown on the invitation, e.g. "Jane Doe" or "The Miller Family"',
    'guest-is-group-label': 'This is a group or family',
    'guest-party-size-label': 'Party size',
    'guest-language-label': 'Invitation language',
    'lang-option-en': 'English',
    'lang-option-ru': 'Russian',
    'lang-option-uz': 'Uzbek',
    'guest-members-label': 'Group member names',
    'add-member-btn': 'Add member',
    'remove-member-btn': 'Remove',
    'guest-greeting-label': 'Greeting message',
    'guest-greeting-placeholder': 'So excited to celebrate with you!',
    'guest-table-label': 'Table',
    'not-assigned': 'Not assigned',
    'table-option-current-suffix': '(includes this guest)',
    'guest-link-label': 'Invitation link',
    'guest-media-heading': 'Media',
    'add-photo-btn': 'Add photo',
    'add-video-btn': 'Add video',
    'delete-guest-btn': 'Delete guest',
    'cancel-btn': 'Cancel',
    'save-btn': 'Save',
    'display-name-required': 'Display name is required.',
    'confirm-delete-guest': 'Remove this guest from your list? This can’t be undone from here.',
    'member-name-placeholder': 'Member name',
    'media-allowance-template': '{photosUsed} of {photosTotal} photos · {videosUsed} of {videosTotal} videos',
    'no-media': 'No media uploaded yet.',
    'delete-aria': 'Delete',

    'import-modal-title': 'Import guest list',
    'close-btn': 'Close',
    'import-modal-success': '{success} of {total} guest(s) added.',

    'hall-doc-title': 'Hall — Wedding Admin',
    'hall-heading': 'Wedding hall',
    'hall-subtitle': 'Drag guests between your own side’s tables. The other side’s tables are visible but not yours to arrange.',
    'import-list-btn': 'Import list',
    'hall-loading': 'Loading the hall…',
    'bride-tables-heading': 'Bride’s tables',
    'groom-tables-heading': 'Groom’s tables',
    'seats-left-template': '{n} of {capacity} seats left',
    'remove-table-btn': 'Remove',
    'add-table-btn': '+ Add table',
    'drop-guest-here': 'Drop a guest here',
    'unassigned-heading-prefix': 'Unassigned guests',
    'everyone-seated': 'Everyone is seated — nice work.',
    'confirm-remove-table': 'Remove this table? It must be empty first.',
    'confirm-own-side-only': 'You can only seat your own guests at tables on your own side.',
    'confirm-head-table-reserved': 'The head table is reserved for the couple — guests can’t be seated there.',
    'head-table-reserved-label': 'Reserved for the couple',

    'super-admin-doc-title': 'Admins — Wedding Admin',
    'admins-heading': 'Admins',
    'admins-subtitle': 'Every admin’s own guest list stays private from every other admin — you can view, but not edit, from here.',
    'new-admin-btn': 'New admin',
    'admins-loading': 'Loading admins…',
    'admins-empty': 'No admins yet',
    'th-username': 'Username',
    'th-side': 'Side',
    'th-guests': 'Guests',
    'badge-super-admin': 'Super admin',
    'status-active': 'Active',
    'status-disabled': 'Disabled',
    'view-guests-btn': 'View guests',
    'disable-btn': 'Disable',
    'enable-btn': 'Enable',
    'admin-guests-title-template': 'Guests — {name}',
    'admin-guests-subtitle': 'Read-only view.',
    'back-to-admins-btn': '← Back to admins',
    'new-admin-modal-title': 'New admin',
    'admin-username-label': 'Username',
    'admin-side-label': 'Side',
    'side-bride': 'Bride',
    'side-groom': 'Groom',
    'admin-password-label': 'Password',
    'admin-password-hint': 'At least 8 characters. Share it with them directly — it won’t be shown again here.',
    'create-admin-btn': 'Create admin',

    'loading-generic': 'Loading…',
    'pager-label': 'Page {page} of {total}',
    'pager-prev': 'Previous',
    'pager-next': 'Next',
  },

  ru: {
    'app-brand': 'Свадебный админ',
    'sidebar-role-admin': 'Администратор',
    'sidebar-role-super': 'Суперадминистратор',
    'nav-guests': 'Гости',
    'nav-hall': 'Зал',
    'nav-import': 'Импорт',
    'nav-admins': 'Администраторы',
    'nav-back-to-dashboard': '← Назад в свою панель',
    'sign-out': 'Выйти',
    'language-label': 'Язык',

    'login-doc-title': 'Вход — Свадебный админ',
    'login-heading': 'Свадебный админ',
    'login-subtitle': 'Войдите, чтобы управлять гостями, рассадкой и приглашениями.',
    'login-username-label': 'Имя пользователя',
    'login-password-label': 'Пароль',
    'login-submit': 'Войти',
    'login-submitting': 'Выполняется вход…',

    'dashboard-doc-title': 'Панель — Свадебный админ',
    'guests-heading': 'Гости',
    'guests-subtitle': 'Ваш список приглашённых — здесь показаны только добавленные вами гости.',
    'add-guest-btn': 'Добавить гостя',
    'guests-loading': 'Загрузка гостей…',
    'guests-empty-title': 'Гостей пока нет',
    'guests-empty-hint': 'Добавьте первого гостя или импортируйте список из .txt файла.',
    'th-name': 'Имя',
    'th-party': 'Кол-во',
    'th-table': 'Стол',
    'th-status': 'Статус',
    'badge-group': 'Группа',
    'table-n': 'Стол {n}',
    'status-viewed': 'Просмотрено',
    'status-not-viewed': 'Ещё не открыто',
    'copy-link-btn': 'Скопировать ссылку',
    'copy-btn': 'Копировать',
    'edit-btn': 'Изменить',
    'copied': 'Скопировано',

    'import-heading': 'Импорт гостей',
    'import-subtitle': 'Загрузите .txt файл — один гость на строку. Формат см. ниже.',
    'import-file-label': 'Список гостей (.txt)',
    'import-file-hint': 'Один гость на строку: <code>Имя</code>, или <code>Имя;Мест</code>, или <code>Имя;Мест;Участник1,Участник2</code>.',
    'import-upload-btn': 'Загрузить',
    'import-past-heading': 'История импорта',
    'import-empty': 'Импортов пока не было',
    'import-success-banner': 'Добавлено {success} из {total} гостей из файла {filename}.',
    'import-result-th-line': 'Строка',
    'import-result-th-content': 'Содержимое',
    'import-result-th-result': 'Результат',
    'import-added': 'Добавлено',
    'import-history-th-file': 'Файл',
    'import-history-th-result': 'Результат',
    'import-history-th-status': 'Статус',
    'import-history-result': '{success}/{total} добавлено',
    'choose-file-first': 'Сначала выберите .txt файл.',

    'guest-modal-title-add': 'Добавить гостя',
    'guest-modal-title-edit': 'Изменить гостя',
    'guest-name-label': 'Отображаемое имя',
    'guest-name-hint': 'Показывается в приглашении, например «Иван Иванов» или «Семья Ивановых»',
    'guest-is-group-label': 'Это группа или семья',
    'guest-party-size-label': 'Количество человек',
    'guest-language-label': 'Язык приглашения',
    'lang-option-en': 'Английский',
    'lang-option-ru': 'Русский',
    'lang-option-uz': 'Узбекский',
    'guest-members-label': 'Имена участников группы',
    'add-member-btn': 'Добавить участника',
    'remove-member-btn': 'Удалить',
    'guest-greeting-label': 'Приветственное сообщение',
    'guest-greeting-placeholder': 'Очень рады отпраздновать этот день с вами!',
    'guest-table-label': 'Стол',
    'not-assigned': 'Не назначен',
    'table-option-current-suffix': '(включая этого гостя)',
    'guest-link-label': 'Ссылка на приглашение',
    'guest-media-heading': 'Медиа',
    'add-photo-btn': 'Добавить фото',
    'add-video-btn': 'Добавить видео',
    'delete-guest-btn': 'Удалить гостя',
    'cancel-btn': 'Отмена',
    'save-btn': 'Сохранить',
    'display-name-required': 'Укажите отображаемое имя.',
    'confirm-delete-guest': 'Удалить этого гостя из списка? Это действие нельзя отменить отсюда.',
    'member-name-placeholder': 'Имя участника',
    'media-allowance-template': 'Фото: {photosUsed} из {photosTotal} · Видео: {videosUsed} из {videosTotal}',
    'no-media': 'Медиафайлы ещё не загружены.',
    'delete-aria': 'Удалить',

    'import-modal-title': 'Импорт списка гостей',
    'close-btn': 'Закрыть',
    'import-modal-success': 'Добавлено {success} из {total} гостей.',

    'hall-doc-title': 'Зал — Свадебный админ',
    'hall-heading': 'Свадебный зал',
    'hall-subtitle': 'Перетаскивайте гостей между столами своей стороны. Столы другой стороны видны, но недоступны для изменения.',
    'import-list-btn': 'Импорт списка',
    'hall-loading': 'Загрузка зала…',
    'bride-tables-heading': 'Столы невесты',
    'groom-tables-heading': 'Столы жениха',
    'seats-left-template': 'Свободно {n} из {capacity} мест',
    'remove-table-btn': 'Удалить',
    'add-table-btn': '+ Добавить стол',
    'drop-guest-here': 'Перетащите гостя сюда',
    'unassigned-heading-prefix': 'Неразмещённые гости',
    'everyone-seated': 'Все гости рассажены — отлично!',
    'confirm-remove-table': 'Удалить этот стол? Сначала он должен быть пустым.',
    'confirm-own-side-only': 'Вы можете рассаживать своих гостей только за столы своей стороны.',
    'confirm-head-table-reserved': 'Стол молодожёнов зарезервирован для жениха и невесты — гостей сюда сажать нельзя.',
    'head-table-reserved-label': 'Зарезервирован для молодожёнов',

    'super-admin-doc-title': 'Администраторы — Свадебный админ',
    'admins-heading': 'Администраторы',
    'admins-subtitle': 'Список гостей каждого администратора скрыт от остальных — здесь его можно только просмотреть, но не редактировать.',
    'new-admin-btn': 'Новый администратор',
    'admins-loading': 'Загрузка администраторов…',
    'admins-empty': 'Администраторов пока нет',
    'th-username': 'Имя пользователя',
    'th-side': 'Сторона',
    'th-guests': 'Гости',
    'badge-super-admin': 'Супер-админ',
    'status-active': 'Активен',
    'status-disabled': 'Отключён',
    'view-guests-btn': 'Смотреть гостей',
    'disable-btn': 'Отключить',
    'enable-btn': 'Включить',
    'admin-guests-title-template': 'Гости — {name}',
    'admin-guests-subtitle': 'Только просмотр.',
    'back-to-admins-btn': '← Назад к администраторам',
    'new-admin-modal-title': 'Новый администратор',
    'admin-username-label': 'Имя пользователя',
    'admin-side-label': 'Сторона',
    'side-bride': 'Невеста',
    'side-groom': 'Жених',
    'admin-password-label': 'Пароль',
    'admin-password-hint': 'Минимум 8 символов. Сообщите его администратору лично — здесь он больше не будет показан.',
    'create-admin-btn': 'Создать администратора',

    'loading-generic': 'Загрузка…',
    'pager-label': 'Страница {page} из {total}',
    'pager-prev': 'Назад',
    'pager-next': 'Далее',
  },
};

export function t(key, vars) {
  const lang = getAdminLanguage();
  let template = DICT[lang]?.[key] ?? DICT[DEFAULT_LANGUAGE][key] ?? key;
  if (vars) {
    for (const [k, v] of Object.entries(vars)) {
      template = template.replaceAll(`{${k}}`, v);
    }
  }
  return template;
}

/** Sets every [data-i18n] element's innerHTML, [data-i18n-placeholder]'s placeholder, and document.title (from <body data-i18n-title>). */
export function applyStaticTranslations() {
  document.documentElement.lang = getAdminLanguage();
  document.querySelectorAll('[data-i18n]').forEach((el) => {
    el.innerHTML = t(el.dataset.i18n);
  });
  document.querySelectorAll('[data-i18n-placeholder]').forEach((el) => {
    el.placeholder = t(el.dataset.i18nPlaceholder);
  });
  const titleKey = document.body.dataset.i18nTitle;
  if (titleKey) document.title = t(titleKey);
}

/**
 * Wires a .lang-toggle container (a <button data-lang="en">/<button
 * data-lang="ru"> pair) as the language switcher: highlights the current
 * choice and reloads the page when a different one is picked, so every
 * dynamically-rendered string (table rows, etc.) picks up the new
 * language too, rather than trying to re-render everything live.
 */
export function initLanguageSwitcher(containerEl) {
  const current = getAdminLanguage();
  containerEl.setAttribute('role', 'group');
  containerEl.setAttribute('aria-label', t('language-label'));
  containerEl.querySelectorAll('[data-lang]').forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.lang === current);
    btn.addEventListener('click', () => {
      if (btn.dataset.lang === current) return;
      setAdminLanguage(btn.dataset.lang);
      location.reload();
    });
  });
}
