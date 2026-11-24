// Offline shell: cache the app so Private mode keeps working without a network.
const CACHE = 'chaukas-shell-v3';
// Holds a screenshot shared from another app until the page picks it up.
const SHARE = 'chaukas-share';
const SHELL = ['/', '/index.html', '/icon.svg', '/manifest.webmanifest'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE && k !== SHARE).map((k) => caches.delete(k)))).then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  const url = new URL(req.url);
  // Share target: text and screenshots shared from other apps arrive here as a POST.
  if (req.method === 'POST' && url.origin === location.origin && url.pathname === '/share') {
    e.respondWith(receiveShare(req));
    return;
  }
  if (req.method !== 'GET' || url.origin !== location.origin) return;
  // Navigations: network first, fall back to the cached shell.
  if (req.mode === 'navigate') {
    e.respondWith(fetch(req).catch(() => caches.match('/index.html')));
    return;
  }
  // Built assets are content-hashed, so cache-first is safe.
  e.respondWith(
    caches.match(req).then(
      (hit) =>
        hit ||
        fetch(req).then((res) => {
          if (res.ok && url.pathname.startsWith('/assets/')) {
            const copy = res.clone();
            caches.open(CACHE).then((c) => c.put(req, copy));
          }
          return res;
        }),
    ),
  );
});

async function receiveShare(req) {
  const params = new URLSearchParams();
  try {
    const form = await req.formData();
    for (const key of ['title', 'text', 'url']) {
      const v = form.get(key);
      if (typeof v === 'string' && v.trim()) params.set(key, v.slice(0, 8000));
    }
    const image = form.get('image');
    if (image && typeof image !== 'string' && image.type.startsWith('image/')) {
      const cache = await caches.open(SHARE);
      await cache.put('/shared-image', new Response(image, { headers: { 'content-type': image.type } }));
      params.set('image', '1');
    }
  } catch {
    // Fall through to an empty check screen.
  }
  params.set('shared', '1');
  return Response.redirect(`/?${params.toString()}#/check`, 303);
}

// Tapping a group-warning notification opens (or focuses) Family Circle.
self.addEventListener('notificationclick', (e) => {
  e.notification.close();
  const target = new URL(e.notification.data?.url || '/#/circle', location.origin).href;
  e.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((list) => {
      for (const c of list) {
        if (new URL(c.url).origin === location.origin && 'focus' in c) {
          c.navigate?.(target);
          return c.focus();
        }
      }
      return self.clients.openWindow(target);
    }),
  );
});
