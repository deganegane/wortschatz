// The app's state and every action on it (port of AppViewModel.kt).
import {
  Status, Grade, Mode, LEVEL_SIZE, UNLOCK_SHARE, newCard, bucketOf, today, makeWord, makeStory, shuffle,
} from './model.js';
import * as Srs from './srs.js';
import * as Check from './speechcheck.js';
import * as Store from './store.js';
import { Speech, Listener, recognitionAvailable } from './speech.js';

const IDLE = { phase: 'IDLE', partial: '', error: null };
const emptyDay = () => ({ r: 0, n: 0, t: 0, s: 0, rd: 0, g: 0 });

/** Values computed from a state object (cached per object). */
const cache = new WeakMap();
export function derive(s) {
  let d = cache.get(s);
  if (d) return d;
  const vocab = s.words.filter((w) => !w.isPhrase);
  const phrases = s.words.filter((w) => w.isPhrase);
  const counts = { ALL: vocab.length, UNTESTED: 0, TO_LEARN: 0, LEARNING: 0, MASTERED: 0, KNOWN: 0 };
  for (const w of vocab) counts[bucketOf(s.cards[w.rank])]++;
  const todayStat = s.days[s.today] || emptyDay();
  const newAvailable =
    counts.TO_LEARN + counts.UNTESTED +
    phrases.filter((w) => {
      const c = s.cards[w.rank];
      return !c || c.status === Status.UNTESTED || c.status === Status.TO_LEARN;
    }).length;
  let dueToday = 0;
  for (const [r, c] of Object.entries(s.cards)) {
    if (c.status === Status.LEARNING && c.due <= s.today && s.byRank.has(+r)) dueToday++;
  }
  const budget = Math.max(0, s.settings.newPerDay - todayStat.n);
  const maxLevel = Math.ceil(s.allCount / LEVEL_SIZE);
  const solid = counts.KNOWN + counts.MASTERED;
  const unlockTarget = Math.floor(vocab.length * UNLOCK_SHARE);
  const next = vocab.find((w) => !s.cards[w.rank] || s.cards[w.rank].status === Status.UNTESTED);
  d = {
    vocab,
    phrases,
    counts,
    todayStat,
    newAvailable,
    dueToday,
    newLeftToday: Math.min(budget, newAvailable),
    streak: Srs.streak(s.days, s.today),
    maxLevel,
    solid,
    unlockTarget,
    canUnlock: s.settings.level < maxLevel && solid >= unlockTarget,
    familiar: counts.KNOWN + counts.MASTERED + counts.LEARNING,
    tested: vocab.length - counts.UNTESTED,
    nextTestRank: next ? next.rank : null,
    hardWords: Srs.hardWords(s.cards).filter((r) => s.byRank.has(r)),
    hasGaps: vocab.some((w) => w.gap),
    storiesRead: s.stories.filter((x) => s.readIds.has(x.id)).length,
  };
  cache.set(s, d);
  return d;
}

export class App {
  constructor() {
    const settings = Store.loadSettings();
    this.allWords = [];
    this.allByRank = new Map();
    this.forms = null;
    this.listeners = new Set();
    this.listenFor = 'REVIEW';
    this.knownSinceVerify = 0;
    this.state = {
      loading: true,
      error: null,
      words: [],
      byRank: new Map(),
      allCount: 0,
      cards: {},
      days: {},
      settings,
      voice: 'LOADING',
      voices: [],
      session: null,
      shadowing: null,
      grammar: null,
      testVerify: null,
      stories: [],
      readIds: new Set(),
      listen: IDLE,
      micBlocked: false,
      autoMicOff: false,
      formsReady: false,
      testUndo: [],
      today: today(),
      toast: null,
    };
    this.recognition = recognitionAvailable;
    this.speech = new Speech((voice, voices) => {
      this.speech.choose(this.state.settings.voiceName);
      this.set({ voice, voices });
    });
    this.listener = new Listener({
      onPartial: (partial) => {
        if (this.state.listen.phase === 'LISTENING') this.set({ listen: { phase: 'LISTENING', partial, error: null } });
      },
      onResult: (results) => this.onHeard(results),
      onError: (error, code) => {
        if (this.state.listen.phase !== 'LISTENING') return;
        const blocked = code === 'not-allowed' || code === 'service-not-allowed';
        this.set({
          listen: { phase: 'ERROR', partial: '', error },
          // A refused tap hides the microphone; a refused automatic start only stops the automatic ones.
          micBlocked: this.state.micBlocked || (blocked && this.userStarted),
          autoMicOff: this.state.autoMicOff || blocked,
        });
      },
    });
  }

