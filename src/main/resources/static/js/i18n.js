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
    'hero-names': 'Бобиржон<span class="amp">и</span>Дилноза',
    'hero-when': '2 октября 2026, пятница<br><b>18:00</b>',
    'hero-place': 'Ташкент, ресторан Santini',

    'seat-eyebrow': 'Личное приглашение для',
    'seat-table-label': 'ваш стол',

    'cd-today': 'Сегодня! Ждём вас',

    'invite-p1': '2 октября мы наконец-то женимся и очень хотим, чтобы вы были рядом. Без вас этот вечер будет совсем не тот.',
    'invite-p2': 'Пусть этот вечер запомнится не тостами и церемониями, а тем, как хорошо нам было всем вместе.',
    'invite-sign': 'Обнимаем, Бобиржон и Дилноза',

    'about-title': 'Немного о нас',
    'about-lede': 'Пока фотограф не снял свадьбу — вот несколько кадров из обычной жизни.',

    'day-title': 'Как пройдёт день',
    'day-note': 'Время примерное, точное скажем ближе к дате.',
    'sched1-title': 'Собираемся',
    'sched1-desc': 'Встречаем гостей, наливаем чай, фотографируемся. Места пронумерованы, схема будет у входа в зал.',
    'sched2-title': 'Наш выход',
    'sched2-desc': 'Самый волнительный момент, просим быть в зале.',
    'sched3-title': 'Танцы',
    'sched3-desc': 'Диджей, живая музыка и национальные танцы. Обувь выбирайте с умом.',
    'sched4-title': 'Расходимся',
    'sched4-desc': 'Обнимаемся на прощание.',

    'where-title': 'Где',
    'venue-name': 'Ресторан Santini',
    'venue-lede': 'Большой зал с летней террасой и танцполом в Юнусабадском районе. Парковка есть, места хватит всем.',
    'fact-address-label': 'Адрес',
    'fact-address-value': 'Ташкент, Юнусабадский район,<br>ул. Чинабад, 61/1',
    'fact-date-label': 'Дата',
    'fact-date-value': '2 октября 2026, пятница',
    'fact-time-label': 'Время',
    'fact-time-value': '18:00',
    'fact-parking-label': 'Парковка',
    'fact-parking-value': 'бесплатная, на территории ресторана',
    'map-yandex-label': 'Яндекс.Карты',
    'map-google-label': 'Google Карты',

    'media-title': 'Фото и видео',
    'media-lede': 'Мы хотим увидеть свою свадьбу вашими глазами.',
    'photo-title': 'Фото',
    'video-title': 'Видео',
    'upload-photo-btn': 'Загрузить фото',
    'upload-video-btn': 'Загрузить видео',

    'contacts-title': 'Контакты',
    'contacts-lede': 'Если что-то непонятно — просто позвоните.',
    'role-groom': 'Жених',
    'name-groom': 'Бобиржон',
    'role-bride': 'Невеста',
    'name-bride': 'Дилноза',
    'role-coordinator': 'Координатор',
    'name-coordinator': 'Имя координатора',
    'role-restaurant': 'Ресторан',

    'footer-names': 'Бобиржон и Дилноза',
    'footer-meta': '2 октября 2026, Ташкент',
  },

  uz: {
    'loading-text': 'Taklifnomangiz ochilmoqda…',
    'error-title': 'Havola yaroqsiz',
    'error-body': "Sizga yuborilgan havolani tekshiring yoki kelin-kuyov bilan bog'laning.",

    'hero-eyebrow': "To'y",
    'hero-names': 'Bobirjon<span class="amp">va</span>Dilnoza',
    'hero-when': '2-oktabr 2026, juma<br><b>18:00</b>',
    'hero-place': 'Toshkent, Santini restorani',

    'seat-eyebrow': 'Shaxsiy taklifnoma',
    'seat-table-label': 'stolingiz',

    'cd-today': 'Bugun! Sizni kutmoqdamiz',

    'invite-p1': "2-oktabr nihoyat turmush qurmoqchimiz va juda xohlaymizki, siz ham yonimizda bo'lsangiz. Sizsiz bu oqshom butunlay boshqacha bo'lardi.",
    'invite-p2': "Ushbu oqshom baland nutqlar bilan emas, balki barchamiz birga o'tkazgan yaxshi damlar bilan yodda qolsin.",
    'invite-sign': "Quchoqlab, Bobirjon va Dilnoza",

    'about-title': 'Biz haqimizda',
    'about-lede': "Fotosuratchi to'yni suratga olguncha — mana kundalik hayotimizdan bir nechta lavha.",

    'day-title': "Kun qanday o'tadi",
    'day-note': "Vaqt taxminiy, aniq vaqtni sanaga yaqinroq aytamiz.",
    'sched1-title': "To'planamiz",
    'sched1-desc': "Mehmonlarni kutib olamiz, choy quyamiz, suratga tushamiz. O'rindiqlar raqamlangan, sxema zal kirishida bo'ladi.",
    'sched2-title': 'Bizning chiqishimiz',
    'sched2-desc': 'Eng hayajonli lahza — iltimos, shu vaqtga zalda bo\'ling.',
    'sched3-title': 'Raqslar',
    'sched3-desc': "DJ, jonli musiqa va milliy raqslar. Poyabzalni oqilona tanlang.",
    'sched4-title': 'Tarqalamiz',
    'sched4-desc': "Xayrlashuv quchoqlari bilan yakunlaymiz.",

    'where-title': 'Qayerda',
    'venue-name': 'Santini restorani',
    'venue-lede': "Yunusobod tumanida joylashgan, yozgi terrasa va raqs maydonchasi bo'lgan katta zal. Avtoturargoh bor, hammaga joy yetadi.",
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

    'contacts-title': 'Aloqa',
    'contacts-lede': "Biror narsa noaniq bo'lsa — qo'ng'iroq qiling.",
    'role-groom': 'Kuyov',
    'name-groom': 'Bobirjon',
    'role-bride': 'Kelin',
    'name-bride': 'Dilnoza',
    'role-coordinator': 'Tadbir tashkilotchisi',
    'name-coordinator': 'Tashkilotchi ismi',
    'role-restaurant': 'Restoran',

    'footer-names': 'Bobirjon va Dilnoza',
    'footer-meta': '2-oktabr 2026, Toshkent',
  },

  en: {
    'loading-text': 'Opening your invitation…',
    'error-title': "This link isn't valid",
    'error-body': "Double-check the link you were sent, or reach out to the couple.",

    'hero-eyebrow': 'Wedding',
    'hero-names': 'Bobirjon<span class="amp">and</span>Dilnoza',
    'hero-when': 'October 2, 2026, Friday<br><b>18:00</b>',
    'hero-place': 'Tashkent, Santini restaurant',

    'seat-eyebrow': 'A personal invitation for',
    'seat-table-label': 'your table',

    'cd-today': "Today! We can't wait to see you",

    'invite-p1': "On October 2nd we're finally getting married, and we'd love for you to be there with us. This evening just wouldn't be the same without you.",
    'invite-p2': 'Let this evening be remembered not for toasts and ceremony, but for how good it felt to simply be together.',
    'invite-sign': 'With love, Bobirjon and Dilnoza',

    'about-title': 'A little about us',
    'about-lede': 'Until the photographer hands over the wedding shots, here are a few snapshots from everyday life.',

    'day-title': 'How the day will go',
    'day-note': "Times are approximate — we'll confirm exact timing closer to the date.",
    'sched1-title': 'Gathering',
    'sched1-desc': "We welcome guests, pour some tea, and take photos. Seats are numbered — the seating chart will be at the hall entrance.",
    'sched2-title': 'Our entrance',
    'sched2-desc': 'The most exciting moment — please be in the hall by then.',
    'sched3-title': 'Dancing',
    'sched3-desc': 'DJ, live music, and traditional dances. Choose your shoes wisely.',
    'sched4-title': 'Farewell',
    'sched4-desc': "We'll say our goodbyes with a hug.",

    'where-title': 'Where',
    'venue-name': 'Santini Restaurant',
    'venue-lede': 'A large hall with a summer terrace and dance floor in the Yunusabad district. Parking is available, with plenty of room for everyone.',
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

    'contacts-title': 'Contacts',
    'contacts-lede': "If anything's unclear, just give us a call.",
    'role-groom': 'Groom',
    'name-groom': 'Bobirjon',
    'role-bride': 'Bride',
    'name-bride': 'Dilnoza',
    'role-coordinator': 'Coordinator',
    'name-coordinator': "Coordinator's name",
    'role-restaurant': 'Restaurant',

    'footer-names': 'Bobirjon and Dilnoza',
    'footer-meta': 'October 2, 2026, Tashkent',
  },
};

