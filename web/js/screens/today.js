import { html, useState } from '../vendor/htm-preact.js';
import { Icon, SectionTitle, Legend, BUCKET_COLOR } from '../ui.js';
import { derive } from '../app-state.js';
import { longDate, plural } from '../model.js';
import * as Store from '../store.js';

const isIos = /iP(hone|ad|od)/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
const standalone = () => navigator.standalone === true || matchMedia('(display-mode: standalone)').matches;

export function TodayTab({ app, s, onOpenTest, onOpenSettings }) {
  const d = derive(s);
  const due = d.dueToday;
  const fresh = d.newLeftToday;
  const pinned = due + fresh > 0 || d.newAvailable > 0;
  const [showProgress, setShowProgress] = useState(false);
  const [installHidden, setInstallHidden] = useState(() => Store.getFlag('installHint'));
  const showInstall = isIos && !standalone() && !installHidden;

  const tiles = [];
  if (d.hasGaps) tiles.push(['grammar', 'Gramatika', '10 rečenica', 'var(--primary)', () => app.startGrammar()]);
  tiles.push(['shadow', 'Shadowing', 'slušaj i ponovi', 'var(--secondary)', () => app.startShadowing()]);
  tiles.push(['quiz', 'Test razine', `${d.tested} / ${d.vocab.length}`, 'var(--der)', onOpenTest]);
  if (d.hardWords.length >= 5) {
    const n = d.hardWords.length;
    tiles.push(['hard', 'Teške riječi', `${n} ${plural(n, 'riječ', 'riječi', 'riječi')}`, 'var(--die)', () => app.startHardSession()]);
  }

  return html`<div class="tab">
    <div class="scroll pad" style=${`padding-bottom:${pinned ? 88 : 16}px`}>
      <div class="row" style="padding:12px 0 0 8px">
        <div class="grow">
          <h1 class="h1">Danas</h1>
          <div class="body-m muted">${longDate(s.today)}</div>
        </div>
        <button class="icon-btn" aria-label="Postavke" onClick=${onOpenSettings}><${Icon} name="settings" /></button>
      </div>
      <div class="sp16"></div>
      <${Summary} s=${s} d=${d} due=${due} fresh=${fresh} />

      ${showInstall && html`<div class="sp12"></div>
        <${Banner} color="var(--primary)" title="Dodaj na početni zaslon"
          text="U Safariju: Dijeli → Dodaj na početni zaslon. Tako radi kao aplikacija i napredak se sigurno čuva."
          action="U redu" onAction=${() => { Store.setFlag('installHint'); setInstallHidden(true); }} />`}
      ${d.canUnlock && html`<div class="sp12"></div>
        <${Banner} color="var(--secondary)" title="Razina svladana!"
          text=${`Otključaj riječi ${d.vocab.length + 1}–${(s.settings.level + 1) * 1000}.`}
          action="Otključaj" onAction=${() => app.setLevel(s.settings.level + 1)} />`}
      ${(s.voice === 'NO_GERMAN' || s.voice === 'NO_ENGINE') && html`<div class="sp12"></div>
        <${Banner} color="var(--error)" title="Nema njemačkog glasa"
          text=${isIos ? 'Dodaj ga u Postavke → Pristupačnost → Izgovoreni sadržaj → Glasovi → Njemački.' : 'Ovaj preglednik nema njemački glas za izgovor.'} />`}

      <div class="sp24"></div>
      <${SectionTitle} text="Vježbaj" style="padding-left:8px" />
      <div class="tiles">
        ${tiles.map(([icon, title, sub, color, onClick]) => html`<button class="tile" onClick=${onClick}>
          <div class="ico" style=${`background:color-mix(in srgb, ${color} 16%, transparent);color:${color}`}><${Icon} name=${icon} size=${20} /></div>
          <div class="title-s t">${title}</div>
          <div class="body-s muted s">${sub}</div>
        </button>`)}
      </div>

      <div class="sp24"></div>
      <div class="card tap" style="padding:16px 20px" onClick=${() => setShowProgress(!showProgress)}>
        <div class="row">
          <div class="grow">
            <div class="title-s">Napredak</div>
            <div class="body-s muted">tvojih ${d.vocab.length} riječi · zadnja 2 tjedna</div>
          </div>
          <span class="muted"><${Icon} name=${showProgress ? 'chevUp' : 'chevDown'} /></span>
        </div>
        ${showProgress && html`<div class="reveal">
          <div class="sp16"></div>
          <${Breakdown} d=${d} />
          <div class="sp12"></div>
          <${Legend} items=${['KNOWN', 'MASTERED', 'LEARNING', 'TO_LEARN', 'UNTESTED'].map((b) => [b, d.counts[b]])} />
          <div class="sp20"></div>
          <${Activity} s=${s} />
        </div>`}
      </div>
      <div class="sp16"></div>
    </div>

    ${pinned && html`<div class="pinned" style="position:absolute;left:0;right:0;bottom:0">
      ${due + fresh > 0
        ? html`<button class="btn big" onClick=${() => app.startSession()}>Kreni (${due + fresh})</button>`
        : html`<button class="btn big neutral" onClick=${() => app.startSession(5)}>Još 5 novih riječi</button>`}
    </div>`}
  </div>`;
}