  // ---- plumbing ----------------------------------------------------------

  subscribe(fn) {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  set(patch) {
    const p = typeof patch === 'function' ? patch(this.state) : patch;
    this.state = { ...this.state, ...p };
    this.listeners.forEach((fn) => fn(this.state));
  }

  word(rank) {
    return this.state.byRank.get(rank) || null;
  }

  async load() {
    try {
      const [words, stories] = await Promise.all([
        fetch('data/words.json').then((r) => {
          if (!r.ok) throw new Error(r.status);
          return r.json();
        }),
        fetch('data/stories.json').then((r) => (r.ok ? r.json() : [])).catch(() => []),
      ]);
      this.allWords = words.map(makeWord).sort((a, b) => a.rank - b.rank);
      this.allWords.forEach((w) => this.allByRank.set(w.rank, w));
      const active = this.activeWords(this.state.settings);
      this.set({
        loading: false,
        words: active,
        byRank: new Map(active.map((w) => [w.rank, w])),
        allCount: this.allWords.filter((w) => !w.isPhrase).length,
        cards: Store.loadCards(),
        days: Store.loadDays(),
        stories: stories.map(makeStory),
        readIds: Store.loadRead(),
        today: today(),
      });
      Store.askPersist();
    } catch (e) {
      this.set({ loading: false, error: 'Riječi se nisu učitale. Provjeri internet i osvježi stranicu.' });
    }
  }

  refreshDay() {
    const t = today();
    if (t !== this.state.today) this.set({ today: t });
  }

  /** Back in the app: settings may have been fixed meanwhile, so try the microphone again. */
  resumed() {
    this.refreshDay();
    if (this.state.micBlocked || this.state.autoMicOff) this.set({ micBlocked: false, autoMicOff: false });
  }

  toast(text) {
    clearTimeout(this.toastTimer);
    this.set({ toast: text });
    this.toastTimer = setTimeout(() => this.set({ toast: null }), 3500);
  }

  persist(cards, days) {
    let ok = true;
    if (cards) ok = Store.saveCards(cards) && ok;
    if (days) ok = Store.saveDays(days) && ok;
    if (!ok) this.toast('Napredak se ne može spremiti (privatni način ili pun prostor).');
  }

  dayPatch(fn) {
    const s = this.state;
    const day = { ...(s.days[s.today] || emptyDay()) };
    fn(day);
    return { ...s.days, [s.today]: day };
  }

  // ---- speech ------------------------------------------------------------

  speak(text) {
    if (this.state.listen.phase === 'LISTENING') this.cancelListening();
    this.speech.speak(text, this.state.settings.slowSpeech);
  }

  speakSlow(text) {
    if (this.state.listen.phase === 'LISTENING') this.cancelListening();
    this.speech.speak(text, true);
  }

  // ---- listening ---------------------------------------------------------

  listen() {
    const s = this.state;
    let target = 'REVIEW';
    if (s.shadowing && !s.session) target = 'SHADOW';
    else if (s.session && (s.session.revealed || (s.session.queue[0] && s.session.queue[0].isNew))) target = 'REPEAT';
    this.startListening(target, true);
  }

  startListening(target, user = false) {
    if (this.state.listen.phase === 'LISTENING') return;
    this.speech.stop();
    this.listenFor = target;
    this.userStarted = user;
    this.set((st) => ({
      listen: { phase: 'LISTENING', partial: '', error: null },
      session: target === 'REPEAT' && st.session ? { ...st.session, repeat: null } : st.session,
    }));
    this.listener.start();
  }

  /** Plays the word, then turns the microphone on so it can be repeated aloud. */
  speakThenRepeat(text) {
    const s = this.state;
    if (!s.session || !s.session.queue[0]) return;
    const token = `${s.session.queue[0].rank}:${s.session.reviewed}`;
    const go = () => {
      const now = this.state.session;
      if (now && now.queue[0] && `${now.queue[0].rank}:${now.reviewed}` === token && !now.repeat && !this.state.micBlocked && !this.state.autoMicOff) {
        this.startListening('REPEAT');
      }
    };
    if (s.settings.autoSpeak) this.speech.speak(text, s.settings.slowSpeech, go);
    else go();
  }

  finishListening() {
    this.listener.finish();
  }

  cancelListening() {
    this.speech.stop();
    this.listener.abort();
    this.set({ listen: IDLE });
  }

  onHeard(results) {
    const s = this.state;
    if (s.listen.phase !== 'LISTENING') return;
    const done = { phase: 'DONE', partial: '', error: null };
    if (this.listenFor === 'SHADOW') {
      const sh = s.shadowing;
      const word = sh && this.word(sh.ranks[sh.index]);
      if (!word) return;
      const match = Check.matchSentence(word.example, results);
      this.set({ listen: done, shadowing: { ...sh, match, heard: results[0] || '', attempts: sh.attempts + 1 } });
      return;
    }
    const session = s.session;
    const item = session && session.queue[0];
    const word = item && this.word(item.rank);
    if (!word) return;
    if (this.listenFor === 'REPEAT') {
      this.set({ listen: done, session: { ...session, repeat: Check.checkWord(word, results) } });
      return;
    }
    const check = item.mode === Mode.CLOZE ? Check.checkCloze(word, results) : Check.checkWord(word, results);
    this.set({ listen: done, session: { ...session, revealed: true, check } });
    if (s.settings.autoSpeak) this.speak(item.mode === Mode.CLOZE ? word.example : word.spoken);
  }

  // ---- review session ----------------------------------------------------

  makeSession(queue) {
    return { queue, revealed: false, check: null, repeat: null, reviewed: 0, again: 0, newSeen: 0, plannedTotal: queue.length };
  }

  startSession(extraNew = 0) {
    this.refreshDay();
    const s = this.state;
    const d = derive(s);
    const settings = { ...s.settings, newPerDay: Math.max(s.settings.newPerDay, d.todayStat.n) + extraNew };
    const queue = Srs.buildQueue(s.words, s.byRank, s.cards, settings, d.todayStat.n, s.today);
    this.set({ session: this.makeSession(queue), listen: IDLE });
  }

  closeSession() {
    this.speech.stop();
    this.listener.abort();
    this.set({ session: null, listen: IDLE });
  }

  reveal() {
    this.listener.abort();
    this.set((st) => ({ session: st.session && { ...st.session, revealed: true }, listen: IDLE }));
  }

  /** [expectReviewed] guards a swipe that finishes after the card already changed. */
  answer(grade, expectReviewed) {
    const s = this.state;
    const session = s.session;
    if (!session) return;
    if (expectReviewed != null && session.reviewed !== expectReviewed) return;
    const item = session.queue[0];
    if (!item) return;
    const old = s.cards[item.rank];
    const wasNew = !old || old.status !== Status.LEARNING;
    this.listener.abort();
    const updated = Srs.answer(old, grade, s.today, s.settings.retention);
    const cards = { ...s.cards, [item.rank]: updated };
    const days = this.dayPatch((d) => {
      d.r++;
      if (wasNew) d.n++;
    });
    const rest = session.queue.slice(1);
    if (grade === Grade.AGAIN) rest.splice(Math.min(Srs.AGAIN_GAP, rest.length), 0, { ...item, isNew: false });
    this.set({
      cards,
      days,
      listen: IDLE,
      session: {
        ...session,
        queue: rest,
        revealed: false,
        check: null,
        repeat: null,
        reviewed: session.reviewed + 1,
        again: session.again + (grade === Grade.AGAIN ? 1 : 0),
        newSeen: session.newSeen + (wasNew ? 1 : 0),
      },
    });
    this.persist(cards, days);
  }

  /** "Već znam" on a new card: mark it known and bring in the next new word instead. */
  skipAsKnown() {
    this.listener.abort();
    this.speech.stop();
    const s = this.state;
    const session = s.session;
    const item = session && session.queue[0];
    if (!item) return;
    const cards = { ...s.cards, [item.rank]: newCard(Status.KNOWN) };
    const inQueue = new Set(session.queue.map((q) => q.rank));
    const replacement = Srs.newCandidates(s.words, cards).find((r) => !inQueue.has(r));
    const rest = session.queue.slice(1);
    if (replacement != null) rest.splice(Math.min(1, rest.length), 0, { rank: replacement, mode: Mode.RECOGNIZE, isNew: true });
    this.set({
      cards,
      listen: IDLE,
      session: {
        ...session,
        queue: rest,
        revealed: false,
        check: null,
        repeat: null,
        plannedTotal: session.plannedTotal - (replacement == null ? 1 : 0),
      },
    });
    this.persist(cards);
  }

  startHardSession() {
    this.refreshDay();
    const s = this.state;
    const queue = derive(s).hardWords
      .map((rank) => {
        const w = this.word(rank);
        if (!w) return null;
        const mode = s.settings.practice === 'CLASSIC' ? Mode.RECOGNIZE : w.clozeText && Math.random() < 0.5 ? Mode.CLOZE : Mode.RECALL;
        return { rank, mode, isNew: false };
      })
      .filter(Boolean);
    if (queue.length) this.set({ session: this.makeSession(queue), listen: IDLE });
  }

  // ---- shadowing ---------------------------------------------------------

  startShadowing(count = 10) {
    const s = this.state;
    const vocab = derive(s).vocab.filter((w) => w.example);
    const familiar = shuffle(
      vocab.filter((w) => {
        const st = s.cards[w.rank] && s.cards[w.rank].status;
        return st === Status.KNOWN || st === Status.LEARNING;
      }),
    )
      .slice(0, count)
      .map((w) => w.rank);
    const have = new Set(familiar);
    const fill = vocab.map((w) => w.rank).filter((r) => !have.has(r)).slice(0, count - familiar.length);
    this.set({ shadowing: { ranks: shuffle(familiar.concat(fill)), index: 0, match: null, heard: '', attempts: 0, done: 0 }, listen: IDLE });
  }

  shadowNext() {
    this.listener.abort();
    const s = this.state;
    const sh = s.shadowing;
    if (!sh) return;
    const tried = sh.attempts > 0;
    const days = tried ? this.dayPatch((d) => d.s++) : s.days;
    this.set({
      days,
      listen: IDLE,
      shadowing: { ...sh, index: sh.index + 1, match: null, heard: '', attempts: 0, done: sh.done + (tried ? 1 : 0) },
    });
    if (tried) this.persist(null, days);
  }

  shadowRetry() {
    this.set((st) => ({ shadowing: st.shadowing && { ...st.shadowing, match: null, heard: '' }, listen: IDLE }));
  }

  closeShadowing() {
    this.speech.stop();
    this.listener.abort();
    this.set({ shadowing: null, listen: IDLE });
  }

  // ---- placement test ----------------------------------------------------

  testAnswer(rank, known) {
    const s = this.state;
    if (derive(s).nextTestRank !== rank) return;
    if (s.testVerify) {
      if (s.testVerify.rank === rank) return;
      this.set({ testVerify: null });
    }
    if (known && s.settings.verifyKnown) {
      this.knownSinceVerify++;
      if (this.knownSinceVerify >= 10) {
        const v = this.makeVerify(rank);
        if (v) {
          this.knownSinceVerify = 0;
          this.set({ testVerify: v });
          return;
        }
      }
    }
    this.commitTest(rank, known);
  }

  makeVerify(rank) {
    const word = this.word(rank);
    if (!word) return null;
    const right = word.hrShort;
    const vocab = derive(this.state).vocab;
    let pool = vocab.filter((w) => w.rank !== rank && w.pos === word.pos && w.hrShort && w.hrShort !== right);
    if (!pool.length) pool = vocab.filter((w) => w.rank !== rank && w.hrShort !== right);
    const wrong = [...new Set(shuffle(pool).map((w) => w.hrShort))].slice(0, 3);
    if (wrong.length < 3) return null;
    const options = shuffle(wrong.concat(right));
    return { rank, options, correct: options.indexOf(right), chosen: null };
  }

  verifyChoose(i) {
    const v = this.state.testVerify;
    if (!v || v.chosen != null) return;
    this.set({ testVerify: { ...v, chosen: i } });
  }

  verifyContinue() {
    const v = this.state.testVerify;
    if (!v || v.chosen == null) return;
    this.set({ testVerify: null });
    this.commitTest(v.rank, v.chosen === v.correct);
  }

  commitTest(rank, known) {
    const s = this.state;
    if (derive(s).nextTestRank !== rank) return;
    const prev = s.cards[rank] || null;
    const set = newCard(known ? Status.KNOWN : Status.TO_LEARN);
    const cards = { ...s.cards, [rank]: set };
    const days = this.dayPatch((d) => d.t++);
    this.set({ cards, days, testUndo: s.testUndo.concat([[rank, prev, set]]).slice(-50) });
    this.persist(cards, days);
  }

  testUndo() {
    const s = this.state;
    if (s.testVerify || !s.testUndo.length) return;
    const [rank, prev, set] = s.testUndo[s.testUndo.length - 1];
    const now = s.cards[rank];
    const same = now && now.status === set.status && now.reps === set.reps && now.due === set.due;
    if (!same) {
      // The word has moved on since (learned, edited in the dictionary): don't undo that.
      this.set({ testUndo: s.testUndo.slice(0, -1) });
      return;
    }
    const cards = { ...s.cards };
    if (prev) cards[rank] = prev;
    else delete cards[rank];
    const days = this.dayPatch((d) => (d.t = Math.max(0, d.t - 1)));
    this.set({ cards, days, testUndo: s.testUndo.slice(0, -1) });
    this.persist(cards, days);
  }

  // ---- dictionary --------------------------------------------------------

  setStatus(rank, status) {
    const s = this.state;
    const cards = { ...s.cards };
    if (status === Status.UNTESTED) delete cards[rank];
    else if (status !== Status.LEARNING) cards[rank] = newCard(status);
    this.set({ cards, testVerify: null });
    this.persist(cards);
  }

  // ---- settings ----------------------------------------------------------

  activeWords(settings) {
    return this.allWords.filter((w) => w.rank <= settings.level * LEVEL_SIZE || (settings.phrasesOn && w.isPhrase));
  }

  setLevel(level) {
    const l = Math.min(Math.max(1, level), Math.max(1, derive(this.state).maxLevel));
    this.updateSettings({ level: l });
  }

  updateSettings(patch) {
    const prev = this.state.settings;
    const next = { ...prev, ...patch };
    const changed = next.level !== prev.level || next.phrasesOn !== prev.phrasesOn;
    const words = changed ? this.activeWords(next) : this.state.words;
    this.set({ settings: next, words, byRank: changed ? new Map(words.map((w) => [w.rank, w])) : this.state.byRank });
    Store.saveSettings(next);
    this.speech.choose(next.voiceName);
  }

  resetProgress() {
    Store.resetProgress();
    this.set({ cards: {}, days: {}, readIds: new Set(), testUndo: [], session: null, testVerify: null });
  }

  exportBackup() {
    return Store.exportAll(this.state);
  }

  importBackup(text) {
    const b = Store.parseBackup(text);
    Store.saveCards(b.cards);
    Store.saveDays(b.days);
    Store.saveRead(b.read);
    this.set({ cards: b.cards, days: b.days, readIds: b.read, testUndo: [], testVerify: null });
    this.updateSettings({ ...b.settings, voiceName: this.state.settings.voiceName });
    return Object.keys(b.cards).length;
  }

  // ---- stories -----------------------------------------------------------

  ensureForms() {
    if (this.forms || this.formsLoading) return;
    this.formsLoading = true;
    fetch('data/forms.json')
      .then((r) => (r.ok ? r.json() : {}))
      .then((f) => {
        this.forms = f;
        this.set({ formsReady: true });
      })
      .catch(() => {})
      .finally(() => {
        this.formsLoading = false;
      });
  }

  lookup(token) {
    const raw = token.toLowerCase().replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, '');
    if (!raw || !this.forms) return null;
    const key = raw.split("'")[0].split('’')[0];
    const rank = this.forms[raw] ?? this.forms[key] ?? this.forms[key.replace(/ß/g, 'ss')];
    return rank != null ? this.allByRank.get(rank) || null : null;
  }

