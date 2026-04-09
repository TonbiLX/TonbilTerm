// SW v3 — tüm eski cache'leri temizle ve kendini kaldır
self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.map((k) => caches.delete(k)))
    ).then(() => {
      // SW kendini unregister et — artık cache yapmıyoruz
      return self.clients.matchAll();
    }).then((clients) => {
      clients.forEach((client) => client.navigate(client.url));
    })
  );
  self.clients.claim();
});

// Tüm istekleri doğrudan network'ten al — cache yok
self.addEventListener('fetch', (event) => {
  event.respondWith(fetch(event.request));
});
