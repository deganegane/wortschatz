import { html, useEffect, useMemo, useRef, useState } from '../vendor/htm-preact.js';
import { Icon, Headword, GrammarLine, ExampleBlock, SpeakButton, Dot, Sheet } from '../ui.js';
import { derive } from '../app-state.js';
import { BUCKETS, Status, bucketOf, fold, today, shortDate } from '../model.js';

const PAGE = 150;

export function DictionaryTab({ app, s }) {
  const d = derive(s);
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('ALL');
  const [phrasesOnly, setPhrasesOnly] = useState(false);
  const [openRank, setOpenRank] = useState(null);
  const [limit, setLimit] = useState(PAGE);
  const listRef = useRef(null);
  const sentinel = useRef(null);
  const needle = fold(query.trim());

  const list = useMemo(
    () => s.words.filter((w) => (phrasesOnly ? w.isPhrase : !w.isPhrase) &&
      (filter === 'ALL' || bucketOf(s.cards[w.rank]) === filter) &&
      (!needle || w.searchKey.includes(needle))),
    [s.words, s.cards, needle, filter, phrasesOnly],
  );
  useEffect(() => {
    setLimit(PAGE);
    if (listRef.current) listRef.current.scrollTop = 0;
  }, [needle, filter, phrasesOnly]);
  useEffect(() => {
    const el = sentinel.current;
    if (!el || !window.IntersectionObserver) return;
    const io = new IntersectionObserver((e) => e[0].isIntersecting && setLimit((l) => l + PAGE), { root: listRef.current, rootMargin: '600px' });
    io.observe(el);
    return () => io.disconnect();
  }, [list, limit]);

  const word = openRank != null ? app.word(openRank) : null;
  return html`<div class="tab">
    <h1 class="h1 pad20" style="padding-top:12px;padding-bottom:4px">Rječnik</h1>
    ${!list.length && html`<div class="body-l muted" style="padding:20px">Nema rezultata.</div>`}
    <div class="scroll" ref=${listRef}>
      ${list.slice(0, limit).map((w) => html`
        <button class="wordrow" key=${w.rank} onClick=${() => { if (document.activeElement) document.activeElement.blur(); setOpenRank(w.rank); }}>
          <span class="label muted rank">${w.isPhrase ? '' : w.rank}</span>
          <div class="grow">
            <div class="title-m"><${HeadwordInline} word=${w} /></div>
            <div class="body-m muted hr">${w.hr}</div>
          </div>
          <span style="width:12px"></span>
          <${Dot} bucket=${bucketOf(s.cards[w.rank])} />
        </button>
        <div class="sep"></div>`)}
      ${limit < list.length && html`<div ref=${sentinel} style="height:40px"></div>`}
    </div>
    <div class="dict-bottom">
      <div class="chips">
        ${d.phrases.length > 0 && html`<button class=${'chip' + (phrasesOnly ? ' on' : '')} onClick=${() => setPhrasesOnly(!phrasesOnly)}>Fraze ${d.phrases.length}</button>`}
        ${BUCKETS.map(([b, label]) => html`<button class=${'chip' + (filter === b ? ' on' : '')} onClick=${() => setFilter(b)}>
          ${b !== 'ALL' && html`<${Dot} bucket=${b} />`}${phrasesOnly ? label : `${label} ${d.counts[b]}`}
        </button>`)}
      </div>
      <label class="searchbox">
        <${Icon} name="search" />
        <input type="search" enterkeyhint="search" autocomplete="off" autocorrect="off" spellcheck=${false}
          placeholder="Traži njemački ili hrvatski" value=${query} onInput=${(e) => setQuery(e.currentTarget.value)}
          onKeyDown=${(e) => e.key === 'Enter' && e.currentTarget.blur()} />
        ${query && html`<button class="icon-btn" aria-label="Obriši" onClick=${(e) => { e.preventDefault(); setQuery(''); }}><${Icon} name="clear" size=${20} /></button>`}
      </label>
    </div>
    ${word && html`<${Sheet} onClose=${() => setOpenRank(null)}><${WordSheet} app=${app} s=${s} word=${word} /><//>`}
  </div>`;
}

const HeadwordInline = ({ word }) =>
  html`${word.article && html`<span style=${`color:var(--${word.article});font-weight:500`}>${word.article} </span>`}${word.de}`;

function WordSheet({ app, s, word }) {
  const card = s.cards[word.rank];
  const b = bucketOf(card);
  const set = (st) => app.setStatus(word.rank, st);
  let buttons;
  if (b === 'UNTESTED') {
    buttons = html`<button class="btn outline auto" onClick=${() => set(Status.KNOWN)}>Znam je</button>
      <button class="btn auto" onClick=${() => set(Status.TO_LEARN)}>Želim je učiti</button>`;
  } else if (b === 'KNOWN') {
    buttons = html`<button class="btn auto" onClick=${() => set(Status.TO_LEARN)}>Ipak je želim učiti</button>`;
  } else if (b === 'TO_LEARN') {
    buttons = html`<button class="btn outline auto" onClick=${() => set(Status.KNOWN)}>Ipak je znam</button>`;
  } else {
    buttons = html`<button class="btn outline auto" onClick=${() => set(Status.KNOWN)}>Znam je, makni iz učenja</button>`;
  }
  return html`<div>
    <div class="row">
      <div class="grow">
        <div class="label muted">${word.isPhrase ? 'fraza' : '#' + word.rank}</div>
        <${Headword} word=${word} />
      </div>
      <${SpeakButton} size=${52} onClick=${() => app.speak(word.spoken)} />
    </div>
    <div class="sp6"></div>
    <${GrammarLine} word=${word} />
    <div class="sp16"></div>
    <div class="title-l primary">${word.hr}</div>
    <div class="sp16"></div>
    <${ExampleBlock} word=${word} onSpeak=${(t) => app.speak(t)} />
    <div class="sp20"></div>
    <div class="row gap8"><${Dot} bucket=${b} /><span class="body-m muted">${statusText(b, card)}</span></div>
    <div class="sp12"></div>
    <div class="row" style="gap:8px;flex-wrap:wrap">${buttons}</div>
    ${b !== 'UNTESTED' && html`<div class="sp4"></div><button class="btn text" style="padding:0" onClick=${() => set(Status.UNTESTED)}>Vrati u test</button>`}
  </div>`;
}

function statusText(b, card) {
  if (b === 'UNTESTED') return 'Još nije prošla test';
  if (b === 'KNOWN') return 'Označena kao poznata';
  if (b === 'TO_LEARN') return 'Čeka na učenje';
  const label = b === 'MASTERED' ? 'Naučena' : 'Učiš je';
  if (!card) return label;
  const diff = card.due - today();
  const when = diff <= 0 ? 'danas' : diff === 1 ? 'sutra' : diff <= 6 ? `za ${diff} dana` : shortDate(card.due);
  return `${label} · sljedeće ponavljanje ${when}`;
}
