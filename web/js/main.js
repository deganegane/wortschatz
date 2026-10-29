import { html, render, useEffect, useRef, useState } from './vendor/htm-preact.js';
import { App } from './app-state.js';
import { Icon, useAppState } from './ui.js';
import { TodayTab } from './screens/today.js';
import { TestTab } from './screens/test.js';
import { ReviewScreen } from './screens/review.js';
import { ShadowingScreen, GrammarScreen } from './screens/practice.js';
import { ReadingTab } from './screens/reading.js';
import { DictionaryTab } from './screens/dictionary.js';
import { SettingsScreen } from './screens/settings.js';

const app = new App();
window.wortschatz = app; // handy for debugging

const TABS = [
  ['today', 'Danas'],
  ['quiz', 'Test'],
  ['read', 'Čitanje'],
  ['book', 'Rječnik'],
];

function Root() {
  const s = useAppState(app);
  const [tab, setTab] = useState(0);
  const [settings, setSettings] = useState(false);
  const [page, setPage] = useState(null);
  const [story, setStory] = useState(null);
  const kb = useKeyboard();
  useTheme(s.settings.themeMode);

  const overlay = s.session ? 'review' : s.shadowing ? 'shadow' : s.grammar ? 'grammar' : null;
  const depth = (overlay ? 1 : 0) + (settings ? 1 : 0) + (settings && page ? 1 : 0) + (tab === 2 && story ? 1 : 0);
  const back = () => {
    const st = app.state;
    if (st.session) app.closeSession();
    else if (st.shadowing) app.closeShadowing();
    else if (st.grammar) app.closeGrammar();
    else if (pageRef.current) setPage(null);
    else if (settingsRef.current) setSettings(false);
    else if (storyRef.current) setStory(null);
  };
  const pageRef = useRef(page);
  const settingsRef = useRef(settings);
  const storyRef = useRef(story);
  pageRef.current = page;
  settingsRef.current = settings;
  storyRef.current = tab === 2 ? story : null;
  useBackButton(depth, back);

  let content;
  if (s.loading) content = html`<div class="loading"><div class="spinner"></div></div>`;
  else if (s.error) content = html`<div class="center-fill"><div class="title-m">${s.error}</div><div class="sp16"></div><button class="btn auto" onClick=${() => location.reload()}>Pokušaj ponovno</button></div>`;
  else if (overlay === 'review') content = html`<${ReviewScreen} app=${app} s=${s} />`;
  else if (overlay === 'shadow') content = html`<${ShadowingScreen} app=${app} s=${s} />`;
  else if (overlay === 'grammar') content = html`<${GrammarScreen} app=${app} s=${s} />`;
  else if (settings) content = html`<${SettingsScreen} app=${app} s=${s} page=${page} setPage=${setPage} onBack=${() => { setPage(null); setSettings(false); }} />`;
  else {
    let body;
    if (tab === 0) body = html`<${TodayTab} app=${app} s=${s} onOpenTest=${() => setTab(1)} onOpenSettings=${() => setSettings(true)} />`;
    else if (tab === 1) body = html`<${TestTab} app=${app} s=${s} onStartLearning=${() => { setTab(0); app.startSession(); }} />`;
    else if (tab === 2) body = html`<${ReadingTab} app=${app} s=${s} openStory=${story} onOpen=${setStory} />`;
    else body = html`<${DictionaryTab} app=${app} s=${s} />`;
    content = html`<div class="screen" key=${'tab' + tab}>${body}</div>
      <nav class="nav">
        ${TABS.map(([icon, label], i) => html`<button class=${tab === i ? 'on' : ''} aria-current=${tab === i ? 'page' : undefined}
          onClick=${() => { if (tab === i && i === 2) setStory(null); setTab(i); }}>
          <span class="ind"><${Icon} name=${icon} /></span>
          <span class="label-s">${label}</span>
        </button>`)}
      </nav>`;
  }
  const tabbed = !s.loading && !overlay && !settings;
  return html`<div class=${'app' + (tabbed ? ' tabbed' : '') + (kb ? ' kb-open' : '')}>
    ${content}
    ${s.toast && html`<div class="toast" role="status">${s.toast}</div>`}
  </div>`;
}

/** Browser/phone back closes the topmost screen instead of leaving the app. */
function useBackButton(depth, back) {
  const depthRef = useRef(0);
  const ignore = useRef(0);
  const backRef = useRef(back);
  backRef.current = back;
  useEffect(() => {
    const prev = depthRef.current;
    if (depth > prev) for (let i = prev; i < depth; i++) history.pushState({ w: i + 1 }, '');
    else if (depth < prev) {
      ignore.current++;
      history.go(depth - prev);
    }
    depthRef.current = depth;
  }, [depth]);
  useEffect(() => {
    const h = () => {
      if (ignore.current > 0) {
        ignore.current--;
        return;
      }
      if (depthRef.current > 0) {
        depthRef.current--;
        backRef.current();
      }
    };
    window.addEventListener('popstate', h);
    return () => window.removeEventListener('popstate', h);
  }, []);
}

/** Tracks the on-screen keyboard (iOS shrinks only the visual viewport). */
function useKeyboard() {
  const [open, setOpen] = useState(false);
  useEffect(() => {
    const vv = window.visualViewport;
    if (!vv) return;
    const update = () => {
      const kb = window.innerHeight - vv.height > 140;
      document.documentElement.style.setProperty('--vvh', kb ? `${vv.height}px` : '100%');
      setOpen(kb);
      window.scrollTo(0, 0);
    };
    vv.addEventListener('resize', update);
    vv.addEventListener('scroll', update);
    return () => {
      vv.removeEventListener('resize', update);
      vv.removeEventListener('scroll', update);
    };
  }, []);
  return open;
}

function useTheme(mode) {
  useEffect(() => {
    const root = document.documentElement;
    if (mode === 'LIGHT') root.dataset.theme = 'light';
    else if (mode === 'DARK') root.dataset.theme = 'dark';
    else delete root.dataset.theme;
    const bg = getComputedStyle(root).getPropertyValue('--bg').trim();
    document.querySelectorAll('meta[name="theme-color"]').forEach((m) => m.setAttribute('content', bg));
  }, [mode]);
}

// iOS only lets a page speak after a tap: say nothing on the first one to unlock speech.
function unlockSpeech() {
  try {
    const u = new SpeechSynthesisUtterance(' ');
    u.volume = 0;
    window.speechSynthesis.speak(u);
  } catch (e) {
    /* no speech */
  }
}
document.addEventListener('touchend', unlockSpeech, { once: true, capture: true });
document.addEventListener('click', unlockSpeech, { once: true, capture: true });

document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'hidden') {
    if (app.state.listen.phase === 'LISTENING') app.cancelListening();
  } else {
    app.resumed();
    if (navigator.serviceWorker) navigator.serviceWorker.getRegistration().then((r) => r && r.update()).catch(() => {});
  }
});

render(html`<${Root} />`, document.getElementById('root'));
app.load();

if ('serviceWorker' in navigator && location.protocol === 'https:') {
  const hadController = !!navigator.serviceWorker.controller;
  navigator.serviceWorker.register('sw.js').catch(() => {});
  // A new version took over: reload once, unless you're in the middle of a round.
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    const st = app.state;
    if (hadController && !st.session && !st.shadowing && !st.grammar) location.reload();
  });
}
