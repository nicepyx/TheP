/* FitWave offline cache. Dynamic cache names allow safe updates after deployment. */
const CACHE = 'fitwave-static-v4.1.0';
const PRECACHE = ['/', '/index.html', '/manifest.webmanifest', '/icons/icon-192.png', '/icons/icon-512.png', '/mascot/a1_idle.png', '/mascot/a1_cheer.png', '/mascot/a1_rest.png', '/mascot/a1_done.png', '/mascot/a1_hero.webp'];
/* __PRECACHE_INJECT__ */
self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(PRECACHE)));
  self.skipWaiting();
});
self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key.startsWith('fitwave-static-') && key !== CACHE).map(key => caches.delete(key)))));
  self.clients.claim();
});
self.addEventListener('fetch', event => {
  if (event.request.method !== 'GET' || new URL(event.request.url).origin !== self.location.origin) return;
  event.respondWith(caches.match(event.request).then(cached => {
    const network = fetch(event.request).then(response => {
      if (response.ok) { const clone=response.clone(); caches.open(CACHE).then(cache => cache.put(event.request,clone)); }
      return response;
    }).catch(() => cached);
    return cached || network;
  }));
});
