// FSRS-5 scheduling with default parameters (port of Srs.kt).
import { Status, Grade, Mode, newCard, isActive } from './model.js';

/** How many cards later a card answered "Opet" comes back in the same session. */
export const AGAIN_GAP = 3;

const W = [
  0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575, 0.1192,
  1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621,
];
const DECAY = -0.5;
const FACTOR = 19 / 81;
const MAX_DAYS = 365;

const clamp = (x, lo, hi) => Math.min(hi, Math.max(lo, x));
const g = (grade) => grade + 1; // AGAIN=1 … EASY=4

const initStability = (grade) => W[g(grade) - 1];
const initDifficulty = (grade) => clamp(W[4] - Math.exp(W[5] * (g(grade) - 1)) + 1, 1, 10);

function nextDifficulty(d, grade) {
  const delta = -W[6] * (g(grade) - 3);
  const damped = d + (delta * (10 - d)) / 9;
  return clamp(W[7] * initDifficulty(Grade.EASY) + (1 - W[7]) * damped, 1, 10);
}

export const retrievability = (days, s) => Math.pow(1 + (FACTOR * days) / s, DECAY);
const intervalFor = (s, retention) => (s / FACTOR) * (Math.pow(retention, 1 / DECAY) - 1);

function recallStability(d, s, r, grade) {
  const hard = grade === Grade.HARD ? W[15] : 1;
  const easy = grade === Grade.EASY ? W[16] : 1;
  return s * (Math.exp(W[8]) * (11 - d) * Math.pow(s, -W[9]) * (Math.exp(W[10] * (1 - r)) - 1) * hard * easy + 1);
}

function forgetStability(d, s, r) {
  const f = W[11] * Math.pow(d, -W[12]) * (Math.pow(s + 1, W[13]) - 1) * Math.exp(W[14] * (1 - r));
  return Math.min(f, s / Math.exp(W[17] * W[18]));
}

const shortTermStability = (s, grade) => s * Math.exp(W[17] * (g(grade) - 3 + W[18]));

/** Cards scheduled by the old SM-2 version get a stability from their interval. */
export function migrate(c) {
  if (c.status !== Status.LEARNING || c.stability > 0 || c.reps === 0) return c;
  const s = Math.max(1, c.interval);
  return { ...c, stability: s, difficulty: 5, lastReview: c.due - Math.round(c.interval) };
}

export function schedule(card, grade, day, retention) {
  const c = migrate(card);
  const first = c.status !== Status.LEARNING || c.stability <= 0;
  const elapsed = day - c.lastReview;
  let s;
  let d;
  if (first) {
    s = initStability(grade);
    d = initDifficulty(grade);
  } else if (elapsed < 1) {
    s = shortTermStability(c.stability, grade);
    d = nextDifficulty(c.difficulty, grade);
  } else {
    const r = retrievability(elapsed, c.stability);
    s = grade === Grade.AGAIN ? forgetStability(c.difficulty, c.stability, r) : recallStability(c.difficulty, c.stability, r, grade);
    d = nextDifficulty(c.difficulty, grade);
  }
  const stability = clamp(s, 0.1, 36500);
  let days;
  if (grade === Grade.AGAIN) days = 0;
  // A brand-new word was only seen, not recalled: bring it back soon regardless.
  else if (first) days = grade === Grade.EASY ? 3 : 1;
  else days = clamp(Math.round(intervalFor(stability, retention)), 1, MAX_DAYS);
  const lapse = grade === Grade.AGAIN && !first && elapsed >= 1;
  return {
    status: Status.LEARNING,
    interval: days,
    stability,
    difficulty: d,
    due: day + days,
    reps: c.reps + 1,
    lapses: lapse ? c.lapses + 1 : c.lapses,
    lastReview: day,
  };
}

/** Days until the next review for each grade, kept in order (Teško ≤ Dobro < Lako). */
export function preview(card, day, retention) {
  const c = card || newCard(Status.TO_LEARN);
  const raw = [0, 1, 2, 3].map((gr) => Math.round(schedule(c, gr, day, retention).interval));
  const good = raw[Grade.GOOD];
  raw[Grade.HARD] = Math.min(raw[Grade.HARD], good);
  raw[Grade.EASY] = Math.min(MAX_DAYS, Math.max(raw[Grade.EASY], good + 1));
  return raw;
}

