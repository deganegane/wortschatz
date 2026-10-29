import { html, useEffect, useState } from '../vendor/htm-preact.js';
import { Icon, Headword, GrammarLine, ExampleBlock, SpeakButton, SectionTitle, Pill, Dot, ActionBar, ActionItem, Sheet } from '../ui.js';
import { derive } from '../app-state.js';
import { bucketOf } from '../model.js';

export function ReadingTab({ app, s, openStory, onOpen }) {
  const story = openStory ? s.stories.find((x) => x.id === openStory) : null;
  if (story) return html`<${StoryScreen} app=${app} s=${s} story=${story} onBack=${() => onOpen(null)} />`;
  const d = derive(s);
  const levels = [...new Set(s.stories.map((x) => x.level))].sort((a, b) => a - b);
  return html`<div class="tab">
    <div class="scroll" style="padding-bottom:24px">
      <div class="pad20" style="padding-top:12px">
        <h1 class="h1">Čitanje</h1>
        <div class="body-m muted">Kratke priče složene od riječi koje učiš. Dodirni riječ za prijevod; pročitano: ${d.storiesRead} od ${s.stories.length}.</div>
        <div class="sp8"></div>
      </div>
      ${!s.stories.length && html`<div class="pad20 muted">Nema priča.</div>`}
      ${levels.map((level) => html`
        <div class="row pad20" style="padding-top:10px;padding-bottom:10px">
          <div class="grow"><${SectionTitle} text=${`Razina ${level} · ${level * 1000} riječi`} style="margin:0" /></div>
          ${level > s.settings.level && html`<span class="label muted">još zaključano</span>`}
        </div>
        ${s.stories.filter((x) => x.level === level).map((x) => {
          const read = s.readIds.has(x.id);
          const dim = x.level > s.settings.level;
          return html`<button class=${'story-row' + (read ? ' read' : '') + (dim ? ' dim' : '')} onClick=${() => onOpen(x.id)}>
            <div class="grow">
              <div class="title-m">${x.title}</div>
              <div class="body-s muted">${x.wordCount} riječi${x.newWords.length ? ` · ${x.newWords.length} nove` : ''}</div>
            </div>
            ${read && html`<span class="good"><${Icon} name="check" /></span>`}
          </button>`;
        })}`)}
    </div>
  </div>`;
}

function StoryScreen({ app, s, story, onBack }) {
  const [showHr, setShowHr] = useState(false);
  const [picked, setPicked] = useState(null);
  useEffect(() => app.ensureForms(), []);
  const read = s.readIds.has(story.id);
  const de = story.de.split('\n').filter((p) => p.trim());
  const hr = story.hr.split('\n').filter((p) => p.trim());
  // Looked up when shown, so a tap before the forms index has loaded still resolves.
  const pick = (token) => setPicked(token);

  return html`<div class="tab">
    <div class="scroll" style="padding:0 24px">
      <div class="label muted" style="padding-top:16px">Razina ${story.level} · ${story.wordCount} riječi</div>
      <h1 class="h1">${story.title}</h1>
      <div class="sp16"></div>
      ${de.map((p, i) => html`
        <${Paragraph} text=${p} onWord=${pick} />
        ${showHr && html`<div class="sp4"></div><div class="body-m muted italic">${hr[i] || ''}</div>`}
        <div class="sp16"></div>`)}
      ${story.newWords.length > 0 && html`
        <div class="sp4"></div>
        <${SectionTitle} text="Nove riječi u priči" />
        <div class="row" style="gap:8px;flex-wrap:wrap">
          ${story.newWords.slice(0, 5).map((w) => html`<${Pill} text=${w} bg="var(--s-high)" fg="var(--on)" onClick=${() => pick(w)} />`)}
        </div>`}
      <div class="sp24"></div>
    </div>
    <${ActionBar} cls="pinned-bar">
      <${ActionItem} icon="back" label="Natrag" onClick=${onBack} />
      <${ActionItem} icon="volume" label="Poslušaj" onClick=${() => app.speak(story.de)} />
      <${ActionItem} icon="slow" label="Sporije" onClick=${() => app.speakSlow(story.de)} />
      <${ActionItem} icon="translate" label=${showHr ? 'Sakrij' : 'Prijevod'} onClick=${() => setShowHr(!showHr)} />
      <${ActionItem} icon="check" label=${read ? 'Pročitano' : 'Gotovo'} color=${read ? 'var(--good)' : undefined}
        onClick=${() => (read ? onBack() : app.markRead(story.id))} />
    <//>
    ${picked && html`<${Sheet} onClose=${() => setPicked(null)}>
      <${WordInfo} app=${app} s=${s} token=${picked} word=${s.formsReady ? app.lookup(picked) : null} loading=${!s.formsReady} />
    <//>`}
  </div>`;
}

function Paragraph({ text, onWord }) {
  const parts = [];
  const re = /[\p{L}\p{N}][\p{L}\p{N}'’-]*/gu;
  let i = 0;
  let m;
  while ((m = re.exec(text))) {
    if (m.index > i) parts.push(text.slice(i, m.index));
    const w = m[0];
    parts.push(html`<span class="w" onClick=${() => onWord(w)}>${w}</span>`);
    i = m.index + w.length;
  }
  parts.push(text.slice(i));
  return html`<p class="story-text">${parts}</p>`;
}

function WordInfo({ app, s, token, word, loading }) {
  const clean = token.replace(/^[^\p{L}]+|[^\p{L}]+$/gu, '');
  if (loading) return html`<div class="row gap12"><div class="spinner" style="width:24px;height:24px;border-width:3px"></div><div class="h2">${clean}</div></div>`;
  if (!word) {
    return html`<div>
      <div class="h2">${clean}</div>
      <div class="sp8"></div>
      <div class="body-m muted">Ova riječ nije na listi (ime, broj ili oblik koji ne prepoznajem). Uključi prijevod odlomka.</div>
      <div class="sp12"></div>
      <button class="btn text" onClick=${() => app.speak(clean)}>Izgovori</button>
    </div>`;
  }
  return html`<div>
    <div class="row">
      <div class="grow">
        <div class="row gap8"><span class="label muted">#${word.isPhrase ? 'fraza' : word.rank}</span><${Dot} bucket=${bucketOf(s.cards[word.rank])} /></div>
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
  </div>`;
}
