// Everything the learner produces, kept in this browser (localStorage), in the same JSON shape
// the Android app uses, so a backup can move between them.
import { DEFAULT_SETTINGS, Status, today } from './model.js';
import { migrate } from './srs.js';

const P = 'wortschatz.';

function get(key) {
  try {
    return localStorage.getItem(P + key);
  } catch (e) {
    return null;
  }
}

let warned = false;
function put(key, value) {
  try {
    localStorage.setItem(P + key, value);
    return true;
  } catch (e) {
    if (!warned) {
      warned = true;
      console.warn('Spremanje nije uspjelo', e);
    }
    return false;
  }
}

function parse(raw, fallback) {
  if (!raw) return fallback;
  try {
    return JSON.parse(raw);
  } catch (e) {
    return fallback;
  }
}

export function loadSettings() {
  const s = { ...DEFAULT_SETTINGS, ...parse(get('settings'), {}) };
  s.level = Math.max(1, s.level | 0);
  return s;
}

export const saveSettings = (s) => put('settings', JSON.stringify(s));

export function cardsFromJson(o) {
  const out = {};
  for (const [k, c] of Object.entries(o || {})) {
    const rank = parseInt(k, 10);
    if (!Number.isFinite(rank) || !c) continue;
    out[rank] = migrate({
      status: [0, 1, 2, 3].includes(c.s) ? c.s : 0,
      interval: +c.i || 0,
      stability: +c.S || 0,
      difficulty: +c.D || 0,
      due: +c.d || 0,
      reps: c.n | 0,
      lapses: c.l | 0,
      lastReview: +c.lr || 0,
    });
  }
  return out;
}

export function cardsToJson(cards) {
  const o = {};
  for (const [rank, c] of Object.entries(cards)) {
    if (c.status === Status.UNTESTED) continue;
    const j = { s: c.status };
    if (c.status === Status.LEARNING) {
      Object.assign(j, { i: c.interval, S: c.stability, D: c.difficulty, d: c.due, n: c.reps, l: c.lapses, lr: c.lastReview });
    }
    o[rank] = j;
  }
  return o;
}

export const loadCards = () => cardsFromJson(parse(get('cards'), {}));
export const saveCards = (cards) => put('cards', JSON.stringify(cardsToJson(cards)));

export function daysFromJson(o) {
  const out = {};
  for (const [k, d] of Object.entries(o || {})) {
    const day = parseInt(k, 10);
    if (!Number.isFinite(day) || !d) continue;
    out[day] = { r: d.r | 0, n: d.n | 0, t: d.t | 0, s: d.s | 0, rd: d.rd | 0, g: d.g | 0 };
  }
  return out;
}

export function daysToJson(days) {
  const cutoff = today() - 400;
  const o = {};
  for (const [k, d] of Object.entries(days)) if (+k >= cutoff) o[k] = d;
  return o;
}

export const loadDays = () => daysFromJson(parse(get('days'), {}));
export const saveDays = (days) => put('days', JSON.stringify(daysToJson(days)));

export const loadRead = () => new Set(parse(get('read'), []));
export const saveRead = (ids) => put('read', JSON.stringify([...ids]));

export function resetProgress() {
  try {
    ['cards', 'days', 'read'].forEach((k) => localStorage.removeItem(P + k));
  } catch (e) {
    /* nothing stored */
  }
}

export const getFlag = (k) => get('flag.' + k) === '1';
export const setFlag = (k, on = true) => put('flag.' + k, on ? '1' : '0');

/** A backup of all progress and settings, as one JSON document. */
export function exportAll(state) {
  return {
    app: 'wortschatz',
    version: 1,
    exported: new Date().toISOString(),
    settings: state.settings,
    cards: cardsToJson(state.cards),
    days: daysToJson(state.days),
    read: [...state.readIds],
  };
}

/** Reads a backup made by [exportAll]; throws when the file isn't one. */
export function parseBackup(text) {
  const o = JSON.parse(text);
  if (!o || o.app !== 'wortschatz' || typeof o.cards !== 'object') throw new Error('Ovo nije Wortschatz kopija.');
  return {
    settings: { ...DEFAULT_SETTINGS, ...(o.settings || {}) },
    cards: cardsFromJson(o.cards),
    days: daysFromJson(o.days),
    read: new Set(Array.isArray(o.read) ? o.read : []),
  };
}

/** Asks the browser not to evict our data (Safari grants it to installed web apps). */
export async function askPersist() {
  try {
    if (navigator.storage && navigator.storage.persist) return await navigator.storage.persist();
  } catch (e) {
    /* not supported */
  }
  return false;
}