function Summary({ s, d, due, fresh }) {
  const total = Math.max(1, d.vocab.length);
  const p = Math.min(1, d.familiar / total);
  const R = 47;
  const C = 2 * Math.PI * R;
  const t = d.todayStat;
  const left = due + fresh;
  return html`<div class="summary">
    <div class="ring">
      <svg viewBox="0 0 104 104" width="104" height="104">
        <circle cx="52" cy="52" r=${R} fill="none" stroke="currentColor" stroke-opacity="0.14" stroke-width="10" />
        ${p > 0 && html`<circle cx="52" cy="52" r=${R} fill="none" stroke="var(--primary)" stroke-width="10" stroke-linecap="round"
          stroke-dasharray=${`${C * Math.max(p, 0.01)} ${C}`} style="transition:stroke-dasharray 0.9s ease" />`}
      </svg>
      <div class="v">
        <div class="h2">${d.familiar}</div>
        <div class="label-s">od ${d.vocab.length}</div>
      </div>
    </div>
    <div class="grow">
      <div class="title-m">${left > 0 ? `Čeka te ${left} ${plural(left, 'kartica', 'kartice', 'kartica')}` : 'Gotovo za danas!'}</div>
      <div class="ministats">
        <div><div class="n">${t.r}</div><div class="l">ponovljeno</div></div>
        <div><div class="n">${t.n}</div><div class="l">novih</div></div>
        <div><div class="n" style=${d.streak > 0 ? 'color:var(--secondary)' : ''}>${d.streak}</div><div class="l">${plural(d.streak, 'dan', 'dana', 'dana')} niz</div></div>
      </div>
      ${s.settings.level < d.maxLevel && html`
        <div class="levelbar"><div style=${`width:${Math.min(1, d.solid / Math.max(1, d.unlockTarget)) * 100}%`}></div></div>
        <div class="label-s" style="margin-top:4px;opacity:0.8">${d.solid} / ${d.unlockTarget} do razine ${s.settings.level + 1}</div>`}
    </div>
  </div>`;
}

function Banner({ color, title, text, action, onAction }) {
  return html`<div class="banner">
    <span class="dot" style=${`background:${color}`}></span>
    <div class="grow">
      <div class="title-s">${title}</div>
      <div class="body-s muted">${text}</div>
    </div>
    ${action && html`<button class="btn text" onClick=${onAction}>${action}</button>`}
  </div>`;
}

function Breakdown({ d }) {
  const total = Math.max(1, d.vocab.length);
  return html`<div class="breakdown">
    ${['KNOWN', 'MASTERED', 'LEARNING', 'TO_LEARN', 'UNTESTED'].map((b) =>
      d.counts[b] ? html`<div style=${`width:${(d.counts[b] / total) * 100}%;background:${BUCKET_COLOR[b]}`}></div>` : null)}
  </div>`;
}

function Activity({ s }) {
  const values = [];
  for (let i = 13; i >= 0; i--) {
    const st = s.days[s.today - i];
    values.push(st ? st.r + Math.floor(st.t / 5) + st.s + st.g + st.rd * 5 : 0);
  }
  const max = Math.max(10, ...values);
  return html`<div>
    <div class="chart">
      ${values.map((v) => html`<div style=${v === 0 ? 'height:4px;background:var(--s-highest)' : `height:${Math.max(0.08, Math.min(1, v / max)) * 100}%;background:var(--primary)`}></div>`)}
    </div>
    <div class="sp6"></div>
    <div class="row label-s muted" style="justify-content:space-between"><span>prije 2 tj.</span><span>danas</span></div>
  </div>`;
}