/** Schedule an answer, respecting the ordering shown on the buttons. */
export function answer(card, grade, day, retention) {
  const c = card || newCard(Status.TO_LEARN);
  const result = schedule(c, grade, day, retention);
  if (grade === Grade.AGAIN) return result;
  const days = preview(c, day, retention)[grade];
  return { ...result, interval: days, due: day + days };
}

export function label(days) {
  if (days <= 0) return '<1 min';
  if (days < 14) return `${days} d`;
  if (days < 60) return `${Math.round(days / 7)} tj.`;
  if (days < 365) return `${Math.round(days / 30)} mj.`;
  return '1 g.';
}

/**
 * Today's queue: everything due (in the active list), with new words mixed in as every
 * fourth card.
 */
export function buildQueue(words, byRank, cards, settings, newDoneToday, day) {
  const due = Object.entries(cards)
    .filter(([r, c]) => c.status === Status.LEARNING && c.due <= day && byRank.has(+r))
    .sort((a, b) => a[1].due - b[1].due || a[0] - b[0])
    .map(([r]) => +r);
  const newCount = Math.max(0, settings.newPerDay - newDoneToday);
  const fresh = newCandidates(words, cards).slice(0, newCount);
  const out = [];
  let di = 0;
  let ni = 0;
  let pos = 0;
  while (di < due.length || ni < fresh.length) {
    const takeNew = ni < fresh.length && (di >= due.length || pos % 4 === 3);
    if (takeNew) {
      // New words are always introduced German first, so you hear and see them.
      out.push({ rank: fresh[ni++], mode: Mode.RECOGNIZE, isNew: true });
    } else {
      const r = due[di++];
      out.push({ rank: r, mode: pickMode(byRank.get(r), cards[r], settings.practice), isNew: false });
    }
    pos++;
  }
  return out;
}

/** Young cards are first recognised; then increasingly produced (said aloud, or filled into the sentence). */
export function pickMode(word, card, practice) {
  const canCloze = !!(word && word.clozeText);
  const reps = card ? card.reps : 0;
  if (practice === 'CLASSIC') return Mode.RECOGNIZE;
  if (practice === 'SPEAKING') return canCloze && Math.random() < 0.5 ? Mode.CLOZE : Mode.RECALL;
  if (reps < 2) return Mode.RECOGNIZE;
  const k = Math.floor(Math.random() * 3);
  if (k === 0) return Mode.RECOGNIZE;
  if (k === 1) return Mode.RECALL;
  return canCloze ? Mode.CLOZE : Mode.RECALL;
}

/**
 * Words that could be introduced next: first the ones marked "ne znam", then untested ones,
 * in rank order — with a conversational phrase as every third new card.
 */
export function newCandidates(words, cards) {
  const pending = (list) => {
    const toLearn = list.filter((w) => cards[w.rank] && cards[w.rank].status === Status.TO_LEARN).map((w) => w.rank);
    const untested = list.filter((w) => !cards[w.rank] || cards[w.rank].status === Status.UNTESTED).map((w) => w.rank);
    return toLearn.concat(untested);
  };
  const vocab = pending(words.filter((w) => !w.isPhrase));
  const phrases = pending(words.filter((w) => w.isPhrase));
  if (!phrases.length) return vocab;
  if (!vocab.length) return phrases;
  const out = [];
  let v = 0;
  let p = 0;
  let i = 0;
  while (v < vocab.length || p < phrases.length) {
    const takePhrase = p < phrases.length && (v >= vocab.length || i % 3 === 2);
    out.push(takePhrase ? phrases[p++] : vocab[v++]);
    i++;
  }
  return out;
}

/** Learning words that keep failing: by lapses, then FSRS difficulty. */
export function hardWords(cards, limit = 20) {
  return Object.entries(cards)
    .filter(([, c]) => c.status === Status.LEARNING && (c.lapses >= 1 || c.difficulty >= 7.5))
    .sort((a, b) => b[1].lapses - a[1].lapses || b[1].difficulty - a[1].difficulty)
    .slice(0, limit)
    .map(([r]) => +r);
}

/** Consecutive active days ending today (or yesterday, if today is still empty). */
export function streak(days, day) {
  let d = isActive(days[day]) ? day : day - 1;
  let n = 0;
  while (isActive(days[d])) {
    n++;
    d--;
  }
  return n;
}
