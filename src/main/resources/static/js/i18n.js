// i18n.js — translations for the guest-facing invitation page. The admin
// dashboard stays English-only (see README.md); this is the one page a
// non-admin actually reads, so it supports the three languages guests at
// this wedding actually speak: Russian, Uzbek (Latin), and English.
//
// A guest's language comes from Guest#language (set by the admin in the
// editor, GuestResponse/PublicInvitationResponse's `language` field) —
// see invitation.js's applyLanguage(). Static markup ships with Russian
// text as a no-JS fallback; every translatable element carries a
// data-i18n key that applyStaticTranslations() overwrites at runtime.
//
// Guests are invited to one of two halls (PublicInvitationResponse's
// `hall`): TASHKENT, the main celebration — what STRINGS below describes
// — or SAMARKAND, whose venue/date/time copy comes from HALL_STRINGS.

export const LANGUAGES = ['ru', 'uz', 'en'];
export const DEFAULT_LANGUAGE = 'ru';

export function normalizeLanguage(lang) {
  const code = (lang || '').trim().toLowerCase().slice(0, 2);
  return LANGUAGES.includes(code) ? code : DEFAULT_LANGUAGE;
}

// Static strings, keyed to match each element's data-i18n attribute in
// invitation.html. Values may contain simple trusted HTML (<br>, <b>,
// <span>) since they're set via innerHTML — never guest- or admin-entered
// content, which stays on the .textContent path elsewhere in invitation.js.
const STRINGS = {
  ru: {
    'loading-text': 'Открываем ваше приглашение…',
    'error-title': 'Ссылка недействительна',
    'error-body': 'Проверьте ссылку, которую вам прислали, или свяжитесь с молодожёнами.',

    'hero-eyebrow': 'Свадьба',
    'hero-names': 'Бобура<br><span class="amp">и</span><br>Дильнозы',
    'hero-when': '2 октября 2026, пятница<br><b>18:00</b>',
    'hero-place': 'Ташкент, ресторан Santini',

    'seat-eyebrow': 'Личное приглашение для',
    'seat-table-label': 'ваш стол',
    'seat-note': 'Места пронумерованы, схема будет у входа в зал.',

    'cd-today': 'Сегодня! Ждём вас',

    'invite-p1': '2 октября мы женимся — это особенный день для нас, и мы хотим, чтобы вы были рядом. Без вас этот вечер будет совсем не тот.',
    'invite-p2': 'Пусть этот вечер запомнится теплом, смехом и тем, что за одним столом собрались самые родные и близкие.',
    'invite-no-children': 'Небольшая просьба: этот вечер мы хотим провести в кругу взрослых, поэтому, пожалуйста, приходите без детей. Спасибо за понимание!',
    'invite-sign': 'Обнимаем, Бобур и Дильноза',

    'about-title': 'Немного о нас',
    'about-lede': 'Пока фотограф не снял свадьбу — вот несколько кадров из обычной жизни.',

    'where-title': 'Где',
    'venue-name': 'Ресторан Santini',
    'fact-address-label': 'Адрес',
    'fact-address-value': 'Ташкент, Юнусабадский район,<br>ул. Чинабад, 61/1',
    'fact-date-label': 'Дата',
    'fact-date-value': '2 октября 2026, пятница',
    'fact-time-label': 'Время',
    'fact-time-value': '18:00',
    'fact-parking-label': 'Парковка',
    'fact-parking-value': 'на территории ресторана',
    'map-yandex-label': 'Яндекс.Карты',
    'map-google-label': 'Google Карты',

    'media-title': 'Фото и видео',
    'media-lede': 'Мы хотим увидеть свою свадьбу вашими глазами.',
    'photo-title': 'Фото',
    'video-title': 'Видео',
    'upload-photo-btn': 'Загрузить фото',
    'upload-video-btn': 'Загрузить видео',
    'media-feed-link': 'Смотреть фото гостей →',

    'footer-names': 'Бобур и Дильноза',
    'footer-meta': '2 октября 2026, Ташкент',
  },

  uz: {
    'loading-text': 'Taklifnomangiz ochilmoqda…',
    'error-title': 'Havola yaroqsiz',
    'error-body': "Sizga yuborilgan havolani tekshiring yoki kelin-kuyov bilan bog'laning.",

    'hero-eyebrow': "To'y",
    'hero-names': 'Bobur<br><span class="amp">va</span><br>Dilnoza',
    'hero-when': '2-oktabr 2026, juma<br><b>18:00</b>',
    'hero-place': 'Toshkent, Santini restorani',

    'seat-eyebrow': 'Shaxsiy taklifnoma',
    'seat-table-label': 'stolingiz',
    'seat-note': "O'rindiqlar raqamlangan, sxema zal kirishida bo'ladi.",

    'cd-today': 'Bugun! Sizni kutmoqdamiz',

    'invite-p1': "2-oktabr nihoyat turmush qurmoqchimiz va juda xohlaymizki, siz ham yonimizda bo'lsangiz. Sizsiz bu oqshom butunlay boshqacha bo'lardi.",
    'invite-p2': "Ushbu oqshom iliqlik, kulgi va bir dasturxon atrofida eng aziz va yaqinlarimiz yig'ilgani bilan yodda qolsin.",
    'invite-no-children': "Kichik bir iltimos: bu oqshomni faqat kattalar davrasida o'tkazishni xohlaymiz, shuning uchun farzandlaringizni uyda qoldirib kelishingizni so'raymiz. Tushunganingiz uchun rahmat!",
    'invite-sign': "Quchoqlab, Bobur va Dilnoza",

    'about-title': 'Biz haqimizda',
    'about-lede': "Fotosuratchi to'yni suratga olguncha — mana kundalik hayotimizdan bir nechta lavha.",

    'where-title': 'Qayerda',
    'venue-name': 'Santini restorani',
    'fact-address-label': 'Manzil',
    'fact-address-value': "Toshkent, Yunusobod tumani,<br>Chinobod ko'chasi, 61/1",
    'fact-date-label': 'Sana',
    'fact-date-value': '2-oktabr 2026, juma',
    'fact-time-label': 'Vaqt',
    'fact-time-value': '18:00',
    'fact-parking-label': 'Avtoturargoh',
    'fact-parking-value': "bepul, restoran hududida",
    'map-yandex-label': 'Yandex xarita',
    'map-google-label': 'Google xarita',

    'media-title': 'Foto va video',
    'media-lede': "To'yimizni sizning ko'zlaringiz bilan ham ko'rishni xohlaymiz.",
    'photo-title': 'Foto',
    'video-title': 'Video',
    'upload-photo-btn': 'Foto yuklash',
    'upload-video-btn': 'Video yuklash',
    'media-feed-link': "Mehmonlar fotolarini ko'rish →",

    'footer-names': 'Bobur va Dilnoza',
    'footer-meta': '2-oktabr 2026, Toshkent',
  },

  en: {
    'loading-text': 'Opening your invitation…',
    'error-title': "This link isn't valid",
    'error-body': "Double-check the link you were sent, or reach out to the couple.",

    'hero-eyebrow': 'Wedding',
    'hero-names': 'Bobur<br><span class="amp">and</span><br>Dilnoza',
    'hero-when': 'October 2, 2026, Friday<br><b>18:00</b>',
    'hero-place': 'Tashkent, Santini restaurant',

    'seat-eyebrow': 'A personal invitation for',
    'seat-table-label': 'your table',
    'seat-note': 'Seats are numbered — the seating chart will be at the hall entrance.',

    'cd-today': "Today! We can't wait to see you",

    'invite-p1': "On October 2nd we're finally getting married, and we'd love for you to be there with us. This evening just wouldn't be the same without you.",
    'invite-p2': 'Let this evening be remembered for its warmth, laughter, and having our nearest and dearest gathered around one table.',
    'invite-no-children': "One small request: we'd love for this evening to be an adults-only celebration, so please leave the little ones at home. Thank you for understanding!",
    'invite-sign': 'With love, Bobur and Dilnoza',

    'about-title': 'A little about us',
    'about-lede': 'Until the photographer hands over the wedding shots, here are a few snapshots from everyday life.',

    'where-title': 'Where',
    'venue-name': 'Santini Restaurant',
    'fact-address-label': 'Address',
    'fact-address-value': 'Tashkent, Yunusabad district,<br>Chinabad street, 61/1',
    'fact-date-label': 'Date',
    'fact-date-value': 'October 2, 2026, Friday',
    'fact-time-label': 'Time',
    'fact-time-value': '18:00',
    'fact-parking-label': 'Parking',
    'fact-parking-value': 'free, on the restaurant grounds',
    'map-yandex-label': 'Yandex Maps',
    'map-google-label': 'Google Maps',

    'media-title': 'Photos and videos',
    'media-lede': "We'd love to see our wedding through your eyes.",
    'photo-title': 'Photos',
    'video-title': 'Videos',
    'upload-photo-btn': 'Upload photo',
    'upload-video-btn': 'Upload video',
    'media-feed-link': "See the guests' photos →",

    'footer-names': 'Bobur and Dilnoza',
    'footer-meta': 'October 2, 2026, Tashkent',
  },
};

