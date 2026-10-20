/** FitWave V4.1: A1 character state machine + opt-in Android pet overlay. */
import { Capacitor, registerPlugin } from '@capacitor/core';
import { App } from '@capacitor/app';

type CharacterState = 'idle'|'greeting'|'touch'|'drag'|'cheer'|'rest'|'ready'|'warning'|'done'|'sleep';
type PetMode = 'training'|'always';
type PetPrefs = { mode:PetMode; size:number; animation:boolean };
type PetStatus = {supported:boolean;permission:boolean;running:boolean;visible:boolean;endAt:number;pendingCommit:number;pendingExercise:string;pendingTarget:string;size:number};
interface PetNative {
  status():Promise<PetStatus>;
  requestOverlayPermission():Promise<{permission?:boolean;openedSettings?:boolean}>;
  start(args:{mode:PetMode;size:number}):Promise<void>;
  update(args:{state:CharacterState;exercise:string;target:string;endAt:number;size:number;inApp:boolean;animation:boolean}):Promise<void>;
  show():Promise<void>;
  hide():Promise<void>;
  stop():Promise<void>;
  acknowledgeCommit(args:{id:number}):Promise<void>;
}
const Pet=registerPlugin<PetNative>('FitWavePet');
const native=Capacitor.isNativePlatform() && Capacitor.getPlatform()==='android';
const PREFIX='fitwave:v41:';
const initial=():PetPrefs=>{
  try { const x=JSON.parse(localStorage.getItem(PREFIX+'prefs')||'{}'); return {mode:x.mode==='always'?'always':'training',size:Math.max(64,Math.min(160,Number(x.size)||100)),animation:x.animation!==false}; }
  catch{return {mode:'training',size:100,animation:true};}
};
let prefs=initial();let state:CharacterState='idle';let restEndAt=0;let running=false;let lastUpdate=0;let updateHandle:number|undefined;let noticeHandle:number|undefined;let handledCommit=0;
const img:Record<CharacterState,string>={idle:'a1_idle.png',greeting:'a1_cheer.png',touch:'a1_done.png',drag:'a1_cheer.png',cheer:'a1_cheer.png',rest:'a1_rest.png',ready:'a1_cheer.png',warning:'a1_cheer.png',done:'a1_done.png',sleep:'a1_rest.png'};
const savePrefs=()=>{try{localStorage.setItem(PREFIX+'prefs',JSON.stringify(prefs));}catch{}};
function toast(t:string){const el=document.getElementById('toast');if(!el)return;el.textContent=t;el.classList.add('show');window.clearTimeout(noticeHandle);noticeHandle=window.setTimeout(()=>el.classList.remove('show'),2900);}
function focusInfo(){const area=document.querySelector('.focus-open .focus-ex-card');
  const exercise=area?.querySelector('h1')?.textContent?.trim()||'';
  const weight=(document.getElementById('focus-weight') as HTMLInputElement|null)?.value||'';
  const reps=(document.getElementById('focus-reps') as HTMLInputElement|null)?.value||'';
  const target=exercise?`${weight||'—'}kg × ${reps||'—'}次`:'返回 FitWave 开始训练';
  return {exercise,target};
}
function repaint(state:CharacterState){
  const portrait=img[state];
  const url='/mascot/'+portrait;
  document.querySelectorAll<HTMLImageElement>('img[data-mascot]').forEach(el=>{
    const old=el.dataset.mascot;
    if(old==='hero')return;
    const selected=old==='run' && state==='idle'?'/mascot/a1_cheer.png':url;
    if(el.getAttribute('src')!==selected)el.src=selected;
  });
  document.querySelectorAll<HTMLElement>('[data-a1-status-text]').forEach(e=>e.textContent=state==='rest'?'休息一下，记得补水':state==='done'?'今天也完成啦！':state==='ready'?'休息结束，准备下一组':state==='cheer'?'这一组做得好！':'一起把今天的训练做好吧');
}
function setState(next:CharacterState){state=next; (window as Window&{__A1_STATE?:CharacterState}).__A1_STATE=next;repaint(next);pushNative();}
function pushNative(){if(!native||!running)return;window.clearTimeout(updateHandle);updateHandle=window.setTimeout(()=>{
  const f=focusInfo();const inApp=!document.hidden;
  void Pet.update({state,exercise:f.exercise,target:f.target,endAt:restEndAt,size:prefs.size,inApp,animation:prefs.animation}).catch(e=>console.info('[FitWavePet] update skipped',e));
},100);}
function setActive(on:boolean){running=on;(window as Window&{__FW_PET_OVERLAY_VISIBLE__?:boolean}).__FW_PET_OVERLAY_VISIBLE__=on;refreshSettings();}
async function getStatus():Promise<PetStatus|null>{if(!native)return null;try{return await Pet.status();}catch(e){console.warn('[FitWavePet] status unavailable',e);return null}}
async function startPet(){if(!native){toast('系统悬浮精灵仅适用于 Android APK；网页版仍可看到应用内吉祥物');return;}
 const s=await getStatus();if(!s)return;
 if(!s.permission){await Pet.requestOverlayPermission();toast('请在系统设置允许显示在其他应用上层，返回后再点击启动');return;}
 try{
   if(s.running) await Pet.show();
   else await Pet.start({mode:prefs.mode,size:prefs.size});
   setActive(true);await syncOnResume();
   toast(s.running?'已恢复精灵显示。返回桌面即可查看':'A1 桌面精灵已启动。按 Home 即可看到，也可以从通知栏关闭');
 }
 catch(e){toast('无法启动桌面精灵。请检查悬浮窗和系统权限');console.warn(e);}
}
async function stopPet(){if(native){try{await Pet.stop();}catch(e){console.warn(e)}}setActive(false);toast('桌面精灵已停止');}
async function hidePet(){if(native){await Pet.hide().catch(console.warn);setActive(true)}toast('精灵已暂时隐藏，服务继续运行。可点击恢复显示或从通知栏停止');}
function injectSettings(){const p=document.getElementById('page-settings');if(!p||!p.children.length||p.querySelector('#a1-pet-panel'))return;
 const card=document.createElement('section');card.className='panel a1-pet-panel';card.id='a1-pet-panel';
 card.innerHTML=`<div class="a1-pet-head"><img src="/mascot/a1_idle.png" alt="A1 奶霜丝带少女"><div><h2>奶霜丝带 · 桌面精灵</h2><p>全局吉祥物 · 后台陪伴 · 训练联动</p></div></div>
 <div class="a1-pet-preview"><img src="/mascot/a1_cheer.png" alt="角色预览"><div class="a1-pet-status"><b id="a1-pet-status">正在检查权限…</b><span id="a1-pet-comment">可在其他应用上方显示透明精灵</span></div></div>
 <div class="setting-row"><div><strong>运行模式</strong><p>仅训练期间 / 全天主动陪伴</p></div></div>
 <div class="seg" role="group" aria-label="桌宠运行模式"><button data-a1-mode="training" class="${prefs.mode==='training'?'active':''}">训练陪伴</button><button data-a1-mode="always" class="${prefs.mode==='always'?'active':''}">全天陪伴</button></div>
 <div style="margin-top:12px"><label for="a1-size" class="onboard">桌面精灵尺寸 <b id="a1-size-label">${prefs.size} dp</b></label><input class="a1-pet-size" id="a1-size" aria-label="桌面精灵尺寸" type="range" min="64" max="160" step="4" value="${prefs.size}"></div>
 <div class="setting-row"><div><strong>角色动态呼吸</strong><p>关闭可减少动态效果和电量消耗</p></div><button class="toggle ${prefs.animation?'on':''}" data-a1-motion="1" aria-label="切换角色动画" aria-pressed="${prefs.animation}"></button></div>
 <div class="a1-pet-actions"><button class="primary" data-a1-pet-start="1">启动桌面精灵</button><button class="secondary" data-a1-pet-show="1">恢复显示</button><button class="secondary" data-a1-pet-hide="1">暂时隐藏</button><button class="secondary" data-a1-pet-stop="1">彻底停止</button><button class="secondary" data-a1-pet-permission="1">授权与状态</button></div>
 <div class="a1-pet-note">首次需主动授权“显示在其他应用上层”。默认不会开机自启，也不申请无障碍或读取其他应用。训练后仅在「训练陪伴」模式自动结束；通知栏始终有停止入口。锁屏或系统清理后台后不保证持续显示。</div>
 <div class="section-header"><h2>吉祥物互动测试</h2><p>应用内轻量表情与浮窗同步</p></div><div class="seg"><button data-a1-emotion="idle">待机</button><button data-a1-emotion="cheer">加油</button><button data-a1-emotion="rest">休息</button><button data-a1-emotion="done">庆祝</button></div>`;
 const info=p.querySelector('.panel');if(info)info.before(card);else p.prepend(card);
 void refreshSettings();
}
async function refreshSettings(){const label=document.getElementById('a1-pet-status');if(!label)return;
 const s=await getStatus();const yes=s?.running??false;
 label.textContent=!native?'网页预览模式':yes?(s?.visible?'系统悬浮精灵已启动':'运行中 · 精灵已隐藏'):s?.permission?'已获权限 · 当前关闭':'尚未授予悬浮窗权限';
 const comment=document.getElementById('a1-pet-comment');if(comment)comment.textContent=!native?'请安装 Android APK 以启用真正跨应用浮窗':yes?'按 Home 查看 · 通知栏可以隐藏或停止':'需要在 App 前台主动点击启动';
}
async function syncOnResume(){const s=await getStatus();if(!s)return;
 if(s.running!==running)setActive(s.running);
 if(s.running&&s.endAt>restEndAt+800&&s.endAt>Date.now()){
   restEndAt=s.endAt;setState('rest');window.dispatchEvent(new CustomEvent('fitwave:pet-sync-rest',{detail:{endAt:restEndAt}}));
 }
 if(s.pendingCommit&&s.pendingCommit!==handledCommit){
   handledCommit=s.pendingCommit;
   window.setTimeout(async()=>{
     if(!document.body.classList.contains('focus-open'))document.querySelector<HTMLButtonElement>('[data-go="train"]')?.click();
     if(!document.body.classList.contains('focus-open'))document.querySelector<HTMLButtonElement>('[data-focus-open]')?.click();
     const current=focusInfo();
     if(current.exercise===s.pendingExercise && current.target===s.pendingTarget){
       if(window.confirm(`A1 请求完成一组：\n${current.exercise}\n${current.target}\n确认已经完成并记录吗？`))document.querySelector<HTMLButtonElement>('[data-focus-done]')?.click();
     }else toast('已返回训练页。请先核对本组重量和次数，再手动打卡');
     try{await Pet.acknowledgeCommit({id:s.pendingCommit})}catch(e){console.info(e)}
   },400);
 }
}
const settingsObs=new MutationObserver(()=>injectSettings());
const settingsNode=document.getElementById('page-settings');if(settingsNode)settingsObs.observe(settingsNode,{childList:true,subtree:false});
document.addEventListener('click',e=>{const t=e.target;if(!(t instanceof Element))return;const b=t.closest<HTMLButtonElement>('button');if(!b)return;
 if(b.dataset.a1PetStart)void startPet();
 if(b.dataset.a1PetStop)void stopPet();
 if(b.dataset.a1PetHide)void hidePet();
 if(b.dataset.a1PetShow)void (async()=>{if(!native)return toast('仅 Android APK 支持系统悬浮精灵'); try{const s=await getStatus();if(s?.running){await Pet.show();setActive(true);toast('精灵已恢复显示');}else await startPet();}catch(e){console.warn(e);toast('恢复显示失败，请检查悬浮窗权限');}})();
 if(b.dataset.a1PetPermission)void (async()=>{const s=await getStatus();if(!native){toast('请在手机安装 Android APK 后使用');return;}if(!s?.permission)await Pet.requestOverlayPermission();else toast(s.running?'系统精灵已启动，可以通过通知栏停止':'悬浮窗权限正常，可以启动');})();
 if(b.dataset.a1Mode){prefs.mode=b.dataset.a1Mode==='always'?'always':'training';savePrefs();document.querySelectorAll('[data-a1-mode]').forEach(x=>x.classList.toggle('active',(x as HTMLElement).dataset.a1Mode===prefs.mode));pushNative();}
 if(b.dataset.a1Emotion)setState(b.dataset.a1Emotion as CharacterState);
 if(b.dataset.a1Motion){prefs.animation=!prefs.animation;savePrefs();document.body.classList.toggle('a1-motion-off',!prefs.animation);b.classList.toggle('on',prefs.animation);b.setAttribute('aria-pressed',String(prefs.animation));pushNative();}
 if(b.dataset.mascotHelp){setState('touch');window.setTimeout(()=>{if(state==='touch')setState('idle')},2000);}
 // Legacy mark-done paths share a single event observer; don't send a second record to native.
 if(b.dataset.focusDone||b.dataset.check){window.setTimeout(()=>{setState('cheer');window.setTimeout(()=>{if(state==='cheer')setState('idle');},1600)},80);}
}, {capture:false});
document.addEventListener('input',e=>{const t=e.target;if(t instanceof HTMLInputElement&&t.id==='a1-size'){prefs.size=Number(t.value);savePrefs();const x=document.getElementById('a1-size-label');if(x)x.textContent=prefs.size+' dp';pushNative();}});
window.addEventListener('fitwave:assistant-emote',(e:Event)=>{const emotion=(e as CustomEvent<{state?:string}>).detail?.state;if(emotion&&emotion!=='thinking')setState(emotion as CharacterState);else if(emotion==='thinking')setState('touch');});
window.addEventListener('fitwave:rest-start',(e:Event)=>{const at=(e as CustomEvent<{endAt:number}>).detail?.endAt;if(at){restEndAt=at;setState('rest');}});
window.addEventListener('fitwave:rest-finished',()=>{restEndAt=0;setState('ready');window.setTimeout(()=>{if(state==='ready')setState('idle');},4000)});
window.addEventListener('fitwave:rest-cancel',()=>{restEndAt=0;setState('idle')});
window.addEventListener('fitwave:session-finished',()=>{restEndAt=0;setState('done');window.setTimeout(()=>{if(prefs.mode==='training'&&running)void stopPet();else setState('idle')},3700)});
window.addEventListener('fitwave:data-saved',()=>pushNative());
const focusObs=new MutationObserver(()=>pushNative());const focusNode=document.getElementById('focus-app');if(focusNode)focusObs.observe(focusNode,{childList:true,subtree:false});
document.addEventListener('visibilitychange',()=>{if(!document.hidden)void syncOnResume();pushNative();});
if(native)void App.addListener('appStateChange',({isActive})=>{if(isActive)void syncOnResume();pushNative();});
document.body.classList.toggle('a1-motion-off',!prefs.animation);repaint('greeting');window.setTimeout(()=>{if(state==='greeting')setState('idle');},2300);void syncOnResume();
console.info('[FitWave V4.1] A1 animated companion, permissions & training bridge ready');
