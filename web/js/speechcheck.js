// Compares what the speech recogniser heard with the expected answer (port of SpeechCheck).
import { Verdict } from './model.js';

const ARTICLES = new Set(['der', 'die', 'das']);
const NUMBERS = {
  0: 'null', 1: 'eins', 2: 'zwei', 3: 'drei', 4: 'vier', 5: 'fünf', 6: 'sechs', 7: 'sieben', 8: 'acht', 9: 'neun',
  10: 'zehn', 11: 'elf', 12: 'zwölf', 20: 'zwanzig', 30: 'dreißig', 50: 'fünfzig', 100: 'hundert', 1000: 'tausend',
};

/** Accents that aren't part of German spelling ("Café" → "cafe"); ä/ö/ü stay. */
const stripForeign = (s) =>
  s.replace(/[éèê]/g, 'e').replace(/[áà]/g, 'a').replace(/í/g, 'i').replace(/ó/g, 'o').replace(/ú/g, 'u').replace(/ç/g, 'c');

export function normalize(s) {
  return stripForeign(s.toLowerCase())
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .trim()
    .split(' ')
    .filter((t) => t)
    .map((t) => NUMBERS[t] || t)
    .join(' ')
    .replace(/ß/g, 'ss');
}

export const tokens = (s) => normalize(s).split(' ').filter((t) => t);

function distance(a, b) {
  let prev = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    const cur = [i];
    for (let j = 1; j <= b.length; j++) {
      cur[j] = Math.min(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
    }
    prev = cur;
  }
  return prev[b.length];
}

/** Same word, allowing one slip for longer words (recognisers mangle endings). */
export function same(a, b) {
  if (a === b) return true;
  if (a === 'eins' && (b === 'ein' || b === 'eine')) return true;
  if (b === 'eins' && (a === 'ein' || a === 'eine')) return true;
  return Math.max(a.length, b.length) >= 5 && distance(a, b) <= 1;
}

function containsPhrase(hay, needle) {
  if (!needle.length) return -1;
  for (let i = 0; i <= hay.length - needle.length; i++) {
    if (needle.every((n, k) => same(hay[i + k], n))) return i;
  }
  return -1;
}

const result = (verdict, heard, wrongArticle = null) => ({ verdict, heard, wrongArticle });

/** Say the German word for the Croatian prompt. */
export function checkWord(word, results) {
  if (!results.length) return result(Verdict.WRONG, '');
  if (word.isPhrase) return checkPhrase(word, results);
  const target = tokens(word.de.replace(/^sich /, ''));
  let alternatives;
  if (word.rank === 1) alternatives = [['der'], ['die'], ['das']];
  // "E-Mail" may come back as "Email", "wie viel" as "wieviel".
  else if (target.length > 1) alternatives = [target, [target.join('')]];
  else alternatives = [target];
  let almost = null;
  for (const heard of results) {
    const hay = tokens(heard);
    for (const alt of alternatives) {
      const at = containsPhrase(hay, alt);
      if (at < 0) continue;
      if (!word.article) return result(Verdict.CORRECT, heard);
      const said = at > 0 && ARTICLES.has(hay[at - 1]) ? hay[at - 1] : null;
      if (said === word.article) return result(Verdict.CORRECT, heard);
      if (said === null) {
        if (!almost) almost = result(Verdict.ALMOST, heard);
      } else if (!almost || !almost.wrongArticle) {
        almost = result(Verdict.ALMOST, heard, said);
      }
    }
  }
  return almost || result(Verdict.WRONG, results[0]);
}

/** A whole phrase: most of its words, in order, is good enough. */
function checkPhrase(word, results) {
  let best = 0;
  let bestHeard = results[0];
  for (const heard of results) {
    const m = matchSentence(word.de, [heard]);
    const share = m.length ? m.filter(Boolean).length / m.length : 0;
    if (share > best) {
      best = share;
      bestHeard = heard;
    }
  }
  if (best >= 0.75) return result(Verdict.CORRECT, bestHeard);
  if (best >= 0.5) return result(Verdict.ALMOST, bestHeard);
  return result(Verdict.WRONG, results[0]);
}

/** Say the missing word(s) of the sentence — or the whole sentence. */
export function checkCloze(word, results) {
  if (!results.length) return result(Verdict.WRONG, '');
  const parts = word.cloze.map(tokens);
  for (const heard of results) {
    const hay = tokens(heard);
    if (parts.every((p) => containsPhrase(hay, p) >= 0)) return result(Verdict.CORRECT, heard);
  }
  // Separable verb with only the main part said ("stehe" without "auf").
  if (parts.length > 1) {
    const len = (p) => p.reduce((n, t) => n + t.length, 0);
    const main = parts.reduce((a, b) => (len(b) > len(a) ? b : a));
    for (const heard of results) {
      if (containsPhrase(tokens(heard), main) >= 0) return result(Verdict.ALMOST, heard);
    }
  }
  // The dictionary form instead of the one the sentence needs.
  const base = tokens(word.de.replace(/^sich /, ''));
  for (const heard of results) {
    if (containsPhrase(tokens(heard), base) >= 0) return result(Verdict.ALMOST, heard);
  }
  return result(Verdict.WRONG, results[0]);
}

/** Which words of [sentence] were heard, in order (for shadowing). */
export function matchSentence(sentence, results) {
  const words = (s) => s.split(' ').map((t) => normalize(t).replace(/ /g, '')).filter((t) => t);
  const target = words(sentence);
  let best = target.map(() => false);
  for (const heard of results) {
    const hay = words(heard);
    let j = 0;
    const m = target.map((t) => {
      for (let k = j; k < hay.length && k < j + 4; k++) {
        if (same(hay[k], t)) {
          j = k + 1;
          return true;
        }
      }
      return false;
    });
    if (m.filter(Boolean).length > best.filter(Boolean).length) best = m;
  }
  return best;
}

export const suggested = (check) => (check.verdict === Verdict.CORRECT ? 2 : check.verdict === Verdict.ALMOST ? 1 : 0);