// Per-hall overrides of STRINGS — only the keys that differ from the
// Tashkent invitation (venue, date, time, and copy that mentions them).
// Same design and structure otherwise; a key missing here falls back to
// STRINGS.
const HALL_STRINGS = {
  SAMARKAND: {
    ru: {
      'hero-when': '10 октября 2026, суббота<br><b>14:00</b>',
      'hero-place': 'Самарканд, ресторан Богишамол',
      'invite-p1': '10 октября мы празднуем нашу свадьбу в Самарканде и очень хотим, чтобы вы были рядом. Без вас этот день будет совсем не тот.',
      'invite-p2': 'Пусть этот день запомнится теплом, смехом и тем, что за одним столом собрались самые родные и близкие.',
      'invite-no-children': 'Небольшая просьба: этот праздник мы хотим провести в кругу взрослых, поэтому, пожалуйста, приходите без детей. Спасибо за понимание!',
      'venue-name': 'Ресторан Богишамол',
      'fact-address-value': 'Самарканд, ресторан Богишамол',
      'fact-date-value': '10 октября 2026, суббота',
      'fact-time-value': '14:00',
      'footer-meta': '10 октября 2026, Самарканд',
    },
    uz: {
      'hero-when': '10-oktabr 2026, shanba<br><b>14:00</b>',
      'hero-place': "Samarqand, Bog'ishamol restorani",
      'invite-p1': "10-oktabr kuni Samarqandda to'yimizni nishonlaymiz va juda xohlaymizki, siz ham yonimizda bo'lsangiz. Sizsiz bu kun butunlay boshqacha bo'lardi.",
      'invite-p2': "Ushbu kun iliqlik, kulgi va bir dasturxon atrofida eng aziz va yaqinlarimiz yig'ilgani bilan yodda qolsin.",
      'invite-no-children': "Kichik bir iltimos: bu bayramni faqat kattalar davrasida o'tkazishni xohlaymiz, shuning uchun farzandlaringizni uyda qoldirib kelishingizni so'raymiz. Tushunganingiz uchun rahmat!",
      'venue-name': "Bog'ishamol restorani",
      'fact-address-value': "Samarqand, Bog'ishamol restorani",
      'fact-date-value': '10-oktabr 2026, shanba',
      'fact-time-value': '14:00',
      'footer-meta': '10-oktabr 2026, Samarqand',
    },
    en: {
      'hero-when': 'October 10, 2026, Saturday<br><b>14:00</b>',
      'hero-place': 'Samarkand, Bogishamol restaurant',
      'invite-p1': "On October 10th we're celebrating our wedding in Samarkand, and we'd love for you to be there with us. The day just wouldn't be the same without you.",
      'invite-p2': 'Let this day be remembered for its warmth, laughter, and having our nearest and dearest gathered around one table.',
      'invite-no-children': "One small request: we'd love for this celebration to be adults-only, so please leave the little ones at home. Thank you for understanding!",
      'venue-name': 'Bogishamol Restaurant',
      'fact-address-value': 'Samarkand, Bogishamol Restaurant',
      'fact-date-value': 'October 10, 2026, Saturday',
      'fact-time-value': '14:00',
      'footer-meta': 'October 10, 2026, Samarkand',
    },
  },
};

