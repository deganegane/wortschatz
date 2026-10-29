import { html, useEffect } from '../vendor/htm-preact.js';
import {
  Icon, Headword, GrammarLine, ExampleBlock, Pill, ActionBar, ActionItem, TopBar, FeedbackBanner, MicRow,
  highlighted, useSwipe, SwipeHints,
} from '../ui.js';
import { derive } from '../app-state.js';
import { Grade, GRADE_LABELS, Mode, POS_LABELS, Verdict, plural } from '../model.js';
import * as Srs from '../srs.js';
import { suggested } from '../speechcheck.js';

export function ReviewScreen({ app, s }) {
  const session = s.session;
  const item = session.queue[0];
  const word = item ? app.word(item.rank) : null;
  const revealed = !!item && (session.revealed || item.isNew);
  const key = item ? `${item.rank}:${session.reviewed}` : 'end';
  const swipe = useSwipe({
    enabled: !!word && revealed,
    key,
    onDecide: (right) => app.answer(right ? Grade.GOOD : Grade.AGAIN, session.reviewed),
  });

  const speaking = !!item && item.mode !== Mode.RECOGNIZE && !item.isNew;
  const micOk = app.recognition && !s.settings.quiet && !s.micBlocked;
  const useMic = speaking && s.settings.speakAnswers && micOk;
  const canRepeat = !speaking && micOk;
  const autoRepeat = canRepeat && revealed && ((item && item.isNew && s.settings.repeatNew) || s.settings.repeatAll);

  useEffect(() => {
    if (!word) return;
    if (autoRepeat) {
      app.speakThenRepeat(word.spoken);
      return;
    }
    if (!s.settings.autoSpeak) return;
    if (!speaking) app.speak(word.spoken);
    else if (revealed && !session.check) app.speak(item.mode === Mode.CLOZE ? word.example : word.spoken);
  }, [key, revealed]);

  useEffect(() => {
    // Data changed under the session (a lowered level): skip the card.
    if (item && !word) app.answer(Grade.GOOD);
  }, [key]);

  const done = session.reviewed;
  const left = session.queue.length;
  const top = html`<${TopBar} onClose=${() => app.closeSession()} progress=${done + left === 0 ? 1 : done / (done + left)} count=${left} />`;
  if (!item) return html`<div class="screen full">${top}<${Summary} app=${app} s=${s} /></div>`;
  if (!word) return html`<div class="screen full">${top}</div>`;

  const showWord = item.mode === Mode.RECOGNIZE || item.isNew || revealed;
  let controls;
  if (revealed) {
    controls = html`
      ${canRepeat && html`<${RepeatRow} app=${app} s=${s} word=${word} /><div class="sp8"></div>`}
      <div class="label-s muted center" style="padding-bottom:6px">← Opet  ·  povuci bilo gdje  ·  Dobro →</div>
      <${GradeButtons} app=${app} preview=${Srs.preview(s.cards[item.rank], s.today, s.settings.retention)}
        sug=${session.check ? suggested(session.check) : null} />`;
  } else if (useMic) {
    controls = html`<${MicRow} listen=${s.listen}
      hint=${item.mode === Mode.CLOZE ? 'Izgovori riječ koja fali (ili cijelu rečenicu)' : 'Reci riječ na njemačkom'}
      onStart=${() => app.listen()} onStop=${() => app.finishListening()}
      left=${html`<button class="btn text" onClick=${() => app.reveal()}>Ne znam</button>`} />`;
  } else {
    controls = html`<button class="btn big" onClick=${() => app.reveal()}>Prikaži odgovor</button>`;
  }

  return html`<div class="screen full swipe-area" ...${swipe.area}>
    ${top}
    <div class="scroll" style="padding:12px 20px">
      <${CardFace} app=${app} item=${item} word=${word} revealed=${revealed} check=${session.check} swipe=${swipe} key=${key} />
    </div>
    <div class="bottom">
      <${ActionBar}>
        <${ActionItem} icon="close" label="Završi" onClick=${() => app.closeSession()} />
        <${ActionItem} icon="volume" label="Riječ" enabled=${showWord} onClick=${() => app.speak(word.spoken)} />
        <${ActionItem} icon="shadow" label="Rečenica" enabled=${!!word.example && (revealed || item.mode === Mode.RECOGNIZE)} onClick=${() => app.speak(word.example)} />
        ${item.isNew && html`<${ActionItem} icon="check" label="Već znam" onClick=${() => app.skipAsKnown()} />`}
      <//>
      <div class="sp4"></div>
      ${controls}
    </div>
  </div>`;
}

