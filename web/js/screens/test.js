import { html, useEffect, useState } from '../vendor/htm-preact.js';
import { Headword, GrammarLine, ExampleBlock, Pill, ActionBar, ActionItem, Progress, useSwipe, SwipeHints } from '../ui.js';
import { derive } from '../app-state.js';

export function TestTab({ app, s, onStartLearning }) {
  const d = derive(s);
  const total = d.vocab.length;
  const rank = d.nextTestRank;
  const verify = s.testVerify && s.testVerify.rank === rank ? s.testVerify : null;
  const word = rank != null ? app.word(rank) : null;
  // Remembered per word, so the next word never flashes up with its translation open.
  const [revealedRank, setRevealedRank] = useState(null);
  const revealed = revealedRank === rank;
  const setRevealed = (on) => setRevealedRank(on ? rank : null);
  useEffect(() => {
    if (word && s.settings.autoSpeak) app.speak(word.spoken);
  }, [rank]);

  const swipe = useSwipe({ enabled: rank != null && !verify, key: rank, onDecide: (right) => rank != null && app.testAnswer(rank, right) });

  let bottom;
  if (!word) {
    bottom = html`
      ${s.testUndo.length > 0 && html`<${ActionBar}><${ActionItem} icon="undo" label="Poništi" onClick=${() => app.testUndo()} /><//><div class="sp6"></div>`}
      <button class="btn" onClick=${onStartLearning}>Kreni učiti</button>`;
  } else if (verify) {
    const answered = verify.chosen != null;
    bottom = html`
      ${verify.options.map((opt, i) => {
        let cls = '';
        if (answered) cls = i === verify.correct ? 'right' : i === verify.chosen ? 'wrong' : 'rest';
        return html`<button class=${'vopt ' + cls} disabled=${answered} onClick=${() => app.verifyChoose(i)}>${opt}</button>`;
      })}
      ${answered && html`<div class="sp6"></div><button class="btn" onClick=${() => app.verifyContinue()}>Dalje</button>`}`;
  } else {
    bottom = html`
      <${ActionBar}>
        <${ActionItem} icon="undo" label="Poništi" enabled=${s.testUndo.length > 0} onClick=${() => app.testUndo()} />
        <${ActionItem} icon="translate" label=${revealed ? 'Sakrij' : 'Prijevod'} onClick=${() => setRevealed(!revealed)} />
        <${ActionItem} icon="volume" label="Izgovor" onClick=${() => app.speak(word.spoken)} />
      <//>
      <div class="sp6"></div>
      <div class="yesno">
        <button class="btn outline" style="color:var(--bad);border-color:color-mix(in srgb, var(--bad) 50%, transparent)" onClick=${() => app.testAnswer(rank, false)}>← Ne znam</button>
        <button class="btn outline" style="color:var(--good);border-color:color-mix(in srgb, var(--good) 50%, transparent)" onClick=${() => app.testAnswer(rank, true)}>Znam →</button>
      </div>`;
  }

  return html`<div class="tab swipe-area" ...${swipe.area}>
    <div class="scroll pad20">
      <h1 class="h1" style="padding-top:12px">Provjera razine</h1>
      <div class="body-m muted">Znaš ${d.counts.KNOWN}  ·  ne znaš ${d.counts.TO_LEARN}</div>
      <div class="sp12"></div>
      <${Progress} value=${d.tested / Math.max(1, total)} />
      <div class="label muted" style="padding-top:6px">${d.tested} / ${total}</div>
      ${!word
        ? html`<div class="sp32"></div>
          <div class="h2">Prošao si cijelu listu!</div>
          <div class="sp8"></div>
          <div class="body-l muted">Od ${total} najčešćih riječi znaš ${d.counts.KNOWN}. Ostale (${d.counts.TO_LEARN + d.counts.LEARNING + d.counts.MASTERED}) uče se kroz dnevno ponavljanje, redom od najčešćih.</div>`
        : html`<div class="sp20"></div>
          ${d.tested === 0 && html`<div class="body-m muted">Znaš li ovu riječ? Budi iskren: "znam" znači da je razumiješ bez razmišljanja. Povuci bilo gdje na ekranu udesno (znam) ili ulijevo (ne znam).</div><div class="sp16"></div>`}
          ${verify
            ? html`<${VerifyCard} word=${word} v=${verify} />`
            : html`<div class="study tap enter" key=${rank} ref=${swipe.card} onClick=${() => setRevealed(!revealed)}>
                <${Pill} text=${'#' + word.rank} bg="var(--s-highest)" fg="var(--muted)" />
                <div class="sp24"></div>
                <${Headword} word=${word} />
                <div class="sp8"></div>
                <${GrammarLine} word=${word} center />
                ${revealed && html`<div class="reveal" style="width:100%">
                  <div class="sp24"></div>
                  <div class="title-l primary">${word.hr}</div>
                  <div class="sp16"></div>
                  <${ExampleBlock} word=${word} onSpeak=${(t) => app.speak(t)} />
                </div>`}
                <${SwipeHints} left="NE ZNAM" right="ZNAM" />
              </div>`}
          <div class="sp16"></div>`}
    </div>
    <div class="bottom">${bottom}</div>
  </div>`;
}

function VerifyCard({ word, v }) {
  const answered = v.chosen != null;
  const passed = v.chosen === v.correct;
  return html`<div class="study">
    <${Pill} text="PROVJERA" bg="var(--tc)" fg="var(--on-tc)" />
    <div class="sp16"></div>
    <${Headword} word=${word} />
    <div class="sp8"></div>
    <div class="body-m muted">Rekao si da znaš. Što znači?</div>
    ${answered && html`<div class="sp12"></div>
      <div class="title-m" style=${`color:${passed ? 'var(--good)' : 'var(--bad)'}`}>${passed ? 'Točno — stvarno je znaš.' : 'Ipak ne — riječ ide u učenje.'}</div>
      ${!passed && html`<div class="body-m muted">${word.hr}</div>`}`}
  </div>`;
}
