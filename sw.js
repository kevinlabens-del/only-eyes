const CACHE='only-eyes-v281';
const STATIC=['./','./index.html','./manifest.webmanifest','./icons/icon-192.png','./icons/icon-512.png','./README.txt','./v281.js'];
self.addEventListener('install',event=>{event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(STATIC)).then(()=>self.skipWaiting()));});
self.addEventListener('message',event=>{if(event.data?.type==='SKIP_WAITING')self.skipWaiting();});
self.addEventListener('activate',event=>{event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>(k.startsWith('only-eyes-')||k.startsWith('transmission-'))&&k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim()));});
async function patchNavigation(request){
  try{
    const response=await fetch(request,{cache:'no-store'});
    const type=response.headers.get('content-type')||'';
    if(!type.includes('text/html'))return response;
    let html=await response.text();
    html=html.replace('<title>Only Eyes V2.8</title>','<title>Only Eyes</title>');
    html=html.replace(/V2\.8(?!\.1)/g,'V2.8.1');
    if(!html.includes('v281.js'))html=html.replace('</body>','<script src="./v281.js?v=2.8.1" defer></script></body>');
    const headers=new Headers(response.headers);headers.set('content-type','text/html; charset=utf-8');headers.set('cache-control','no-store');
    const patched=new Response(html,{status:response.status,statusText:response.statusText,headers});
    const copy=patched.clone();caches.open(CACHE).then(cache=>cache.put('./index.html',copy));
    return patched;
  }catch(e){return (await caches.match('./index.html'))||Response.error();}
}
self.addEventListener('fetch',event=>{
  if(event.request.method!=='GET')return;
  const url=new URL(event.request.url);if(url.origin!==location.origin)return;
  if(event.request.mode==='navigate'){event.respondWith(patchNavigation(event.request));return;}
  event.respondWith(fetch(event.request,{cache:'no-store'}).then(response=>{const copy=response.clone();caches.open(CACHE).then(cache=>cache.put(event.request,copy));return response;}).catch(()=>caches.match(event.request)));
});
