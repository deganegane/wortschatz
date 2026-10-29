// Offline support: everything the app needs is cached on install; a new deploy (new VERSION)
// replaces the cache.
const VERSION = 'dev';
const CACHE = 'wortschatz-' + VERSION;
const FILES = [
  './', 'index.html', 'manifest.webmanifest', 'css/app.css',
  'js/main.js', 'js/app-state.js', 'js/model.js', 'js/srs.js', 'js/speechcheck.js', 'js/store.js', 'js/speech.js',
  'js/ui.js', 'js/icons.js', 'js/version.js', 'js/vendor/htm-preact.js',
  'js/screens/today.js', 'js/screens/test.js', 'js/screens/review.js', 'js/screens/practice.js',
  'js/screens/reading.js', 'js/screens/dictionary.js', 'js/screens/settings.js',
  'fonts/space_grotesk_regular.ttf', 'fonts/space_grotesk_medium.ttf', 'fonts/space_grotesk_bold.ttf',
  'icons/favicon.svg', 'icons/apple-touch-icon.png', 'icons/icon-192.png',
  'data/words.json', 'data/stories.json', 'data/forms.json',
];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(FILES.map((f) => new Request(f, { cache: 'reload' })))).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k.startsWith('wortschatz-') && k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== location.origin) return;
  e.respondWith(
    caches.open(CACHE).then((c) =>
      c.match(req, { ignoreSearch: true }).then((hit) =>
        hit ||
        fetch(req).then((res) => {
          if (res.ok && res.type === 'basic') c.put(req, res.clone());
          return res;
        }).catch(() => (req.mode === 'navigate' ? c.match('./') : Response.error())),
      ),
    ),
  );
});
