// Shared building blocks (port of Components.kt, Speaking.kt and Swipe.kt).
import { html, useEffect, useLayoutEffect, useRef, useState } from './vendor/htm-preact.js';
import { ICONS } from './icons.js';
import { POS_LABELS, Verdict, findWord, bucketLabel } from './model.js';

export { html };

export function Icon({ name, size = 24, color }) {
  const ic = ICONS[name];
  if (!ic) return null;
  return html`<svg width=${size} height=${size} viewBox=${ic.vb} aria-hidden="true" style=${color ? `color:${color}` : ''}>
    ${ic.p.map((p) => html`<path d=${p.d} fill="currentColor" />`)}
  </svg>`;
}

export const ARTICLE_VAR = { der: 'var(--der)', die: 'var(--die)', das: 'var(--das)' };

/** German headword with its article in the gender colour. */
export function Headword({ word, cls = '' }) {
  return html`<div class=${'headword ' + cls}>
    ${word.article && html`<span class="art" style=${`color:${ARTICLE_VAR[word.article]}`}>${word.article} </span>`}${word.de}
  </div>`;
}

export function grammarParts(word) {
  const parts = [];
  if (POS_LABELS[word.pos]) parts.push(POS_LABELS[word.pos]);
  if (word.cat) parts.push(word.cat);
  if (word.article && word.plural) parts.push(word.plural === '—' ? 'bez množine' : `mn. die ${word.plural}`);
  if (word.forms) parts.push(word.forms);
  return parts;
}

export function GrammarLine({ word, center }) {
  const parts = grammarParts(word);
  if (!parts.length) return null;
  return html`<div class=${'body-s muted' + (center ? ' center' : '')}>${parts.join('  ·  ')}</div>`;
}

export function SpeakButton({ onClick, size = 44, label = 'Izgovori' }) {
  return html`<button class="speak" style=${`width:${size}px;height:${size}px`} aria-label=${label}
    onClick=${(e) => { e.stopPropagation(); onClick(); }}>
    <${Icon} name="volume" size=${Math.round(size * 0.48)} />
  </button>`;
}

export function ExampleBlock({ word, onSpeak }) {
  if (!word.example) return null;
  return html`<div class="example">
    <div class="grow">
      <div class="body-l" style="font-weight:500">${word.example}</div>
      <div class="sp4"></div>
      <div class="body-m muted italic">${word.exampleHr}</div>
    </div>
    <${SpeakButton} size=${40} onClick=${() => onSpeak(word.example)} label="Izgovori rečenicu" />
  </div>`;
}

export const BUCKET_COLOR = {
  KNOWN: 'var(--good)',
  MASTERED: 'var(--primary)',
  LEARNING: 'var(--secondary)',
  TO_LEARN: 'var(--bad)',
  UNTESTED: 'var(--outline)',
  ALL: 'var(--outline)',
};

export const Dot = ({ bucket, color }) => html`<span class="dot" style=${`background:${color || BUCKET_COLOR[bucket]}`}></span>`;

export const Pill = ({ text, bg, fg, onClick }) =>
  onClick
    ? html`<button class="pill" style=${`background:${bg};color:${fg}`} onClick=${onClick}>${text}</button>`
    : html`<span class="pill" style=${`background:${bg};color:${fg}`}>${text}</span>`;

export const SectionTitle = ({ text, style = '' }) => html`<div class="section-title" style=${style}>${text}</div>`;

export function Legend({ items }) {
  return html`<div class="legend">
    ${items.map(([b, n]) => html`<div class="row gap8">
      <${Dot} bucket=${b} /><span class="body-m grow">${bucketLabel(b)}</span><span class="label muted">${n}</span>
    </div>`)}
  </div>`;
}

export function ActionBar({ children, cls = '' }) {
  return html`<div class=${'actionbar ' + cls}>${children}</div>`;
}

export function ActionItem({ icon, label, enabled = true, color, onClick }) {
  return html`<button class="action" disabled=${!enabled} onClick=${onClick} style=${color ? `color:${color}` : ''}>
    <${Icon} name=${icon} size=${24} />
    <span class="label-s">${label}</span>
  </button>`;
}

export function Progress({ value, thin, color }) {
  const pct = Math.max(0, Math.min(1, value || 0)) * 100;
  return html`<div class=${'progress' + (thin ? ' thin' : '')}><div style=${`width:${pct}%` + (color ? `;background:${color}` : '')}></div></div>`;
}

export function TopBar({ onClose, progress, count }) {
  return html`<div class="topbar">
    <button class="icon-btn" aria-label="Zatvori" onClick=${onClose}><${Icon} name="close" /></button>
    <div class="grow"><${Progress} value=${progress} /></div>
    <span class="label muted count">${count}</span>
  </div>`;
}