  markRead(id) {
    const s = this.state;
    if (s.readIds.has(id)) return;
    const read = new Set(s.readIds);
    read.add(id);
    const days = this.dayPatch((d) => d.rd++);
    this.set({ readIds: read, days });
    Store.saveRead(read);
    this.persist(null, days);
  }

  // ---- grammar -----------------------------------------------------------

  startGrammar(count = 10) {
    this.refreshDay();
    const s = this.state;
    const withGap = derive(s).vocab.filter((w) => w.gap);
    const isFamiliar = (w) => {
      const st = s.cards[w.rank] && s.cards[w.rank].status;
      return st === Status.KNOWN || st === Status.LEARNING;
    };
    const familiar = withGap.filter(isFamiliar);
    const pool = familiar.length >= count ? familiar : familiar.concat(withGap.filter((w) => !isFamiliar(w)));
    const ranks = shuffle(pool).slice(0, count).map((w) => w.rank);
    if (ranks.length) this.set({ grammar: { ranks, index: 0, chosen: null, correct: 0, wrong: 0 } });
  }

  grammarChoose(option) {
    const s = this.state;
    const g = s.grammar;
    if (!g || g.chosen != null) return;
    const word = this.word(g.ranks[g.index]);
    if (!word || !word.gap) return;
    const ok = option === word.gap.blank;
    this.set({ grammar: { ...g, chosen: option, correct: g.correct + (ok ? 1 : 0), wrong: g.wrong + (ok ? 0 : 1) } });
    if (ok && s.settings.autoSpeak) this.speak(word.example);
  }

  grammarNext() {
    const s = this.state;
    const g = s.grammar;
    if (!g) return;
    const next = { ...g, index: g.index + 1, chosen: null };
    const finished = next.index >= next.ranks.length;
    const days = finished ? this.dayPatch((d) => (d.g += g.ranks.length)) : s.days;
    this.set({ grammar: next, days });
    if (finished) this.persist(null, days);
  }

  closeGrammar() {
    this.speech.stop();
    this.set({ grammar: null });
  }
}
