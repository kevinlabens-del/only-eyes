const CACHE='only-eyes-v285';
const APP_SCOPE='/only-eyes/';
const CORE=[
  APP_SCOPE,
  APP_SCOPE+'index.html',
  APP_SCOPE+'manifest.webmanifest',
  APP_SCOPE+'icons/icon-192.png',
  APP_SCOPE+'icons/icon-512.png'
];

self.addEventListener('install',event=>{
  event.waitUntil((async()=>{
    const cache=await caches.open(CACHE);
    for(const url of CORE){
      try{
        const response=await fetch(new Request(url,{cache:'reload'}));
        if(response.ok) await cache.put(url,response.clone());
      }catch{}
    }
    await self.skipWaiting();
  })());
});

self.addEventListener('message',event=>{
  if(event.data?.type==='SKIP_WAITING') self.skipWaiting();
});

self.addEventListener('activate',event=>{
  event.waitUntil((async()=>{
    const keys=await caches.keys();
    await Promise.all(keys
      .filter(key=>key!==CACHE && (key.startsWith('only-eyes-')||key.startsWith('transmission-')))
      .map(key=>caches.delete(key)));
    await self.clients.claim();
  })());
});

self.addEventListener('fetch',event=>{
  if(event.request.method!=='GET') return;
  const url=new URL(event.request.url);
  if(url.origin!==self.location.origin) return;

  if(event.request.mode==='navigate'){
    event.respondWith((async()=>{
      try{
        const response=await fetch(event.request,{cache:'no-store'});
        if(response.ok){
          const cache=await caches.open(CACHE);
          await cache.put(APP_SCOPE+'index.html',response.clone());
        }
        return response;
      }catch{
        return (await caches.match(APP_SCOPE+'index.html')) || (await caches.match(APP_SCOPE));
      }
    })());
    return;
  }

  event.respondWith((async()=>{
    try{
      const response=await fetch(event.request,{cache:'no-store'});
      if(response.ok){
        const cache=await caches.open(CACHE);
        await cache.put(event.request,response.clone());
      }
      return response;
    }catch{
      return (await caches.match(event.request)) || Response.error();
    }
  })());
});
