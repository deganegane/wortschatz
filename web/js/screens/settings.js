import { html, useRef, useState } from '../vendor/htm-preact.js';
import { Icon, Toggle, Segmented, Dialog, Sheet, Progress } from '../ui.js';
import { derive } from '../app-state.js';
import { PRACTICE, THEMES, UNLOCK_SHARE } from '../model.js';
import { VERSION } from '../version.js';

const PAGES = {
  LEVEL: 'Razina',
  LEARNING: 'Učenje',
  SPEAKING: 'Govor i mikrofon',
  VOICE: 'Izgovor',
  BACKUP: 'Sigurnosna kopija',
  LOOK: 'Izgled',
};
const isIos = /iP(hone|ad|od)/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
const SAMPLE = 'Guten Tag! Wie geht es dir?';

export function SettingsScreen({ app, s, page, setPage, onBack }) {
  const st = s.settings;
  const d = derive(s);
  const [confirmReset, setConfirmReset] = useState(false);
  const [voicePicker, setVoicePicker] = useState(false);
  const set = (patch) => app.updateSettings(patch);
  const current = app.speech.current;
  const label = (o) => PRACTICE.find((p) => p[0] === o)[1];

  let body;
  if (!page) {
    const rows = [
      ['LEVEL', 'var(--secondary)', `${st.level * 1000} riječi` + (st.level < d.maxLevel ? ` · ${d.solid} / ${d.unlockTarget} do sljedeće` : '')],
      ['LEARNING', 'var(--primary)', `${st.newPerDay} novih dnevno · ${label(st.practice)} · pamćenje ${Math.round(st.retention * 100)} %`],
      ['SPEAKING', 'var(--der)', st.quiet ? 'Tihi način uključen' : st.speakAnswers ? 'Odgovori glasom' + (st.repeatNew ? ' · ponovi nove' : '') : 'Bez mikrofona'],
      ['VOICE', 'var(--das)', (current ? current.label.split(' · ')[0] : 'Zadani glas') + (st.autoSpeak ? ' · automatski' : '')],
      ['BACKUP', 'var(--die)', 'Spremi ili vrati napredak'],
      ['LOOK', 'var(--muted)', `Tema: ${THEMES.find((t) => t[0] === st.themeMode)[1].toLowerCase()} · paleta Jadran`],
    ];
    body = html`
      <div class="sp8"></div>
      <div class="panel">
        ${rows.map(([p, color, sum], i) => html`${i > 0 && html`<hr class="divider" />`}<${NavRow} title=${PAGES[p]} sum=${sum} color=${color} onClick=${() => setPage(p)} />`)}
      </div>
      <div class="sp16"></div>
      <div class="panel"><button class="navrow" onClick=${() => setConfirmReset(true)}><span class="title-s err">Resetiraj napredak</span></button></div>
      <div class="body-s muted" style="padding:20px 8px">Wortschatz ${VERSION} (web) · ${s.allCount} riječi i ${d.phrases.length} fraza · font Space Grotesk (SIL OFL)</div>`;
  } else if (page === 'LEVEL') {
    body = html`<div class="panel padded"><${LevelSection} app=${app} s=${s} /></div>`;
  } else if (page === 'LEARNING') {
    body = html`
      <div class="panel padded">
        <${Info} title="Novih riječi dnevno" info="10 je dobra mjera uz par minuta ponavljanja dnevno." />
        <${Segmented} options=${[5, 10, 15, 20]} value=${st.newPerDay} label=${String} onSelect=${(n) => set({ newPerDay: n })} />
        <div class="sp16"></div>
        <${Info} title="Način vježbe" info="Klasično: vidiš njemačku riječ i sjetiš se značenja. Govor: izgovaraš njemačku riječ ili dopunjavaš rečenicu. Mješovito: nove riječi prvo prepoznaješ, a zatim ih sve češće izgovaraš (preporučeno)." />
        <${Segmented} options=${PRACTICE.map((p) => p[0])} value=${st.practice} label=${label} onSelect=${(p) => set({ practice: p })} />
        <div class="sp16"></div>
        <${Info} title="Ciljano pamćenje" info="Koliko je vjerojatno da se sjetiš riječi kad dođe na red (FSRS). Više znači češća ponavljanja." />
        <${Segmented} options=${[0.85, 0.9, 0.95]} value=${st.retention} label=${(r) => `${Math.round(r * 100)} %`} onSelect=${(r) => set({ retention: r })} />
      </div>
      <div class="sp12"></div>
      <div class="panel padded">
        <${Toggle} title="Fraze za razgovor" sub="Svaka treća nova kartica" checked=${st.phrasesOn} onChange=${(v) => set({ phrasesOn: v })} />
        <${Toggle} title="Provjera u testu razine" sub='Svaki deseti "znam"' checked=${st.verifyKnown} onChange=${(v) => set({ verifyKnown: v })} />
      </div>`;
  } else if (page === 'SPEAKING') {
    body = html`
      <div class="panel padded">
        <${Toggle} title="Odgovaraj glasom" sub=${app.recognition ? 'Mikrofon provjerava riječ i član' : 'Ovaj preglednik ne prepoznaje govor'} checked=${st.speakAnswers} onChange=${(v) => set({ speakAnswers: v })} />
        <${Toggle} title="Ponovi nove riječi naglas" sub="Mikrofon se sam uključi" checked=${st.repeatNew} onChange=${(v) => set({ repeatNew: v })} />
        <${Toggle} title="Uvijek ponovi naglas" sub="Na svakoj kartici prepoznavanja" checked=${st.repeatAll} onChange=${(v) => set({ repeatAll: v })} />
      </div>
      <div class="sp12"></div>
      <div class="panel padded">
        <${Toggle} title="Tihi način" sub="Bez mikrofona, za javna mjesta" checked=${st.quiet} onChange=${(v) => set({ quiet: v })} />
      </div>
      ${isIos && html`<div class="body-s muted" style="padding:16px 8px">Na iPhoneu prepoznavanje govora radi preko Siri diktiranja i treba internet. Ako mikrofon ne reagira, provjeri Postavke → Općenito → Tipkovnica → Diktiranje i dopuštenje za mikrofon u Safariju.</div>`}`;
  } else if (page === 'VOICE') {
    body = html`
      <div class="panel padded">
        <${Toggle} title="Automatski izgovor" sub="Kad se kartica pokaže" checked=${st.autoSpeak} onChange=${(v) => set({ autoSpeak: v })} />
        <${Toggle} title="Sporiji izgovor" checked=${st.slowSpeech} onChange=${(v) => set({ slowSpeech: v })} />
      </div>
      <div class="sp12"></div>
      <div class="panel">
        ${s.voices.length > 1 && html`<${NavRow} title="Glas" sum=${current ? current.label : 'Zadani'} color="var(--das)" onClick=${() => setVoicePicker(true)} /><hr class="divider" />`}
        <${NavRow} title="Probaj glas" sum=${`„${SAMPLE}“`} color="var(--primary)" onClick=${() => app.speak(SAMPLE)} />
      </div>
      <div class="body-s muted" style="padding:16px 8px">${s.voice === 'NO_GERMAN' || s.voice === 'NO_ENGINE' ? 'Nije pronađen njemački glas. ' : ''}${isIos
        ? 'Ljepši glasovi: Postavke → Pristupačnost → Izgovoreni sadržaj → Glasovi → Njemački, pa preuzmi „poboljšani“ ili „vrhunski“ glas (npr. Anna ili Helena). Nakon toga ga odaberi ovdje.'
        : 'Glasove nudi preglednik ili sustav; više ih možeš dodati u postavkama govora na uređaju.'}</div>`;
  } else if (page === 'BACKUP') {
    body = html`<${Backup} app=${app} s=${s} />`;
  } else if (page === 'LOOK') {
    body = html`<div class="panel padded">
      <${Info} title="Tema" />
      <${Segmented} options=${THEMES.map((t) => t[0])} value=${st.themeMode} label=${(m) => THEMES.find((t) => t[0] === m)[1]} onSelect=${(m) => set({ themeMode: m })} />
    </div>`;
  }

  return html`<div class="screen full">
    <div class="topbar">
      <button class="icon-btn" aria-label="Natrag" onClick=${() => (page ? setPage(null) : onBack())}><${Icon} name="arrowBack" /></button>
      <div class="title-l" style="padding-left:4px">${page ? PAGES[page] : 'Postavke'}</div>
    </div>
    <div class="scroll pad" key=${page || 'main'} style="padding-bottom:calc(24px + env(safe-area-inset-bottom))">${body}</div>
    ${confirmReset && html`<${Dialog} title="Resetirati napredak?" danger confirm="Resetiraj"
      text='Ovo briše sve oznake "znam / ne znam", raspored ponavljanja i niz dana. Postavke ostaju. Ne može se poništiti.'
      onClose=${() => setConfirmReset(false)} onConfirm=${() => { app.resetProgress(); setConfirmReset(false); }} />`}
    ${voicePicker && html`<${Sheet} onClose=${() => setVoicePicker(false)}>
      <div class="title-l">Glas</div>
      <div class="body-s muted">Dodirni za probu. Online glasovi trebaju internet.</div>
      <div class="sp8"></div>
      ${s.voices.map((v) => html`<button class="toggle-row" style="padding:10px 0" onClick=${() => { set({ voiceName: v.name }); app.speak(SAMPLE); }}>
        <span class=${'radio' + (current && current.name === v.name ? ' on' : '')}></span>
        <span class="body-m grow" style="padding-left:12px">${v.label}</span>
      </button>`)}
    <//>`}
  </div>`;
}

function NavRow({ title, sum, color, onClick }) {
  return html`<button class="navrow" onClick=${onClick}>
    <span class="bub" style=${`background:color-mix(in srgb, ${color} 18%, transparent)`}><span class="dot" style=${`background:${color}`}></span></span>
    <div class="grow">
      <div class="title-s">${title}</div>
      <div class="body-s muted sum">${sum}</div>
    </div>
    <span class="muted"><${Icon} name="chevRight" /></span>
  </button>`;
}

function Info({ title, info }) {
  const [open, setOpen] = useState(false);
  return html`<div>
    <div class="row">
      <div class="title-s grow">${title}</div>
      ${info && html`<button class="icon-btn" style="width:32px;height:32px" aria-label="Objašnjenje" onClick=${() => setOpen(!open)}><${Icon} name="info" size=${18} /></button>`}
    </div>
    ${open && html`<div class="body-s muted reveal">${info}</div>`}
    <div class="sp8"></div>
  </div>`;
}

function LevelSection({ app, s }) {
  const st = s.settings;
  const d = derive(s);
  const [early, setEarly] = useState(false);
  return html`<div>
    <div class="title-s">Aktivna lista: ${st.level * 1000} najčešćih riječi</div>
    <div class="sp4"></div>
    ${st.level < d.maxLevel
      ? html`<div class="body-s muted">Sljedećih 1000 otključava se kad znaš ili svladaš ${Math.round(UNLOCK_SHARE * 100)} % trenutne liste: ${d.solid} / ${d.unlockTarget}.</div>
        <div class="sp10"></div>
        <${Progress} value=${d.solid / Math.max(1, d.unlockTarget)} />
        <div class="sp12"></div>
        <div class="row gap8">
          <button class="btn auto" disabled=${!d.canUnlock} onClick=${() => app.setLevel(st.level + 1)}>Otključaj ${(st.level + 1) * 1000}</button>
          ${!d.canUnlock && html`<button class="btn text" onClick=${() => setEarly(true)}>Ipak sada</button>`}
        </div>`
      : html`<div class="body-s muted">Otključane su sve riječi.</div>`}
    ${st.level > 1 && html`<div class="sp8"></div><button class="btn text" style="padding:0" onClick=${() => app.setLevel(st.level - 1)}>Vrati na ${(st.level - 1) * 1000}</button>`}
    ${early && html`<${Dialog} title="Otključati prije vremena?" confirm="Otključaj"
      text="Nove riječi dolaze tek kad potrošiš sve iz trenutne liste, pa se ništa ne gubi. Ali češće riječi donose više u razgovoru — preporuka je prvo svladati ovu razinu."
      onClose=${() => setEarly(false)} onConfirm=${() => { app.setLevel(st.level + 1); setEarly(false); }} />`}
  </div>`;
}

function Backup({ app, s }) {
  const file = useRef(null);
  const [pending, setPending] = useState(null);
  const [msg, setMsg] = useState(null);
  const d = derive(s);

  const exportNow = async () => {
    const text = JSON.stringify(app.exportBackup());
    const name = `wortschatz-${new Date().toISOString().slice(0, 10)}.json`;
    try {
      const f = new File([text], name, { type: 'application/json' });
      if (navigator.canShare && navigator.canShare({ files: [f] })) {
        await navigator.share({ files: [f], title: 'Wortschatz napredak' });
        setMsg('Kopija je spremna.');
        return;
      }
    } catch (e) {
      if (e && e.name === 'AbortError') return;
    }
    const url = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 5000);
    setMsg('Kopija je preuzeta.');
  };

  const pick = (e) => {
    const f = e.currentTarget.files && e.currentTarget.files[0];
    e.currentTarget.value = '';
    if (!f) return;
    f.text().then((text) => setPending(text), () => setMsg('Datoteka se ne može pročitati.'));
  };

  return html`<div>
    <div class="body-m muted" style="padding:8px 8px 16px">Napredak se čuva samo u ovom pregledniku. Povremeno spremi kopiju (npr. u Datoteke ili iCloud), pa ga možeš vratiti na novom uređaju ili nakon brisanja podataka.</div>
    <div class="panel padded">
      <div class="title-s">Tvoj napredak</div>
      <div class="body-s muted">${d.familiar} riječi poznato ili u učenju · ${Object.keys(s.days).length} dana aktivnosti</div>
      <div class="sp16"></div>
      <button class="btn" onClick=${exportNow}><${Icon} name="download" size=${20} />Spremi kopiju</button>
      <div class="sp10"></div>
      <button class="btn tonal" onClick=${() => file.current && file.current.click()}><${Icon} name="upload" size=${20} />Vrati iz kopije</button>
      <input type="file" accept="application/json,.json" ref=${file} style="display:none" onChange=${pick} />
      ${msg && html`<div class="sp12"></div><div class="body-s primary">${msg}</div>`}
    </div>
    <div class="body-s muted" style="padding:16px 8px">Dnevni podsjetnik u web verziji ne postoji; najlakše je dodati ponavljajući podsjetnik u aplikaciji Podsjetnici.</div>
    ${pending && html`<${Dialog} title="Vratiti napredak iz kopije?" confirm="Vrati" danger
      text="Trenutni napredak na ovom uređaju zamijenit će se onim iz kopije."
      onClose=${() => setPending(null)}
      onConfirm=${() => {
        try {
          const n = app.importBackup(pending);
          setMsg(`Vraćeno: ${n} riječi.`);
        } catch (e) {
          setMsg(e.message && e.message.startsWith('Ovo') ? e.message : 'Ovo nije ispravna kopija.');
        }
        setPending(null);
      }} />`}
  </div>`;
}