export function Sheet({ onClose, children }) {
  useEscape(onClose);
  return html`<div class="scrim" onClick=${onClose}>
    <div class="sheet" role="dialog" onClick=${(e) => e.stopPropagation()}>
      <div class="grip"></div>
      ${children}
    </div>
  </div>`;
}

export function Dialog({ title, text, confirm, danger, onConfirm, onClose }) {
  useEscape(onClose);
  return html`<div class="scrim center" onClick=${onClose}>
    <div class="dialog" role="alertdialog" onClick=${(e) => e.stopPropagation()}>
      <div class="title-l">${title}</div>
      <div class="sp12"></div>
      <div class="body-m muted">${text}</div>
      <div class="acts">
        <button class="btn text" onClick=${onClose}>Odustani</button>
        <button class="btn text" style=${danger ? 'color:var(--error)' : ''} onClick=${onConfirm}>${confirm}</button>
      </div>
    </div>
  </div>`;
}

function useEscape(fn) {
  const ref = useRef(fn);
  ref.current = fn;
  useEffect(() => {
    const h = (e) => e.key === 'Escape' && ref.current();
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, []);
}

export function Toggle({ title, sub, checked, onChange }) {
  return html`<button class="toggle-row" role="switch" aria-checked=${checked} onClick=${() => onChange(!checked)}>
    <div class="grow">
      <div class="body-l">${title}</div>
      ${sub && html`<div class="body-s muted">${sub}</div>`}
    </div>
    <span class=${'switch' + (checked ? ' on' : '')}></span>
  </button>`;
}

export function Segmented({ options, value, label, onSelect }) {
  return html`<div class="seg" role="radiogroup">
    ${options.map((o) => html`<button role="radio" aria-checked=${o === value} class=${o === value ? 'on' : ''} onClick=${() => onSelect(o)}>${label(o)}</button>`)}
  </div>`;
}

// ---- text helpers ---------------------------------------------------------

/** The example with the cloze parts highlighted. */
export function highlighted(word, color) {
  const text = word.example;
  const ranges = [];
  for (const part of word.cloze) {
    const i = findWord(text, part);
    if (i >= 0) ranges.push([i, i + part.length]);
  }
  ranges.sort((a, b) => a[0] - b[0]);
  const out = [];
  let i = 0;
  for (const [a, b] of ranges) {
    if (a < i) continue;
    out.push(text.slice(i, a));
    out.push(html`<span class="mark" style=${`color:${color}`}>${text.slice(a, b)}</span>`);
    i = b;
  }
  out.push(text.slice(i));
  return out;
}

/** Shadowing: heard words in green, missed ones in red. */
export function matched(sentence, match) {
  let t = 0;
  return sentence.split(' ').map((w, i) => {
    const sep = i > 0 ? ' ' : '';
    if (!/[\p{L}\p{N}]/u.test(w)) return sep + w;
    const ok = match[t++];
    return html`${sep}<span style=${`color:${ok ? 'var(--good)' : 'var(--bad)'};font-weight:500`}>${w}</span>`;
  });
}

// ---- microphone -----------------------------------------------------------

export function MicButton({ listen, onStart, onStop }) {
  const on = listen.phase === 'LISTENING';
  return html`<div class=${'mic' + (on ? ' on' : '')}>
    ${on && html`<div class="halo"></div>`}
    <button aria-label=${on ? 'Gotovo' : 'Govori'} onClick=${() => (on ? onStop() : onStart())}>
      <${Icon} name=${on ? 'stop' : 'mic'} size=${34} />
    </button>
  </div>`;
}

export function ListenStatus({ listen, hint }) {
  let text = hint;
  if (listen.phase === 'LISTENING') text = listen.partial ? `„${listen.partial}“` : 'Slušam…';
  else if (listen.phase === 'ERROR') text = listen.error || 'Nešto je pošlo krivo.';
  return html`<div class=${'body-m center ' + (listen.phase === 'ERROR' ? 'err' : 'muted')} style="padding:0 8px">${text}</div>`;
}

export function MicRow({ listen, hint, onStart, onStop, left }) {
  return html`<div class="col" style="align-items:stretch">
    <${ListenStatus} listen=${listen} hint=${hint} />
    <div class="sp8"></div>
    <div class="microw">
      <div class="side">${left}</div>
      <${MicButton} listen=${listen} onStart=${onStart} onStop=${onStop} />
      <div class="side"></div>
    </div>
  </div>`;
}

export function FeedbackBanner({ check, word, cloze }) {
  let color;
  let title;
  if (check.verdict === Verdict.CORRECT) {
    color = 'var(--good)';
    title = 'Točno!';
  } else if (check.verdict === Verdict.ALMOST) {
    color = 'var(--primary)';
    if (check.wrongArticle) title = `Skoro — ${word.article} ${word.de}, ne ${check.wrongArticle}`;
    else if (!cloze && word.article) title = `Točno — ali reci i član: ${word.full}`;
    else title = 'Skoro — fali dio odgovora';
  } else {
    color = 'var(--bad)';
    title = check.heard ? 'Nije to' : 'Nisam ništa čuo';
  }
  return html`<div class="feedback center" style=${`background:color-mix(in srgb, ${color} 14%, transparent);color:${color}`}>
    <div class="title-m">${title}</div>
    ${check.heard && html`<div class="body-s muted">Čuo sam: „${check.heard}“</div>`}
  </div>`;
}

// ---- swipe ----------------------------------------------------------------

/**
 * Swipe left/right anywhere on the screen (so the thumb can stay low) moves the card.
 * Returns props for the swipe area and a ref for the card that follows the finger.
 */
export function useSwipe({ enabled, key, onDecide }) {
  const card = useRef(null);
  const st = useRef({ deciding: false });
  const cb = useRef(onDecide);
  cb.current = onDecide;

  const paint = (dx, animate) => {
    const el = card.current;
    if (!el) return;
    el.style.transition = animate ? 'transform 0.2s ease-out' : 'none';
    el.style.transform = dx ? `translateX(${dx}px) rotate(${dx / 22}deg)` : '';
    const w = el.offsetWidth || 300;
    const k = Math.min(1, Math.abs(dx) / (w * 0.25));
    const l = el.querySelector('.hint.l');
    const r = el.querySelector('.hint.r');
    if (l) l.style.opacity = dx < 0 ? k : 0;
    if (r) r.style.opacity = dx > 0 ? k : 0;
  };

  useLayoutEffect(() => {
    st.current = { deciding: false };
    paint(0, false);
  }, [key]);

  const down = (e) => {
    if (!enabled || st.current.deciding) return;
    if (e.pointerType === 'mouse' && e.button !== 0) return;
    if (e.target.closest && e.target.closest('input, .no-swipe')) return;
    st.current = { deciding: false, id: e.pointerId, x: e.clientX, y: e.clientY, dir: null, dx: 0, lx: e.clientX, lt: e.timeStamp, v: 0 };
  };
  const move = (e) => {
    const s = st.current;
    if (s.id !== e.pointerId || s.deciding) return;
    const dx = e.clientX - s.x;
    const dy = e.clientY - s.y;
    if (!s.dir) {
      if (Math.abs(dx) > 10 && Math.abs(dx) > Math.abs(dy) * 1.2) {
        s.dir = 'h';
        try {
          e.currentTarget.setPointerCapture(e.pointerId);
        } catch (err) {
          /* capture is optional */
        }
      } else if (Math.abs(dy) > 10) {
        s.dir = 'v';
      } else return;
    }
    if (s.dir !== 'h') return;
    const dt = e.timeStamp - s.lt;
    if (dt > 0) s.v = (e.clientX - s.lx) / dt;
    s.lx = e.clientX;
    s.lt = e.timeStamp;
    s.dx = dx;
    paint(dx, false);
  };
  const up = (e) => {
    const s = st.current;
    if (s.id !== e.pointerId) return;
    s.id = null;
    if (s.dir !== 'h') return;
    s.dragged = true;
    setTimeout(() => (s.dragged = false), 60);
    const el = card.current;
    const w = (el && el.offsetWidth) || 300;
    const fling = Math.abs(s.v) > 0.6 && Math.abs(s.dx) > 40 && Math.sign(s.v) === Math.sign(s.dx);
    if (e.type !== 'pointercancel' && (Math.abs(s.dx) > w * 0.25 || fling)) {
      const right = s.dx > 0;
      s.deciding = true;
      if (navigator.vibrate) navigator.vibrate(12);
      paint((right ? 1 : -1) * w * 1.4, true);
      // Bound now: if a button changes the card during the animation, this answer still
      // belongs to the card that was swiped (and its guard rejects it).
      const decide = cb.current;
      setTimeout(() => st.current === s && decide(right), 170);
      // If the answer was refused (the card didn't change), bring the card back.
      setTimeout(() => {
        if (st.current === s && s.deciding) {
          s.deciding = false;
          paint(0, true);
        }
      }, 700);
    } else {
      paint(0, true);
    }
  };
  const clickCapture = (e) => {
    if (st.current.dragged) {
      e.stopPropagation();
      e.preventDefault();
    }
  };
  return {
    area: { onPointerDown: down, onPointerMove: move, onPointerUp: up, onPointerCancel: up, onClickCapture: clickCapture },
    card,
  };
}

export const SwipeHints = ({ left, right }) => html`<div class="hint l">${left}</div><div class="hint r">${right}</div>`;

/** Keeps a component re-rendering with the app state. */
export function useAppState(app) {
  const [s, setS] = useState(app.state);
  useEffect(() => {
    setS(app.state); // the state may have changed before this effect ran
    return app.subscribe(setS);
  }, []);
  return s;
}
