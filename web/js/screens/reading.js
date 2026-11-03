import { html, useEffect, useRef, useState } from '../vendor/htm-preact.js';
import { Icon, Headword, GrammarLine, ExampleBlock, SpeakButton, SectionTitle, Pill, Dot, ActionBar, ActionItem, Sheet } from '../ui.js';
import { derive } from '../app-state.js';
import { bucketOf, LEVEL_SIZE, RATINGS } from '../model.js';
import { statusText } from './dictionary.js';

const FILTERS = [
  ['ALL', 'Sve'],
  ['STORIES', 'Priče'],
  ['DIALOGS', 'Dijalozi'],
];

const meta = (x) => (x.isDialog ? 'dijalog · ' : '') + `${x.wordCount} riječi` + (x.newWords.length ? ` · ${x.newWords.length} nove` : '');
const ratingLabel = (r) => (RATINGS.find((x) => x[0] === r) || [0, ''])[1];

export function ReadingTab({ app, s, openStory, onOpen }) {
  const story = openStory ? s.stories.find((x) => x.id === openStory) : null;
  const [filter, setFilter] = useState('ALL');
  if (story) return html`<${StoryScreen} app=${app} s=${s} story=${story} onBack=${() => onOpen(null)} onOpen=${onOpen} />`;
  const d = derive(s);
  const shown = s.stories.filter((x) => filter === 'ALL' || (filter === 'DIALOGS') === x.isDialog);
  const levels = [...new Set(shown.map((x) => x.level))].sort((a, b) => a - b);
  const rated = new Map(s.ratings);
  const next = d.recommended;
  return html`<div class="tab">
    <div class="scroll" style="padding-bottom:24px">
      <div class="pad20" style="padding-top:12px">
        <h1 class="h1">Čitanje</h1>
        <div class="body-m muted">Priče i dijalozi od riječi koje učiš. Dodirni riječ za prijevod, zadrži prst na rečenici za njen prijevod. Pročitano: ${d.storiesRead} od ${s.stories.length}.</div>
      </div>
      ${next && html`<button class="recommend" onClick=${() => onOpen(next.id)}>
        <div class="label" style="opacity:0.8">${s.ratings.length ? 'PREPORUKA ZA TEBE' : 'ZA POČETAK'}</div>
        <div class="sp4"></div>
        <div class="title-l">${next.title}</div>
        <div class="body-s" style="opacity:0.8">${meta(next)}</div>
      </button>`}
      ${s.stories.some((x) => x.isDialog) && html`<div class="chips" style="padding:4px 20px">
        ${FILTERS.map(([f, label]) => html`<button class=${'chip small' + (filter === f ? ' on' : '')} onClick=${() => setFilter(f)}>${label}</button>`)}
      </div>`}
      ${!s.stories.length && html`<div class="pad20 muted">Nema priča.</div>`}
      ${levels.map((level) => html`
        <div class="row pad20" style="padding-top:10px;padding-bottom:10px">
          <div class="grow"><${SectionTitle} text=${`Razina ${level} · ${level * 1000} riječi`} style="margin:0" /></div>
          ${level > s.settings.level && html`<span class="label muted">još zaključano</span>`}
        </div>
        ${shown.filter((x) => x.level === level).map((x) => {
          const read = s.readIds.has(x.id);
          const dim = x.level > s.settings.level;
          const r = rated.get(x.id);
          return html`<button class=${'story-row' + (read ? ' read' : '') + (dim ? ' dim' : '')} onClick=${() => onOpen(x.id)}>
            <div class="grow">
              <div class="title-m">${x.title}</div>
              <div class="body-s muted">${meta(x)}${r ? ` · ${ratingLabel(r).toLowerCase()}` : ''}</div>
            </div>
            ${read && html`<span class="good"><${Icon} name="check" /></span>`}
          </button>`;
        })}`)}
    </div>
  </div>`;
}

const speakable = (story) => (story.isDialog ? story.de.split('\n').filter((l) => l.trim()).map((l) => l.replace(/^[^:]{1,30}: /, '')).join('\n') : story.de);

