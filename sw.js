const CACHE_NAME = 'only-eyes-app-shell-v1';

const CORE_ASSETS = [
  './',
  './index.html',
  './manifest.webmanifest',
  './icons/icon-192.png',
  './icons/icon-512.png'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then((cache) => Promise.allSettled(CORE_ASSETS.map((asset) => cache.add(asset))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(
        keys
          .filter((key) => key !== CACHE_NAME && (key.startsWith('only-eyes-') || key.startsWith('transmission-')))
          .map((key) => caches.delete(key))
      ))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'SKIP_WAITING') self.skipWaiting();
});

function networkTimeout(ms) {
  return new Promise((_, reject) => {
    setTimeout(() => reject(new Error('network-timeout')), ms);
  });
}

async function navigationResponse(request) {
  const cache = await caches.open(CACHE_NAME);
  const cachedExact = await caches.match(request, { ignoreSearch: true });
  const fallback = await cache.match('./index.html');

  try {
    const network = await Promise.race([fetch(request), networkTimeout(4000)]);
    if (network && network.ok) {
      cache.put(request, network.clone()).catch(() => {});
      return network;
    }
  } catch (e) {}

  return cachedExact || fallback || new Response('Only Eyes indisponible hors ligne.', {
    status: 503,
    headers: { 'Content-Type': 'text/plain; charset=utf-8' }
  });
}

async function cacheFirst(request) {
  const cached = await caches.match(request, { ignoreSearch: true });
  if (cached) return cached;

  const response = await fetch(request);
  if (response && response.ok) {
    const cache = await caches.open(CACHE_NAME);
    cache.put(request, response.clone()).catch(() => {});
  }
  return response;
}

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  if (request.mode === 'navigate') {
    event.respondWith(navigationResponse(request));
    return;
  }

  event.respondWith(
    cacheFirst(request).catch(() => caches.match(request, { ignoreSearch: true }))
  );
});
