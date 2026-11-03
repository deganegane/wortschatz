// Shadowing and grammar rounds.
import { html, useEffect } from '../vendor/htm-preact.js';
import { ActionBar, ActionItem, TopBar, MicRow, Pill, matched, useSwipe, SwipeHints, SwipeLegend, EndScreen } from '../ui.js';
import { BLANK, findWord, plural } from '../model.js';

export function ShadowingScreen({ app, s }) {
  const sh = s.shadowing;
  const finished = sh.index >= sh.ranks.length;
  const word = finished ? null : app.word(sh.ranks[sh.index]);
  useEffect(() => {
    if (!word) return;
    const t = setTimeout(() => app.speak(word.example), 250);
    return () => clearTimeout(t);
  }, [sh.index]);
  // Right: next sentence. Left: hear it again and repeat.
  const swipe = useSwipe({
    enabled: !!word,
    key: `${sh.index}:${sh.attempts}`,
    onDecide: (right) => (right ? app.shadowNext() : app.shadowAgain()),
  });

  const top = html`<${TopBar} onClose=${() => app.closeShadowing()} progress=${sh.index / Math.max(1, sh.ranks.length)}
    count=${`${Math.min(sh.index + 1, sh.ranks.length)} / ${sh.ranks.length}`} />`;
  if (!word) {
    return html`<div class="screen full">${top}
      <${EndScreen} title="Sehr gut!" text=${`Ponovio si ${sh.done} ${plural(sh.done, 'rečenicu', 'rečenice', 'rečenica')} naglas.`}
        again="Još 10 rečenica" onAgain=${() => app.startShadowing()} onBack=${() => app.closeShadowing()} />
    </div>`;
  }
  const match = sh.match;
  const pct = match && match.length ? Math.floor((match.filter(Boolean).length * 100) / match.length) : 0;
  const micOk = app.recognition && !s.settings.quiet && !s.micBlocked;
  return html`<div class="screen full swipe-area" ...${swipe.area}>
    ${top}
    <div class="scroll" style="padding:12px 20px">
      <div class="body-m muted">Poslušaj, pa odmah ponovi naglas istim ritmom i melodijom. Ne razmišljaj o gramatici — oponašaj.</div>
      <div class="sp16"></div>
      <div class="study enter" key=${sh.index} ref=${swipe.card}>
        <div class="h2" style="font-weight:500">${match ? matched(word.example, match) : word.example}</div>
        <div class="sp10"></div>
        <div class="body-m muted italic">${word.exampleHr}</div>
        ${match && html`<div class="sp16"></div>
          <div class="title-s" style=${`color:${pct >= 60 ? 'var(--good)' : 'var(--bad)'}`}>
            ${pct >= 90 ? `Odlično — ${pct} % riječi` : pct >= 60 ? `Dobro — ${pct} % riječi. Crvene probaj još jednom.` : `${pct} % riječi. Poslušaj sporije pa ponovi.`}
          </div>
          ${sh.heard && html`<div class="body-s muted">Čuo sam: „${sh.heard}“</div>`}`}
        <${SwipeHints} left="PONOVI" right="DALJE" />
      </div>
    </div>
    <div class="bottom">
      <${ActionBar}>
        <${ActionItem} icon="close" label="Završi" onClick=${() => app.closeShadowing()} />
        <${ActionItem} icon="volume" label="Poslušaj" onClick=${() => app.speak(word.example)} />
        <${ActionItem} icon="slow" label="Sporije" onClick=${() => app.speakSlow(word.example)} />
      <//>
      <${SwipeLegend} text="← Ponovi  ·  povuci bilo gdje  ·  Dalje →" />
      ${micOk
        ? html`<${MicRow} listen=${s.listen} hint=${match ? 'Ponovi još jednom ili idi dalje' : 'Dodirni mikrofon i ponovi rečenicu'}
            onStart=${() => { app.shadowRetry(); app.listen(); }} onStop=${() => app.finishListening()}
            secondary=${!match && html`<button class="btn text" onClick=${() => app.shadowNext()}>Preskoči</button>`}
            primary=${match && html`<button class="btn" onClick=${() => app.shadowNext()}>Dalje</button>`} />`
        : html`<div class="body-s muted">${s.settings.quiet
            ? 'Tihi način: ponovi rečenicu šapatom ili u sebi, pa idi dalje.'
            : s.micBlocked && s.listen.error ? s.listen.error
            : 'Prepoznavanje govora nije dostupno, pa ponavljaj sam i idi dalje kad zvuči kao original.'}</div>
          <div class="sp8"></div>
          <button class="btn big" onClick=${() => app.shadowNext()}>Dalje</button>`}
    </div>
  </div>`;
}