function StoryScreen({ app, s, story, onBack, onOpen }) {
  const [showHr, setShowHr] = useState(false);
  const [picked, setPicked] = useState(null);
  const scroller = useRef(null);
  useEffect(() => app.ensureForms(), []);
  useEffect(() => {
    setShowHr(false);
    setPicked(null);
    if (scroller.current) scroller.current.scrollTop = 0;
  }, [story.id]);
  const strict = s.settings.strictReading;
  const read = s.readIds.has(story.id);
  const rating = (s.ratings.find((x) => x[0] === story.id) || [])[1];
  const next = derive(s).recommended;
  const sel = picked && picked.type === 'sentence' ? picked : null;

  return html`<div class="tab">
    <div class="scroll" ref=${scroller} style="padding:0 24px">
      <div class="row" style="padding-top:12px">
        <div class="label muted grow">Razina ${story.level} · ${meta(story)}</div>
        <button class=${'chip small' + (strict ? ' on' : '')} onClick=${() => app.updateSettings({ strictReading: !strict })}>Bez pomoći</button>
      </div>
      <h1 class="h1">${story.title}</h1>
      <div class="body-s muted">${strict ? 'Bez prijevoda: čitaj i pokušaj razumjeti iz konteksta.' : 'Dodir na riječ: prijevod · zadrži na rečenici: prijevod rečenice'}</div>
      <div class="sp16"></div>
      ${story.paragraphs.map((para, pi) => html`
        <${Paragraph} sentences=${para} dialog=${story.isDialog} strict=${strict}
          selected=${sel && sel.pi === pi ? sel.si : -1}
          onWord=${(w) => setPicked({ type: 'word', token: w })}
          onSentence=${(si) => setPicked({ type: 'sentence', pi, si, de: para[si][0], hr: para[si][1] })} />
        ${showHr && !strict && html`<div class="sp4"></div><div class="body-m muted italic">${para.map((x) => x[1]).join(' ')}</div>`}
        <div class=${story.isDialog ? 'sp10' : 'sp16'}></div>`)}
      ${story.newWords.length > 0 && !strict && html`
        <div class="sp4"></div>
        <${SectionTitle} text="Nove riječi u priči" />
        <div class="row" style="gap:8px;flex-wrap:wrap">
          ${story.newWords.slice(0, 5).map((w) => html`<${Pill} text=${w} bg="var(--s-high)" fg="var(--on)" onClick=${() => setPicked({ type: 'word', token: w })} />`)}
        </div>`}
      <div class="sp24"></div>
      <div class="card" style="padding:16px">
        <div class="title-s">${rating ? 'Hvala! Prema tome biram sljedeću.' : 'Kako ti je bilo?'}</div>
        <div class="body-s muted">Prema ocjeni se predlaže sljedeća priča.</div>
        <div class="sp10"></div>
        <div class="rate">
          ${RATINGS.map(([r, label]) => html`<button class=${rating === r ? 'on' : ''} onClick=${() => app.rateStory(story.id, r)}>${label}</button>`)}
        </div>
      </div>
      ${read && next && next.id !== story.id && html`<div class="sp10"></div>
        <button class="btn outline" onClick=${() => onOpen(next.id)}>Sljedeće: ${next.title}</button>`}
      <div class="sp24"></div>
    </div>
    <${ActionBar} cls="pinned-bar">
      <${ActionItem} icon="back" label="Natrag" onClick=${onBack} />
      <${ActionItem} icon="volume" label="Poslušaj" onClick=${() => app.speak(speakable(story))} />
      <${ActionItem} icon="slow" label="Sporije" onClick=${() => app.speakSlow(speakable(story))} />
      <${ActionItem} icon="translate" label=${showHr ? 'Sakrij' : 'Prijevod'} enabled=${!strict} onClick=${() => setShowHr(!showHr)} />
      <${ActionItem} icon="check" label=${read ? 'Pročitano' : 'Gotovo'} color=${read ? 'var(--good)' : undefined}
        onClick=${() => (read ? onBack() : app.markRead(story.id))} />
    <//>
    ${picked && html`<${Sheet} onClose=${() => setPicked(null)}>
      ${picked.type === 'sentence'
        ? html`<${SentenceInfo} app=${app} p=${picked} dialog=${story.isDialog} />`
        : html`<${WordInfo} app=${app} s=${s} story=${story} token=${picked.token} />`}
    <//>`}
  </div>`;
}

/** Words open on tap; a long press on a sentence shows its translation. */
function Paragraph({ sentences, dialog, strict, selected, onWord, onSentence }) {
  const press = useRef(null);
  const start = (si) => (e) => {
    if (strict) return;
    const p = { si, x: e.clientX, y: e.clientY, fired: false };
    p.timer = setTimeout(() => {
      p.fired = true;
      if (navigator.vibrate) navigator.vibrate(10);
      onSentence(si);
    }, 450);
    press.current = p;
  };
  const move = (e) => {
    const p = press.current;
    if (p && !p.fired && Math.hypot(e.clientX - p.x, e.clientY - p.y) > 10) {
      clearTimeout(p.timer);
      press.current = null;
    }
  };
  const end = () => {
    const p = press.current;
    if (p && !p.fired) clearTimeout(p.timer);
  };
  const click = (e) => {
    const p = press.current;
    press.current = null;
    if (p && p.fired) {
      e.stopPropagation();
      e.preventDefault();
    }
  };
  const re = /[\p{L}\p{N}][\p{L}\p{N}'’-]*/gu;
  return html`<p class=${'story-text' + (dialog ? ' dlg' : '') + (strict ? ' strict' : '')} onContextMenu=${(e) => e.preventDefault()}>
    ${sentences.map(([de], si) => {
      let text = de;
      let who = null;
      if (dialog && si === 0) {
        const m = /^[^:]{1,30}: /.exec(de);
        if (m) {
          who = m[0];
          text = de.slice(m[0].length);
        }
      }
      const parts = [];
      let i = 0;
      let m;
      re.lastIndex = 0;
      while ((m = re.exec(text))) {
        if (m.index > i) parts.push(text.slice(i, m.index));
        const w = m[0];
        parts.push(strict ? w : html`<span class="w" onClick=${() => onWord(w)}>${w}</span>`);
        i = m.index + w.length;
      }
      parts.push(text.slice(i));
      return html`${si > 0 ? ' ' : ''}${who && html`<span class="who">${who}</span>`}<span class=${'sent' + (selected === si ? ' sel' : '')}
        onPointerDown=${start(si)} onPointerMove=${move} onPointerUp=${end} onPointerCancel=${end} onClickCapture=${click}>${parts}</span>`;
    })}
  </p>`;
}

function SentenceInfo({ app, p, dialog }) {
  const de = dialog ? p.de.replace(/^[^:]{1,30}: /, '') : p.de;
  return html`<div>
    <div class="label muted">REČENICA</div>
    <div class="sp6"></div>
    <div class="row gap12">
      <div class="title-l grow">${p.de}</div>
      <${SpeakButton} size=${48} onClick=${() => app.speak(de)} />
    </div>
    <div class="sp12"></div>
    <div class="body-l primary italic">${p.hr || 'Prijevod rečenice nije dostupan.'}</div>
    <div class="sp8"></div>
    <button class="btn text" style="padding:0" onClick=${() => app.speakSlow(de)}>Polako</button>
  </div>`;
}

function WordInfo({ app, s, story, token }) {
  const clean = token.replace(/^[^\p{L}]+|[^\p{L}]+$/gu, '');
  if (!s.formsReady) {
    return html`<div class="row gap12"><div class="spinner" style="width:24px;height:24px;border-width:3px"></div><div class="h2">${clean}</div></div>`;
  }
  // Looked up while shown, so a tap before the forms index loaded still resolves.
  const word = app.lookup(token);
  if (!word) {
    const g = story.glosses.find((x) => x.forms.includes(clean.toLowerCase()));
    if (g) {
      return html`<div>
        <div class="row">
          <div class="grow">
            <div class="label muted">riječ iz priče · nije na popisu</div>
            <div class="h2">${g.de || clean}</div>
          </div>
          <${SpeakButton} size=${48} onClick=${() => app.speak(g.de || clean)} />
        </div>
        <div class="sp12"></div>
        <div class="title-l primary">${g.hr}</div>
      </div>`;
    }
    return html`<div>
      <div class="h2">${clean}</div>
      <div class="sp8"></div>
      <div class="body-m muted">Ova riječ nije na listi (ime, broj ili oblik koji ne prepoznajem). Zadrži prst na rečenici za njen prijevod.</div>
      <div class="sp12"></div>
      <button class="btn text" onClick=${() => app.speak(clean)}>Izgovori</button>
    </div>`;
  }
  const card = s.cards[word.rank];
  const b = bucketOf(card);
  const wordLevel = Math.ceil(word.rank / LEVEL_SIZE);
  return html`<div>
    <div class="row">
      <div class="grow">
        <div class="row gap8"><span class="label muted">#${word.rank}</span><${Dot} bucket=${b} /></div>
        <${Headword} word=${word} cls="m" />
      </div>
      <${SpeakButton} size=${48} onClick=${() => app.speak(word.spoken)} />
    </div>
    <div class="sp4"></div>
    <${GrammarLine} word=${word} />
    <div class="sp12"></div>
    <div class="title-l primary">${word.hr}</div>
    <div class="sp16"></div>
    <${ExampleBlock} word=${word} onSpeak=${(t) => app.speak(t)} />
    <div class="sp16"></div>
    ${b === 'UNTESTED' || b === 'KNOWN'
      ? html`<button class="btn" onClick=${() => app.addToLearning(word.rank)}>${b === 'KNOWN' ? 'Ipak je dodaj u učenje' : 'Dodaj u učenje'}</button>`
      : html`<div class="row gap8"><${Dot} bucket=${b} /><span class="body-m muted">${b === 'TO_LEARN' ? 'Dodana u učenje — dolazi među prvim novim riječima' : statusText(b, card)}</span></div>`}
    ${b === 'TO_LEARN' && wordLevel > s.settings.level && html`<div class="sp6"></div><div class="body-s muted">Riječ je s razine ${wordLevel}, pa stiže na red kad otključaš tu razinu.</div>`}
  </div>`;
}