function lookup(code, key, hall) {
  return HALL_STRINGS[hall]?.[code]?.[key] ?? STRINGS[code][key];
}

export function t(lang, key, hall) {
  const code = normalizeLanguage(lang);
  return lookup(code, key, hall) ?? STRINGS[DEFAULT_LANGUAGE][key] ?? key;
}

/** Sets every [data-i18n] element's innerHTML from the dictionary for `lang` (and the guest's `hall`, if known). */
export function applyStaticTranslations(lang, hall) {
  const code = normalizeLanguage(lang);
  document.documentElement.lang = code;
  document.querySelectorAll('[data-i18n]').forEach((el) => {
    const value = lookup(code, el.dataset.i18n, hall);
    if (value != null) el.innerHTML = value;
  });
}

// -----------------------------------------------------------------------
// Dynamic strings — depend on a count, a name, or another runtime value,
// so they can't just live as static innerHTML like the table above.
// -----------------------------------------------------------------------

function pluralRu(n, forms) {
  const a = n % 10;
  const b = n % 100;
  if (a === 1 && b !== 11) return forms[0];
  if (a >= 2 && a <= 4 && (b < 10 || b >= 20)) return forms[1];
  return forms[2];
}

const COUNTDOWN_WORDS = {
  ru: {
    day: (n) => pluralRu(n, ['день', 'дня', 'дней']),
    hour: (n) => pluralRu(n, ['час', 'часа', 'часов']),
    minute: (n) => pluralRu(n, ['минута', 'минуты', 'минут']),
    second: (n) => pluralRu(n, ['секунда', 'секунды', 'секунд']),
  },
  uz: {
    // Uzbek nouns don't inflect for number after a numeral.
    day: () => 'kun',
    hour: () => 'soat',
    minute: () => 'daqiqa',
    second: () => 'soniya',
  },
  en: {
    day: (n) => (n === 1 ? 'day' : 'days'),
    hour: (n) => (n === 1 ? 'hour' : 'hours'),
    minute: (n) => (n === 1 ? 'minute' : 'minutes'),
    second: (n) => (n === 1 ? 'second' : 'seconds'),
  },
};