function CardFace({ app, item, word, revealed, check, swipe }) {
  const mode = item.isNew ? Mode.RECOGNIZE : item.mode;
  const speak = (t) => app.speak(t);
  let pill;
  if (item.isNew) pill = html`<${Pill} text="NOVA RIJEČ" bg="var(--primary)" fg="var(--on-primary)" />`;
  else if (mode === Mode.RECALL) pill = html`<${Pill} text="RECI NA NJEMAČKOM" bg="var(--sc)" fg="var(--on-sc)" />`;
  else if (mode === Mode.CLOZE) pill = html`<${Pill} text="DOPUNI REČENICU" bg="var(--tc)" fg="var(--on-tc)" />`;
  else pill = html`<${Pill} text=${'#' + word.rank} bg="var(--s-highest)" fg="var(--muted)" />`;

  let body;
  if (mode === Mode.RECOGNIZE) {
    body = html`
      <${Headword} word=${word} />
      <div class="sp8"></div>
      <${GrammarLine} word=${word} center />
      ${revealed
        ? html`<div class="reveal" style="width:100%">
            <div class="sp20"></div><hr class="divider" /><div class="sp20"></div>
            <div class="title-l primary">${word.hr}</div>
            <div class="sp20"></div>
            <${ExampleBlock} word=${word} onSpeak=${speak} />
          </div>`
        : html`<div class="sp24"></div><div class="label muted" style="opacity:0.7">sjeti se značenja, pa provjeri</div>`}`;
  } else if (mode === Mode.RECALL) {
    const sub = word.article ? 'imenica · reci je s članom' : word.cat ? `fraza · ${word.cat}` : POS_LABELS[word.pos] || '';
    body = html`
      <div class="h2">${word.hr}</div>
      <div class="sp8"></div>
      <div class="body-m muted">${sub}</div>
      ${revealed && html`<div class="reveal" style="width:100%">
        <div class="sp20"></div>
        ${check ? html`<${FeedbackBanner} check=${check} word=${word} /><div class="sp20"></div>` : html`<hr class="divider" /><div class="sp20"></div>`}
        <${Headword} word=${word} />
        <div class="sp8"></div>
        <${GrammarLine} word=${word} center />
        <div class="sp20"></div>
        <${ExampleBlock} word=${word} onSpeak=${speak} />
      </div>`}`;
  } else {
    body = html`
      <div class="h2" style="font-weight:500">${revealed ? highlighted(word, 'var(--primary)') : word.clozeText || word.example}</div>
      <div class="sp10"></div>
      <div class="body-m muted italic">${word.exampleHr}</div>
      ${!revealed && html`<div class="sp12"></div><${Pill} text=${'tražena riječ: ' + word.hrShort} bg="var(--s-highest)" fg="var(--muted)" />`}
      ${revealed && html`<div class="reveal" style="width:100%">
        <div class="sp20"></div>
        ${check && html`<${FeedbackBanner} check=${check} word=${word} cloze /><div class="sp16"></div>`}
        <hr class="divider" /><div class="sp16"></div>
        <${Headword} word=${word} cls="m" />
        <div class="sp6"></div>
        <${GrammarLine} word=${word} center />
        <div class="sp8"></div>
        <div class="title-m primary">${word.hr}</div>
      </div>`}`;
  }
  const tappable = !revealed && mode === Mode.RECOGNIZE;
  return html`<div class=${'study enter' + (tappable ? ' tap' : '')} ref=${swipe.card} onClick=${tappable ? () => app.reveal() : undefined}>
    <div class="row" style="width:100%">${pill}</div>
    <div class="sp24"></div>
    ${body}
    <${SwipeHints} left="OPET" right="DOBRO" />
  </div>`;
}