export function GrammarScreen({ app, s }) {
  const g = s.grammar;
  const finished = g.index >= g.ranks.length;
  const word = finished ? null : app.word(g.ranks[g.index]);
  const gap = word && word.gap;
  const answered = g.chosen != null;
  // Once answered: swipe right anywhere for the next sentence.
  const swipe = useSwipe({ enabled: !!gap && answered, key: g.index, allow: (right) => right, onDecide: () => app.grammarNext() });
  const top = html`<${TopBar} onClose=${() => app.closeGrammar()} progress=${g.index / Math.max(1, g.ranks.length)}
    count=${`${Math.min(g.index + 1, g.ranks.length)} / ${g.ranks.length}`} />`;
  if (!gap) {
    const total = g.correct + g.wrong;
    const title = total === 0 ? 'Nema rečenica' : g.correct === total ? 'Fehlerfrei!' : g.correct >= total * 0.7 ? 'Gut gemacht!' : 'Weiter so!';
    return html`<div class="screen full">${top}
      <${EndScreen} title=${title} text=${`${g.correct} od ${total} točno`} again="Još 10 rečenica"
        onAgain=${() => app.startGrammar()} onBack=${() => app.closeGrammar()} />
    </div>`;
  }
  const correct = g.chosen === gap.blank;
  const i = findWord(word.example, gap.blank);
  const color = correct ? 'var(--good)' : 'var(--primary)';
  const sentence = i < 0
    ? word.example
    : html`${word.example.slice(0, i)}<span class="mark" style=${`color:${color}`}>${answered ? gap.blank : BLANK}</span>${word.example.slice(i + gap.blank.length)}`;
  return html`<div class="screen full swipe-area" ...${swipe.area}>
    ${top}
    <div class="scroll" style="padding:12px 20px">
      <div class="body-m muted">Koji oblik ide u prazninu?</div>
      <div class="sp16"></div>
      <div class="study enter" key=${g.index} ref=${swipe.card}>
        <div class="h2" style="font-weight:500">${sentence}</div>
        <div class="sp10"></div>
        <div class="body-m muted italic">${word.exampleHr}</div>
        <div class="sp12"></div>
        <${Pill} text=${gap.hint} bg="var(--s-highest)" fg="var(--muted)" />
        ${answered && html`<div class="sp16"></div>
          <div class="feedback center reveal" style=${`background:color-mix(in srgb, ${correct ? 'var(--good)' : 'var(--bad)'} 14%, transparent);color:${correct ? 'var(--good)' : 'var(--bad)'}`}>
            <div class="title-m">${correct ? 'Točno!' : `Nije — ide „${gap.blank}“`}</div>
            ${gap.explanation && html`<div class="sp4"></div><div class="body-s" style="color:var(--on)">${gap.explanation}</div>`}
          </div>`}
        <div class="hint r">DALJE</div>
      </div>
    </div>
    <div class="bottom">
      <${ActionBar}>
        <${ActionItem} icon="close" label="Završi" onClick=${() => app.closeGrammar()} />
        <${ActionItem} icon="volume" label="Poslušaj" enabled=${answered} onClick=${() => app.speak(word.example)} />
      <//>
      <div class="sp4"></div>
      <div class="opts">
        ${gap.options.map((opt) => {
          let cls = '';
          if (answered) cls = opt === gap.blank ? 'right' : opt === g.chosen ? 'wrong' : 'rest';
          return html`<button class=${'opt ' + cls} disabled=${answered} onClick=${() => app.grammarChoose(opt)}>${opt}</button>`;
        })}
      </div>
      ${answered && html`<${SwipeLegend} text="povuci bilo gdje  ·  Dalje →" />
        <button class="btn big" onClick=${() => app.grammarNext()}>${g.index + 1 >= g.ranks.length ? 'Rezultat' : 'Dalje'}</button>`}
    </div>
  </div>`;
}
