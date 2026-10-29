// German pronunciation (speechSynthesis) and speech recognition (Web Speech API).

const synth = typeof window !== 'undefined' ? window.speechSynthesis : null;

// Fun voices Apple ships in every language; never a good default for learning.
const NOVELTY = /eloquence|grandma|grandpa|eddy|flo\b|reed|rocko|sandy|shelley|bahh|bells|boing|bubbles|cellos|wobble|bad news|good news|jester|organ|superstar|trinoids|whisper|zarvox|albert|fred|junior|kathy|ralph/i;

function quality(v) {
  const id = `${v.voiceURI} ${v.name}`.toLowerCase();
  if (id.includes('premium')) return 3;
  if (id.includes('enhanced') || id.includes('neural') || id.includes('natural')) return 2;
  if (NOVELTY.test(id)) return 0;
  return 1;
}

function describe(v, i) {
  const country = /-AT/i.test(v.lang) ? 'austrijski' : /-CH/i.test(v.lang) ? 'švicarski' : 'njemački';
  const q = ['zabavni glas', 'obična kvaliteta', 'poboljšana kvaliteta', 'vrhunska kvaliteta'][quality(v)];
  const name = v.name.replace(/\s*\((Enhanced|Premium|Poboljšano|Vrhunsko)\)\s*/i, '').replace(/^Microsoft |^Google /, '');
  return {
    name: v.voiceURI || v.name,
    label: `${name || 'Glas ' + (i + 1)} · ${country} · ${q}` + (v.localService ? '' : ' · online'),
    network: !v.localService,
    quality: quality(v),
    voice: v,
  };
}

export class Speech {
  constructor(onVoices) {
    this.voices = [];
    this.current = null;
    this.onVoices = onVoices;
    this.utterances = [];
    this.token = 0;
    this.state = synth ? 'LOADING' : 'NO_ENGINE';
    if (!synth) return;
    const load = () => this.loadVoices();
    // Not from inside the constructor: the callback may use the object being built.
    Promise.resolve().then(load);
    if (synth.addEventListener) synth.addEventListener('voiceschanged', load);
    else synth.onvoiceschanged = load;
    // Some browsers never fire voiceschanged when the list is already there.
    setTimeout(load, 600);
    setTimeout(() => {
      if (this.state === 'LOADING') {
        this.state = this.voices.length ? 'READY' : 'NO_GERMAN';
        this.onVoices(this.state, this.voices);
      }
    }, 5000);
  }

  loadVoices() {
    const all = synth.getVoices() || [];
    if (!all.length) return;
    const german = all.filter((v) => /^de([-_]|$)/i.test(v.lang));
    german.sort((a, b) => quality(b) - quality(a) || (b.localService ? 1 : 0) - (a.localService ? 1 : 0) || (/de-DE/i.test(b.lang) ? 1 : 0) - (/de-DE/i.test(a.lang) ? 1 : 0) || a.name.localeCompare(b.name));
    this.voices = german.map(describe);
    this.state = this.voices.length ? 'READY' : 'NO_GERMAN';
    this.choose(this.wanted);
    this.onVoices(this.state, this.voices);
  }

  /** The named voice, or else the best offline one. */
  choose(name) {
    this.wanted = name;
    this.current =
      this.voices.find((v) => v.name === name) ||
      this.voices.find((v) => !v.network && v.quality > 0) ||
      this.voices.find((v) => v.quality > 0) ||
      this.voices[0] ||
      null;
  }

