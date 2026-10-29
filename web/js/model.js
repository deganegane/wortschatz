// Data types and small helpers shared by the whole app (port of Model.kt).

export const PHRASE_RANK = 10000;
export const LEVEL_SIZE = 1000;
export const UNLOCK_SHARE = 0.8;
export const MASTERED_DAYS = 21;
export const BLANK = '_____';

export const Status = { UNTESTED: 0, KNOWN: 1, TO_LEARN: 2, LEARNING: 3 };
export const Grade = { AGAIN: 0, HARD: 1, GOOD: 2, EASY: 3 };
export const GRADE_LABELS = ['Opet', 'Teško', 'Dobro', 'Lako'];
export const Mode = { RECOGNIZE: 'R', RECALL: 'P', CLOZE: 'C' };
export const Verdict = { CORRECT: 'ok', ALMOST: 'almost', WRONG: 'wrong' };

export const PRACTICE = [
  ['CLASSIC', 'Klasično'],
  ['SPEAKING', 'Govor'],
  ['MIXED', 'Mješovito'],
];
export const THEMES = [
  ['SYSTEM', 'Sustav'],
  ['LIGHT', 'Svijetla'],
  ['DARK', 'Tamna'],
];

export const BUCKETS = [
  ['ALL', 'Sve'],
  ['UNTESTED', 'Netestirano'],
  ['TO_LEARN', 'Ne znam'],
  ['LEARNING', 'Učim'],
  ['MASTERED', 'Naučeno'],
  ['KNOWN', 'Znam'],
];
export const bucketLabel = (b) => BUCKETS.find((x) => x[0] === b)[1];

export const POS_LABELS = {
  n: 'imenica', v: 'glagol', adj: 'pridjev', adv: 'prilog', prep: 'prijedlog', konj: 'veznik',
  pron: 'zamjenica', art: 'član', num: 'broj', part: 'čestica', phr: 'izraz', intj: 'uzvik',
};

export const DEFAULT_SETTINGS = {
  newPerDay: 10,
  practice: 'MIXED',
  speakAnswers: true,
  retention: 0.9,
  autoSpeak: true,
  slowSpeech: false,
  themeMode: 'SYSTEM',
  level: 1,
  phrasesOn: true,
  voiceName: null,
  repeatNew: true,
  repeatAll: false,
  quiet: false,
  verifyKnown: true,
};

const LETTER = /[\p{L}]/u;

/** Index of [part] in [text] as a whole word (no letter right before or after), or -1. */
export function findWord(text, part, from = 0) {
  if (!part) return -1;
  let i = text.indexOf(part, from);
  while (i >= 0) {
    const before = i > 0 ? text[i - 1] : '';
    const after = text[i + part.length] || '';
    if (!LETTER.test(before) && !LETTER.test(after)) return i;
    i = text.indexOf(part, i + 1);
  }
  return -1;
}

/** Lower-case and strip accents, so "susse" finds "süße" and "cas" finds "čaš". */
export function fold(s) {
  return s.toLowerCase().normalize('NFD').replace(/\p{M}+/gu, '').replace(/ß/g, 'ss').replace(/đ/g, 'd');
}

/** Turns one entry of words.json into the object the app works with. */
export function makeWord(o) {
  const w = {
    rank: o.r,
    de: o.de,
    article: o.a || null,
    plural: o.pl || null,
    pos: o.pos || '',
    forms: o.f || null,
    hr: o.hr,
    example: o.ex || '',
    exampleHr: o.exHr || '',
    cloze: o.cl || [],
    cat: o.cat || null,
    gap: o.g && o.g.o && o.g.o.length >= 2 ? { blank: o.g.b, options: o.g.o, hint: o.g.h || '', explanation: o.g.e || '' } : null,
  };
  w.isPhrase = w.rank > PHRASE_RANK;
  w.full = w.article ? `${w.article} ${w.de}` : w.de;
  w.hrShort = w.hr.split(',')[0].split(';')[0].split(' (')[0].trim();
  w.clozeText = clozeText(w);
  w.searchKey = fold(`${w.de} ${w.hr} ${w.article || ''}`);
  w.spoken = w.article ? `${w.article} ${w.de}` : w.de.replace(/ \/ /g, ', ');
  return w;
}

function clozeText(w) {
  if (!w.cloze.length) return null;
  let t = w.example;
  for (const part of w.cloze) {
    const i = findWord(t, part);
    if (i < 0) return null;
    t = t.slice(0, i) + BLANK + t.slice(i + part.length);
  }
  return t;
}

export function makeStory(o) {
  const de = o.de || '';
  return {
    id: o.id,
    level: o.level || 1,
    title: o.title,
    de,
    hr: o.hr || '',
    newWords: o.new || [],
    wordCount: de.split(/\s+/).filter((t) => LETTER.test(t)).length,
  };
}

export const isMastered = (c) => !!c && c.status === Status.LEARNING && c.interval >= MASTERED_DAYS;

export function bucketOf(c) {
  if (!c || c.status === Status.UNTESTED) return 'UNTESTED';
  if (c.status === Status.KNOWN) return 'KNOWN';
  if (c.status === Status.TO_LEARN) return 'TO_LEARN';
  return isMastered(c) ? 'MASTERED' : 'LEARNING';
}

export const newCard = (status) => ({ status, interval: 0, stability: 0, difficulty: 0, due: 0, reps: 0, lapses: 0, lastReview: 0 });

/** Local calendar day as a day number since 1970-01-01 (same as Android's epoch day). */
export function today(now = new Date()) {
  return Math.floor(Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()) / 86400000);
}

export function dayToDate(day) {
  const d = new Date(day * 86400000);
  return new Date(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate());
}

const DAYS_HR = ['nedjelja', 'ponedjeljak', 'utorak', 'srijeda', 'četvrtak', 'petak', 'subota'];
const MONTHS_HR = ['siječnja', 'veljače', 'ožujka', 'travnja', 'svibnja', 'lipnja', 'srpnja', 'kolovoza', 'rujna', 'listopada', 'studenoga', 'prosinca'];

/** "Četvrtak, 29. listopada" */
export function longDate(day) {
  const d = dayToDate(day);
  const s = `${DAYS_HR[d.getDay()]}, ${d.getDate()}. ${MONTHS_HR[d.getMonth()]}`;
  return s[0].toUpperCase() + s.slice(1);
}

export function shortDate(day) {
  const d = dayToDate(day);
  return `${d.getDate()}. ${d.getMonth() + 1}. ${d.getFullYear()}.`;
}

/** Croatian plural: 1 riječ, 2 riječi, 5 riječi. */
export function plural(n, one, few, many) {
  const m10 = n % 10;
  const m100 = n % 100;
  if (m10 === 1 && m100 !== 11) return one;
  if (m10 >= 2 && m10 <= 4 && !(m100 >= 12 && m100 <= 14)) return few;
  return many;
}

export const isActive = (d) => !!d && (d.r > 0 || d.t > 0 || d.s > 0 || d.rd > 0 || d.g > 0);

export function shuffle(list) {
  const a = list.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}