export function countdownWord(lang, unit, n) {
  const code = normalizeLanguage(lang);
  return COUNTDOWN_WORDS[code][unit](n);
}

const MAP_QUERY = {
  TASHKENT: {
    ru: 'Ресторан Santini, Ташкент, улица Чинабад, 61/1',
    uz: "Santini restorani, Toshkent, Chinobod ko'chasi, 61/1",
    en: 'Santini Restaurant, Tashkent, Chinabad street, 61/1',
  },
};

// Samarkand links to exact pins ("lat,lng" — both map searches accept it)
// rather than a name search, one per provider: Yandex's own pin for the
// restaurant, and Google's plus code MX63+PG (full: 8JF8MX63+PG) decoded.
const MAP_PIN = {
  SAMARKAND: {
    yandex: '39.661840,66.953679',
    google: '39.661812,66.953812',
  },
};

/** Search text for `provider` ('yandex' | 'google') — an exact pin where the hall has one, else the address in `lang`. */
export function mapQueryFor(lang, hall, provider) {
  return MAP_PIN[hall]?.[provider] ?? (MAP_QUERY[hall] ?? MAP_QUERY.TASHKENT)[normalizeLanguage(lang)];
}

const DEFAULT_GREETING = {
  ru: 'Дорогие гости!',
  uz: 'Aziz mehmonlar!',
  en: 'Dear guests!',
};

export function defaultGreetingFor(lang) {
  return DEFAULT_GREETING[normalizeLanguage(lang)];
}

const SEAT_MEMBERS_TEMPLATE = {
  ru: (names) => `Вместе с вами: ${names}`,
  uz: (names) => `Siz bilan birga: ${names}`,
  en: (names) => `Joining you: ${names}`,
};

export function seatMembersText(lang, names) {
  return SEAT_MEMBERS_TEMPLATE[normalizeLanguage(lang)](names);
}

const REMAINING_TEMPLATE = {
  ru: {
    photo: (n) => (n > 0 ? '' : 'Лимит фото исчерпан'),
    video: (n) => (n > 0 ? '' : 'Лимит видео исчерпан'),
  },
  uz: {
    photo: (n) => (n > 0 ? '' : 'Foto limiti tugadi'),
    video: (n) => (n > 0 ? '' : 'Video limiti tugadi'),
  },
  en: {
    photo: (n) => (n > 0 ? '' : 'Photo limit reached'),
    video: (n) => (n > 0 ? '' : 'Video limit reached'),
  },
};

export function remainingMediaText(lang, kind, n) {
  return REMAINING_TEMPLATE[normalizeLanguage(lang)][kind](n);
}

const DOCUMENT_TITLE_TEMPLATE = {
  ru: (name) => `${name} — приглашение на свадьбу Бобура и Дильнозы`,
  uz: (name) => `${name} — Bobur va Dilnoza to'yiga taklifnoma`,
  en: (name) => `${name} — invitation to Bobur and Dilnoza's wedding`,
};

export function documentTitleFor(lang, guestName) {
  return DOCUMENT_TITLE_TEMPLATE[normalizeLanguage(lang)](guestName);
}