  /** Speaks [text]; [onDone] runs once it has finished (not when interrupted). */
  speak(text, slow, onDone) {
    const token = ++this.token;
    if (!synth || !text || !text.trim()) {
      if (onDone) setTimeout(onDone, 0);
      return;
    }
    synth.cancel();
    // Long texts are spoken sentence by sentence (Chrome cuts long utterances off).
    const parts = text.length > 180 ? text.match(/[^.!?…\n]+[.!?…“"]*[\s\n]*/g) || [text] : [text];
    this.utterances = parts.map((p) => {
      const u = new SpeechSynthesisUtterance(p.trim());
      u.lang = this.current ? this.current.voice.lang : 'de-DE';
      try {
        if (this.current) u.voice = this.current.voice;
      } catch (e) {
        /* the browser rejected the voice: use its default German one */
      }
      u.rate = slow ? 0.7 : 0.95;
      return u;
    });
    const last = this.utterances[this.utterances.length - 1];
    let finished = false;
    const done = () => {
      if (finished || token !== this.token) return;
      finished = true;
      clearTimeout(this.guard);
      if (onDone) onDone();
    };
    last.onend = done;
    last.onerror = done;
    if (onDone) {
      // iOS sometimes never reports the end: give up waiting after a generous estimate.
      const ms = 1500 + text.length * (slow ? 130 : 90);
      clearTimeout(this.guard);
      this.guard = setTimeout(done, ms);
    }
    this.utterances.forEach((u) => synth.speak(u));
  }

  stop() {
    this.token++;
    clearTimeout(this.guard);
    if (synth) synth.cancel();
  }
}

const Recognition = typeof window !== 'undefined' ? window.SpeechRecognition || window.webkitSpeechRecognition : null;

export const recognitionAvailable = !!Recognition;

function setAudioSession(type) {
  try {
    if (navigator.audioSession) navigator.audioSession.type = type;
  } catch (e) {
    /* not supported */
  }
}

/**
 * One listening attempt at a time. Callbacks of an attempt that was stopped or replaced are
 * ignored, so a late result can never land on the next card.
 */
export class Listener {
  constructor({ onPartial, onResult, onError }) {
    this.onPartial = onPartial;
    this.onResult = onResult;
    this.onError = onError;
    this.gen = 0;
    this.rec = null;
  }

  start() {
    if (!Recognition) {
      this.onError('Prepoznavanje govora nije dostupno u ovom pregledniku.');
      return;
    }
    this.abort();
    const gen = ++this.gen;
    const rec = new Recognition();
    this.rec = rec;
    rec.lang = 'de-DE';
    rec.interimResults = true;
    rec.continuous = false;
    rec.maxAlternatives = 5;
    let partial = '';
    let delivered = false;
    const deliver = (list) => {
      if (delivered || gen !== this.gen) return;
      delivered = true;
      clearTimeout(this.timer);
      setAudioSession('playback');
      this.onResult(list);
    };
    rec.onresult = (e) => {
      if (gen !== this.gen) return;
      let finals = null;
      let interim = '';
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const r = e.results[i];
        if (r.isFinal) {
          finals = [];
          for (let k = 0; k < r.length; k++) if (r[k].transcript.trim()) finals.push(r[k].transcript.trim());
        } else {
          interim += r[0].transcript;
        }
      }
      if (interim.trim()) {
        partial = interim.trim();
        this.onPartial(partial);
      }
      if (finals && finals.length) deliver(finals);
    };
    rec.onerror = (e) => {
      if (gen !== this.gen || delivered) return;
      if (e.error === 'no-speech' || e.error === 'aborted') return; // onend delivers what we have
      delivered = true;
      clearTimeout(this.timer);
      setAudioSession('playback');
      this.onError(errorText(e.error), e.error);
    };
    rec.onend = () => {
      if (gen !== this.gen) return;
      if (partial) {
        deliver([partial]);
      } else if (!delivered) {
        // Nothing heard: let them try again instead of counting it as a wrong answer.
        delivered = true;
        clearTimeout(this.timer);
        setAudioSession('playback');
        this.onError('Nisam ništa čuo — dodirni mikrofon i pokušaj ponovno.', 'no-speech');
      }
    };
    this.deliverPartial = () => {
      if (gen !== this.gen) return;
      if (partial) deliver([partial]);
      else rec.onend();
    };
    setAudioSession('play-and-record');
    try {
      rec.start();
    } catch (err) {
      delivered = true;
      setAudioSession('playback');
      this.onError('Mikrofon se nije uspio uključiti. Probaj još jednom.');
      return;
    }
    // Safety net: never listen forever.
    this.timer = setTimeout(() => {
      if (gen === this.gen) this.finish();
    }, 9000);
  }

  /** Stop early; whatever was said so far is still evaluated. */
  finish() {
    if (!this.rec) return;
    const gen = this.gen;
    try {
      this.rec.stop();
    } catch (e) {
      /* already stopped */
    }
    // iOS sometimes never reports the end after stop(): use what was heard so far.
    clearTimeout(this.endGuard);
    this.endGuard = setTimeout(() => {
      if (gen === this.gen && this.deliverPartial) this.deliverPartial();
    }, 1500);
  }

  /** Stop and drop the attempt. */
  abort() {
    this.gen++;
    clearTimeout(this.timer);
    clearTimeout(this.endGuard);
    setAudioSession('playback');
    if (this.rec) {
      try {
        this.rec.abort();
      } catch (e) {
        /* already stopped */
      }
      this.rec = null;
    }
  }
}

function errorText(code) {
  switch (code) {
    case 'not-allowed':
      return 'Bez dopuštenja za mikrofon ne mogu čuti odgovor. Dopusti mikrofon i prepoznavanje govora (na iPhoneu: Postavke → Safari → Mikrofon) ili uključi Tihi način.';
    case 'service-not-allowed':
      return 'Prepoznavanje govora ovdje nije dopušteno. Na iPhoneu uključi Diktiranje (Postavke → Općenito → Tipkovnica) ili otvori stranicu u Safariju. Do tada odgovaraj dodirom.';
    case 'audio-capture':
      return 'Mikrofon nije pronađen.';
    case 'network':
      return 'Prepoznavanje govora treba internet.';
    case 'language-not-supported':
      return 'Prepoznavanje njemačkog nije dostupno na ovom uređaju.';
    default:
      return 'Nešto je pošlo krivo s mikrofonom. Probaj još jednom.';
  }
}