function GradeButtons({ app, preview, sug }) {
  const styles = [
    'background:color-mix(in srgb, var(--bad) 18%, transparent);color:var(--bad)',
    'background:var(--s-highest);color:var(--on)',
    'background:var(--primary);color:var(--on-primary)',
    'background:var(--sc);color:var(--on-sc)',
  ];
  return html`<div class="grades">
    ${[0, 1, 2, 3].map((g) => html`<button class=${'grade' + (sug === g ? ' sug' : '')} style=${styles[g]} onClick=${() => app.answer(g)}>
      <span class="title-s">${GRADE_LABELS[g]}</span>
      <span class="sub">${Srs.label(preview[g])}</span>
    </button>`)}
  </div>`;
}

function RepeatRow({ app, s, word }) {
  const listen = s.listen;
  const result = s.session.repeat;
  const on = listen.phase === 'LISTENING';
  let text = 'Ponovi riječ naglas';
  let color = 'var(--muted)';
  if (on) {
    text = listen.partial ? `„${listen.partial}“` : 'Ponovi naglas…';
    color = 'var(--primary)';
  } else if (result && result.verdict === Verdict.CORRECT) {
    text = 'Dobro izgovoreno!';
    color = 'var(--good)';
  } else if (result && result.verdict === Verdict.ALMOST) {
    text = word.article ? `Skoro — reci i član: ${word.full}` : 'Skoro — probaj još jednom';
    color = 'var(--primary)';
  } else if (result) {
    text = result.heard ? `Čuo sam „${result.heard}“ — probaj još jednom` : 'Nisam ništa čuo';
    color = 'var(--bad)';
  } else if (listen.phase === 'ERROR') {
    text = listen.error || 'Nešto je pošlo krivo.';
    color = 'var(--error)';
  }
  return html`<div class="repeat">
    <button class="speak" aria-label=${on ? 'Gotovo' : 'Ponovi naglas'} onClick=${() => (on ? app.finishListening() : app.listen())}>
      <${Icon} name=${on ? 'stop' : 'mic'} size=${22} />
    </button>
    <div class="body-m grow" style=${`color:${color}`}>${text}</div>
  </div>`;
}

function Summary({ app, s }) {
  const ses = s.session;
  const d = derive(s);
  return html`<div class="center-fill">
    ${ses.reviewed === 0
      ? html`<div class="h2">Nema ničega za ponavljanje</div>
        <div class="sp8"></div>
        <div class="body-l muted">Za danas je sve gotovo. Ako želiš, dodaj još novih riječi.</div>`
      : html`<div class="display primary">Gut gemacht!</div>
        <div class="sp8"></div>
        <div class="title-m">Gotovo za danas.</div>
        <div class="sp24"></div>
        <div class="row" style="gap:24px">
          <${Stat} n=${ses.reviewed} l=${plural(ses.reviewed, 'kartica', 'kartice', 'kartica')} />
          <${Stat} n=${ses.newSeen} l=${plural(ses.newSeen, 'nova riječ', 'nove riječi', 'novih riječi')} />
          <${Stat} n=${ses.again} l=${plural(ses.again, 'put', 'puta', 'puta') + ' „opet”'} />
        </div>
        <div class="sp12"></div>
        <div class="body-m muted">${d.streak} ${plural(d.streak, 'dan', 'dana', 'dana')} zaredom</div>`}
    <div class="sp32"></div>
    <button class="btn" onClick=${() => app.closeSession()}>Natrag</button>
    ${d.newAvailable > 0 && html`<div class="sp10"></div><button class="btn tonal" onClick=${() => app.startSession(5)}>Još 5 novih riječi</button>`}
  </div>`;
}

const Stat = ({ n, l }) => html`<div class="col" style="align-items:center"><div class="h2">${n}</div><div class="label muted">${l}</div></div>`;