export function t(lang, key) {
  const code = normalizeLanguage(lang);
  return STRINGS[code][key] ?? STRINGS[DEFAULT_LANGUAGE][key] ?? key;
}

/** Sets every [data-i18n] element's innerHTML from the dictionary for `lang`. */
export function applyStaticTranslations(lang) {
  const code = normalizeLanguage(lang);
  document.documentElement.lang = code;
  document.querySelectorAll('[data-i18n]').forEach((el) => {
    const value = STRINGS[code][el.dataset.i18n];
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

const GALLERY_CAPTIONS = {
  ru: [
    'Первые кольца были из скрепок',
    'Бегаем вместе по утрам',
    'И гуляем, когда есть время',
  ],
  uz: [
    'Birinchi uzuklarimiz skrepkadan edi',
    'Har kuni ertalab birga yuguramiz',
    'Va vaqt topilganda sayr qilamiz',
  ],
  en: [
    'Our first rings were made of paperclips',
    'We go running together every morning',
    'And take walks whenever we find the time',
  ],
};

export function galleryCaptionsFor(lang) {
  return GALLERY_CAPTIONS[normalizeLanguage(lang)];
}

const MAP_QUERY = {
  ru: 'Ресторан Santini, Ташкент, улица Чинабад, 61/1',
  uz: "Santini restorani, Toshkent, Chinobod ko'chasi, 61/1",
  en: 'Santini Restaurant, Tashkent, Chinabad street, 61/1',
};

export function mapQueryFor(lang) {
  return MAP_QUERY[normalizeLanguage(lang)];
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
    photo: (n) => (n > 0 ? `Осталось ${n} фото` : 'Лимит фото исчерпан'),
    video: (n) => (n > 0 ? `Осталось ${n} видео` : 'Лимит видео исчерпан'),
  },
  uz: {
    photo: (n) => (n > 0 ? `Yana ${n} ta foto yuklash mumkin` : 'Foto limiti tugadi'),
    video: (n) => (n > 0 ? `Yana ${n} ta video yuklash mumkin` : 'Video limiti tugadi'),
  },
  en: {
    photo: (n) => (n > 0 ? `${n} photo${n === 1 ? '' : 's'} left` : 'Photo limit reached'),
    video: (n) => (n > 0 ? `${n} video${n === 1 ? '' : 's'} left` : 'Video limit reached'),
  },
};

export function remainingMediaText(lang, kind, n) {
  return REMAINING_TEMPLATE[normalizeLanguage(lang)][kind](n);
}

const DOCUMENT_TITLE_TEMPLATE = {
  ru: (name) => `${name} — приглашение на свадьбу Бобиржона и Дилнозы`,
  uz: (name) => `${name} — Bobirjon va Dilnoza to'yiga taklifnoma`,
  en: (name) => `${name} — invitation to Bobirjon and Dilnoza's wedding`,
};

export function documentTitleFor(lang, guestName) {
  return DOCUMENT_TITLE_TEMPLATE[normalizeLanguage(lang)](guestName);
}
