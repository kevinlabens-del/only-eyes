(()=>{
'use strict';
const VERSION='2.8.1';
const INSTALL_MARK='only-eyes-installed-v281';
let deferredInstallPrompt=null;
let installBtn=null;
const standalone=()=>window.matchMedia('(display-mode: standalone)').matches||window.navigator.standalone===true;
function toast(msg){
  const existing=document.getElementById('toast');
  if(existing){existing.textContent=msg;existing.classList.add('show');setTimeout(()=>existing.classList.remove('show'),3600);return;}
  const el=document.createElement('div');el.textContent=msg;el.style.cssText='position:fixed;left:50%;bottom:24px;transform:translateX(-50%);z-index:99999;background:#111;color:#fff;border:1px solid #444;border-radius:12px;padding:12px 14px;width:min(90%,430px);text-align:center;font:700 13px Arial';document.body.appendChild(el);setTimeout(()=>el.remove(),3600);
}
function ensureButton(){
  if(installBtn&&document.contains(installBtn))return installBtn;
  const footer=document.querySelector('footer.controls');
  if(!footer)return null;
  installBtn=document.createElement('button');
  installBtn.id='installBtn';
  installBtn.className='primary';
  installBtn.type='button';
  installBtn.textContent='INSTALLER L’APPLICATION';
  installBtn.style.order='-10';
  footer.prepend(installBtn);
  installBtn.addEventListener('click',installApp);
  return installBtn;
}
function updateInstallUI(){
  const b=ensureButton();if(!b)return;
  if(standalone()){
    localStorage.setItem(INSTALL_MARK,'1');
    b.textContent='✓ APPLICATION INSTALLÉE';b.disabled=true;return;
  }
  if(localStorage.getItem(INSTALL_MARK)==='1'){
    b.textContent='✓ DÉJÀ INSTALLÉ';b.disabled=true;return;
  }
  b.disabled=false;
  b.textContent=deferredInstallPrompt?'INSTALLER L’APPLICATION':'INSTALLATION ANDROID';
}
async function installApp(){
  if(standalone()){updateInstallUI();return;}
  if(!deferredInstallPrompt){
    toast('Dans Chrome, ouvre le menu ⋮ puis « Installer l’application ». Si Only Eyes est déjà installé, ouvre-le depuis le tiroir d’applications.');
    return;
  }
  const p=deferredInstallPrompt;deferredInstallPrompt=null;
  const b=ensureButton();if(b){b.disabled=true;b.textContent='INSTALLATION…';}
  try{
    await p.prompt();
    const choice=await p.userChoice;
    if(choice.outcome==='accepted')toast('Installation lancée… confirme dans Android.');
    else{localStorage.removeItem(INSTALL_MARK);toast('Installation annulée.');}
  }catch(e){toast('Installation impossible : '+(e?.message||e));}
  updateInstallUI();
}
window.addEventListener('beforeinstallprompt',e=>{e.preventDefault();deferredInstallPrompt=e;updateInstallUI();});
window.addEventListener('appinstalled',()=>{localStorage.setItem(INSTALL_MARK,'1');deferredInstallPrompt=null;updateInstallUI();toast('Only Eyes est installé sur ton téléphone ✓');});
window.matchMedia('(display-mode: standalone)').addEventListener?.('change',updateInstallUI);
function init(){
  document.title='Only Eyes';
  document.querySelectorAll('.badge').forEach(el=>{if(el.textContent.includes('ONLY EYES')){const span=el.querySelector('span');if(span)span.textContent='V'+VERSION;}});
  const diag=document.getElementById('diag');if(diag&&diag.textContent.includes('V2.8'))diag.textContent=diag.textContent.replace(/V2\.8(?!\.1)/g,'V2.8.1');
  ensureButton();updateInstallUI();
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();
