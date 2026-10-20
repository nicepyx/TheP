/** FitWave 4.2 — SQLite mirror, private opt-in sync, push-to-talk and companion assistant. */
import { Capacitor, registerPlugin } from '@capacitor/core';
import { emote } from './live2d';
import './v42.css';
const KEY='forge_training_v33_2026';
const ANDROID=Capacitor.isNativePlatform()&&Capacitor.getPlatform()==='android';
const Store=registerPlugin<{
  status():Promise<{ready:boolean;hasData:boolean;snapshots:number;bytes:number}>;
  read():Promise<{payload:string}>;
  write(v:{payload:string;snapshot?:boolean;reason?:string}):Promise<void>;
  checkpoints():Promise<{items:Array<{id:number;reason:string;created:number}>}>;
  restore(v:{id:number}):Promise<{payload:string}>;
}>('FitWaveStore');
const Voice=registerPlugin<{listen():Promise<{text:string}>;speak(v:{text:string}):Promise<void>;stop():Promise<void>}>('FitWaveVoice');
const Widget=registerPlugin<{update(v:{title:string;detail:string;done:number;total:number}):Promise<void>}>('FitWaveWidget');

type Training={sessions:Record<string,{completedAt?:string|number|null;sets?:Record<string,unknown[]>}>;cardio:Record<string,unknown>;body:unknown[];settings?:unknown};
const valid=(s:string)=>{try{let d=JSON.parse(s);return d&&typeof d.sessions==='object'&&d.sessions!==null&&typeof d.cardio==='object'&&d.cardio!==null&&Array.isArray(d.body);}catch{return false;}};
function local(){try{return localStorage.getItem(KEY)||'';}catch{return '';}}
function putLocal(payload:string){if(!valid(payload))throw new Error('训练数据格式错误');localStorage.setItem(KEY,payload);}
function toast(message:string){const x=document.getElementById('toast');if(x){x.textContent=message;x.classList.add('show');setTimeout(()=>x.classList.remove('show'),3400);}else console.info(message);}
const appState=()=>{try{return JSON.parse(local()) as Training;}catch{return null;}};
const today=()=>{const d=new Date();return [d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-');};
function progress(){const d=appState();const now=new Date();const weekday=(now.getDay()+6)%7;const mon=new Date(now.getFullYear(),now.getMonth(),now.getDate()-weekday,12);let done=0;for(const [k,s] of Object.entries(d?.sessions||{})){const date=k.split('__')[0];const dd=new Date(date+'T12:00:00');if(dd>=mon&&dd<new Date(mon.getTime()+7*86400000)&&s?.completedAt)done++;}
  const day=now.getDay();const name=day===1?'胸部训练':day===2?'背部训练':day===4?'肩部训练':day===6?'腿部训练':day===3?'快走30分钟':day===5?'恢复日':'休息与散步';
  return {done:Math.min(4,done),total:4,title:name,detail:`${today()} · ${day===2||day===4?'晨起爬楼10–15层':'把今天过好就很棒'}`};
}
let lastNativePayload='';let pendingWrite:number|undefined;
async function flushNative(payload:string,snapshot=false,reason='自动镜像'){if(!ANDROID||!valid(payload))return;
 try{await Store.write({payload,snapshot,reason});lastNativePayload=payload;}catch(e){console.warn('[FitWave SQLite]',e);toast('SQLite 保存失败，请及时导出备份');}}
function scheduleMirror(s:string){if(!ANDROID||!valid(s))return;clearTimeout(pendingWrite);pendingWrite=window.setTimeout(()=>{void flushNative(s);},400);}
async function initSqlite(){if(!ANDROID)return;
 try {const {payload}=await Store.read();const l=local();if(!valid(l)&&valid(payload)){putLocal(payload);location.reload();return;}
  if(valid(l)){await flushNative(l,!valid(payload),'从 V4.1 导入初始数据');}
  console.info('[FitWave] Native SQLite ready');
 }catch(e){console.warn('SQLite 尚未可用',e);toast('原生 SQLite 初始化失败，网页备份仍可使用');}
}
async function updateWidget(){if(!ANDROID)return;try{await Widget.update(progress());}catch(e){console.info('[FitWave widget]',e);}}

// Supabase project URL and anon/publishable key are public application config; no service key belongs in an APK.
const SYNC_CONFIG='fitwave:v42:cloud';
type CloudConfig={url:string;anonKey:string;auto:boolean};
function config():CloudConfig{try{const p=JSON.parse(localStorage.getItem(SYNC_CONFIG)||'{}');return {url:String(p.url||''),anonKey:String(p.anonKey||''),auto:p.auto===true};}catch{return {url:'',anonKey:'',auto:false};}}
let conf=config();
let tokens:{access:string;refresh:string;uid:string}|null=null;
function cloudUrl(path:string){if(!/^https:\/\/[\w.-]+(?:\:\d+)?\/?$/.test(conf.url))throw new Error('云服务地址必须是 HTTPS 项目根地址');if(!conf.anonKey)throw new Error('请填写 Supabase publishable/anon key');return conf.url.replace(/\/$/,'')+path;}
async function request(path:string,init:RequestInit={},auth=false){const url=cloudUrl(path);const headers=new Headers(init.headers);
  headers.set('apikey',conf.anonKey);headers.set('Content-Type','application/json');if(auth){if(!tokens)throw new Error('请先登录');headers.set('Authorization',`Bearer ${tokens.access}`);}
  const response=await fetch(url,{...init,headers,cache:'no-store'});
  if(!response.ok){const body=(await response.text()).slice(0,260);throw new Error(`云端请求失败 ${response.status}: ${body}`);}return response.status===204?null:response.json();
}
async function signIn(email:string,password:string,signUp=false){if(!email.includes('@')||password.length<6)throw new Error('请填写有效邮箱与至少6位密码');const endpoint=signUp?'/auth/v1/signup':'/auth/v1/token?grant_type=password';
 const r=await request(endpoint,{method:'POST',body:JSON.stringify({email,password})});
 if(signUp&&!r.access_token)return '注册申请已提交，请先到邮箱确认账号，再登录';
 if(!r.access_token||!r.user?.id)throw new Error('登录失败，请检查邮箱验证状态');
 tokens={access:r.access_token,refresh:r.refresh_token||'',uid:r.user.id};return '云账户已登录；尚未自动上传任何数据';}
async function refresh(){if(!tokens?.refresh)return false;try{const r=await request('/auth/v1/token?grant_type=refresh_token',{method:'POST',body:JSON.stringify({refresh_token:tokens.refresh})});tokens={access:r.access_token,refresh:r.refresh_token,uid:r.user.id};return true;}catch{return false;}}
async function cloudBackup(automatic=false){if(!tokens)throw new Error('请先登录');const payload=local();if(!valid(payload))throw new Error('本地训练数据为空或格式异常');
 // This is a client-side backup, not end-to-end encryption. Supabase RLS confines rows to their owners.
 const body={user_id:tokens.uid,payload:JSON.parse(payload),schema_version:1};
 const versionKey=`fitwave:v42:remote:${tokens.uid}`;
 const rows=await request(`/rest/v1/fitwave_backups?select=updated_at&user_id=eq.${encodeURIComponent(tokens.uid)}`,{},true) as Array<{updated_at:string}>;
 const remoteVersion=rows[0]?.updated_at||'';
 const lastSeen=localStorage.getItem(versionKey)||'';
 if(remoteVersion&&remoteVersion!==lastSeen){
   if(automatic)throw new Error('云端存在另一份记录，自动上传已暂停以避免覆盖；请先手动处理');
   if(!confirm(`云端已有更新时间为 ${new Date(remoteVersion).toLocaleString('zh-CN')} 的备份。\n上传将覆盖它；请确认你已经检查了另一台设备的记录。继续上传吗？`))return '已取消上传';
 }
 try {await request('/rest/v1/fitwave_backups?on_conflict=user_id',{method:'POST',headers:{Prefer:'resolution=merge-duplicates,return=minimal'},body:JSON.stringify(body)},true);}
 catch(e){if(await refresh())await request('/rest/v1/fitwave_backups?on_conflict=user_id',{method:'POST',headers:{Prefer:'resolution=merge-duplicates,return=minimal'},body:JSON.stringify(body)},true);else throw e;}
 const after=await request(`/rest/v1/fitwave_backups?select=updated_at&user_id=eq.${encodeURIComponent(tokens.uid)}`,{},true) as Array<{updated_at:string}>;
 if(after[0]?.updated_at)localStorage.setItem(versionKey,after[0].updated_at);
 return `已上传云端备份（${new Date().toLocaleString('zh-CN')}）`;
}
async function cloudRestore(){if(!tokens)throw new Error('请先登录');let a:Array<{payload:Training;updated_at:string}>;
 try{a=await request(`/rest/v1/fitwave_backups?select=payload,updated_at&user_id=eq.${encodeURIComponent(tokens.uid)}`,{},true);}catch(e){if(await refresh())a=await request(`/rest/v1/fitwave_backups?select=payload,updated_at&user_id=eq.${encodeURIComponent(tokens!.uid)}`,{},true);else throw e;}
 const remote=a[0];if(!remote)throw new Error('该账户尚无云端备份');const s=JSON.stringify(remote.payload);if(!valid(s))throw new Error('云端记录格式异常');
 if(!confirm(`云端更新于 ${new Date(remote.updated_at).toLocaleString('zh-CN')}。\n下载会覆盖本机当前训练记录，且可能丢失未上传的新数据。继续吗？`))return '已取消';
 if(tokens)localStorage.setItem(`fitwave:v42:remote:${tokens.uid}`,remote.updated_at);
 if(ANDROID&&valid(local()))await flushNative(local(),true,'云端覆盖前备份');putLocal(s);await flushNative(s,true,'云端恢复');location.reload();return '已恢复';
}
let autoHandle:number|undefined;
function queueAutoCloud(){if(!conf.auto||!tokens)return;clearTimeout(autoHandle);autoHandle=window.setTimeout(()=>{void cloudBackup(true).catch(e=>console.info('[FitWave] 自动备份暂不可用',e));},8000);}

const speechReply=(question:string):string|null=>{
  const q=question.replace(/\s+/g,'');const p=progress();
  if(/(今天|今日).*?(练什么|训练|安排)/.test(q))return `今天的安排是${p.title}。${p.detail}。`;
  if(/(训练|本周).*?(进度|完成|几次)/.test(q))return `本周已经完成 ${p.done} 次力量训练，目标 4 次。`;
  if(/(体重|腰围).*?(多少|最近|记录)/.test(q)){const b=appState()?.body||[];const last=b.at(-1) as {weight?:number;waist?:number}|undefined;return last?`最近记录的体重是 ${last.weight??'未知'} 公斤。`:'还没有体重记录。';}
  if(/打开.*训练|进入.*训练/.test(q)){document.querySelector<HTMLButtonElement>('.bottomnav [data-go="train"]')?.click();return '已打开训练记录页面。';}
  if(/休息|倒计时/.test(q)){return '完成一组后会自动开始休息倒计时，可以用悬浮精灵查看剩余时间。';}
  return null;
};
async function askAssistant(q:string){emote('thinking');let answer=speechReply(q);
 if(!answer&&tokens){try{const r=await request('/functions/v1/fitwave-ai',{method:'POST',body:JSON.stringify({message:q})},true);answer=String(r.answer||'').slice(0,1000);}catch(e){answer=`AI 服务暂时不可用：${(e as Error).message}`;}}
 if(!answer)answer='我可以查看今天的训练安排、本周进度和最近体重，也可以打开训练页面。要使用开放式 AI 对话，请先在设置中连接自己的 Supabase 账号并部署 AI 服务。';
 emote(/完成|很棒|成功/.test(answer)?'cheer':'touch');return answer;
}
function element(id:string){return document.getElementById(id) as HTMLElement|null;}
let modal:HTMLElement|null=null;
function setupUI(){if(element('fw42-launch'))return;
 const trigger=document.createElement('button');trigger.id='fw42-launch';trigger.className='fw42-launch';trigger.textContent='✦';trigger.title='A1 语音与数据中心';trigger.setAttribute('aria-label','打开 A1 助手与数据中心');document.body.appendChild(trigger);
 const shell=document.createElement('div');shell.className='fw42-sheet';shell.id='fw42-sheet';shell.hidden=true;shell.innerHTML=`<div class="fw42-body" role="dialog" aria-modal="true" aria-label="A1 助手与数据中心">
   <div class="fw42-header"><div><span class="fw42-kicker">FITWAVE V4.2 · MILK RIBBON</span><h2>小澪 · 训练与数据中心</h2></div><button data-fw42="close" aria-label="关闭">关闭 ×</button></div>
   <p class="fw42-sub">角色会根据训练状态变化。语音只在你主动点击时启用；联网 AI 和云同步默认关闭。</p>
   <div class="fw42-card"><b>角色语音助手</b><div class="fw42-actions"><button class="fw42-primary" data-fw42="listen">按下说话</button><button data-fw42="speak">播报回答</button><button data-fw42="stopvoice">停止语音</button></div>
     <label for="fw42-question">对小澪说</label><textarea id="fw42-question" placeholder="例如：今天练什么？ / 我这周完成了几次训练？"></textarea><div class="fw42-actions"><button class="fw42-primary" data-fw42="ask">发送消息</button></div>
     <div class="fw42-response" id="fw42-answer">你好，我是小澪。今天的训练也一起加油吧。</div><div class="fw42-status" id="fw42-voice-status">语音需要 Android 系统提供识别和播报服务。</div>
   </div>
   <div class="fw42-card"><b>原生 SQLite 数据保险箱</b><div class="fw42-actions"><button data-fw42="sqlite-status">查看数据库</button><button data-fw42="sqlite-snapshot">创建恢复点</button><button data-fw42="sqlite-restore">恢复历史记录</button></div>
     <div id="fw42-db-status" class="fw42-status">正在检查原生数据库…</div></div>
   <div class="fw42-card"><b>云端同步（需要自己的 Supabase 项目）</b>
     <label for="fw42-url">HTTPS Supabase Project URL</label><input id="fw42-url" placeholder="https://YOUR-PROJECT.supabase.co" autocomplete="url">
     <label for="fw42-anon">Publishable / anon key（公钥，不是 service_role）</label><input id="fw42-anon" autocomplete="off" placeholder="sb_publishable_... 或 anon key">
     <div class="fw42-actions"><button data-fw42="saveconfig">保存项目配置</button></div>
     <label for="fw42-email">邮箱</label><input id="fw42-email" type="email" autocomplete="username" placeholder="you@example.com">
     <label for="fw42-password">密码（不会存入本地文件）</label><input id="fw42-password" type="password" autocomplete="current-password">
     <div class="fw42-actions"><button data-fw42="signup">注册</button><button data-fw42="login">登录</button><button data-fw42="logout">退出</button></div>
     <div class="fw42-actions"><button class="fw42-primary" data-fw42="upload">上传本地备份</button><button data-fw42="download">从云端恢复</button></div>
     <label><input id="fw42-auto" type="checkbox" style="width:auto"> 登录期间，保存记录后自动上传备份</label>
     <div class="fw42-status" id="fw42-cloud-status">未连接云端。不影响离线训练。</div><small>覆盖恢复必须确认；训练数据不会在未经启用时自动上传。</small>
   </div>
   <div class="fw42-card"><b>Android 桌面小组件</b><p class="fw42-sub">长按小米手机桌面空白处 → 添加小组件 → 找到 FitWave。组件显示本周训练数与当日计划。</p><button data-fw42="widget">立即刷新小组件</button></div>
   <div class="fw42-card"><b>Live2D 模型状态</b><p class="fw42-sub">已预留 Cubism 角色模型和状态驱动接口；当前运行的是 A1 多表情轻动画。完整 Live2D 需分层 .moc3 模型及合规运行时资源。</p><button data-fw42="emote">预览角色动作</button></div>
 </div>`;
 document.body.appendChild(shell);modal=shell;
 trigger.addEventListener('click',()=>openPanel());shell.addEventListener('click',e=>{if(e.target===shell)closePanel();});
 shell.querySelectorAll<HTMLButtonElement>('button[data-fw42]').forEach(b=>b.addEventListener('click',()=>{void action(b.dataset.fw42||'')}));
 element('fw42-auto')!.addEventListener('change',()=>{conf.auto=(element('fw42-auto') as HTMLInputElement).checked;localStorage.setItem(SYNC_CONFIG,JSON.stringify(conf));});
}
function openPanel(){const e=modal;if(!e)return;e.hidden=false;(element('fw42-url') as HTMLInputElement).value=conf.url;(element('fw42-anon') as HTMLInputElement).value=conf.anonKey;(element('fw42-auto') as HTMLInputElement).checked=conf.auto;void showSqliteStatus();}
function closePanel(){if(modal)modal.hidden=true;}
const value=(id:string)=>((element(id) as HTMLInputElement|null)?.value||'').trim();
const setText=(id:string,s:string)=>{const x=element(id);if(x)x.textContent=s;};
let lastAnswer='你好，我是小澪。';
let emotionIndex=0;const emoList=['idle','greeting','cheer','rest','ready','done','touch','thinking'] as const;
async function showSqliteStatus(){if(!ANDROID){setText('fw42-db-status','Web 预览模式：原生 SQLite 仅在 Android APK 中可用');return;}try{const r=await Store.status();setText('fw42-db-status',`数据库：${r.ready?'正常':'不可用'}\n训练主数据：${r.hasData?'已保存':'尚无'}\n大小：${Math.round(r.bytes/1024)} KB · 恢复点：${r.snapshots}`);}catch(e){setText('fw42-db-status',`SQLite 查询失败：${(e as Error).message}`);}}
async function action(id:string){try{
 if(id==='close'){closePanel();return;}
 if(id==='listen'){setText('fw42-voice-status','正在听，请清楚说出训练问题…');emote('thinking');const r=await Voice.listen();(element('fw42-question') as HTMLTextAreaElement).value=r.text;setText('fw42-voice-status',`识别到：${r.text}`);if(r.text){lastAnswer=await askAssistant(r.text);setText('fw42-answer',lastAnswer);}return;}
 if(id==='speak'){if(!ANDROID)throw new Error('请在 Android App 上使用语音播报');await Voice.speak({text:lastAnswer});return;}
 if(id==='stopvoice'){if(ANDROID)await Voice.stop();setText('fw42-voice-status','语音已停止');return;}
 if(id==='ask'){const q=value('fw42-question');if(!q)throw new Error('请输入问题');setText('fw42-answer','小澪正在思考…');lastAnswer=await askAssistant(q);setText('fw42-answer',lastAnswer);return;}
 if(id==='sqlite-status'){await showSqliteStatus();return;}
 if(id==='sqlite-snapshot'){if(!ANDROID)throw new Error('SQLite 只在 Android App 中可用');await flushNative(local(),true,'手动保存');await showSqliteStatus();toast('SQLite 恢复点已创建');return;}
 if(id==='sqlite-restore'){if(!ANDROID)throw new Error('请在 Android App 中恢复');const r=await Store.checkpoints();if(!r.items.length)throw new Error('尚无恢复点');const message=r.items.slice(0,12).map((x,i)=>`${i+1}. ${new Date(x.created).toLocaleString('zh-CN')} · ${x.reason}`).join('\n');const input=prompt('选择要恢复的序号（将覆盖当前训练数据）：\n'+message);if(!input)return;const index=Number(input)-1;const selection=r.items[index];if(!selection)throw new Error('无效序号');if(!confirm('确定覆盖当前训练数据吗？原数据会先生成 SQLite 恢复点。'))return;const x=await Store.restore({id:selection.id});putLocal(x.payload);location.reload();return;}
 if(id==='saveconfig'){conf={url:value('fw42-url'),anonKey:value('fw42-anon'),auto:(element('fw42-auto') as HTMLInputElement).checked};cloudUrl('/');localStorage.setItem(SYNC_CONFIG,JSON.stringify(conf));tokens=null;setText('fw42-cloud-status','配置已保存。请登录后使用备份。');return;}
 if(id==='login'||id==='signup'){const email=value('fw42-email'),password=value('fw42-password');const res=await signIn(email,password,id==='signup');(element('fw42-password') as HTMLInputElement).value='';setText('fw42-cloud-status',res);return;}
 if(id==='logout'){tokens=null;setText('fw42-cloud-status','已退出；本地训练记录保留');return;}
 if(id==='upload'){setText('fw42-cloud-status','正在上传…');const r=await cloudBackup();setText('fw42-cloud-status',r);return;}
 if(id==='download'){setText('fw42-cloud-status','正在检查云端备份…');const r=await cloudRestore();setText('fw42-cloud-status',r);return;}
 if(id==='widget'){await updateWidget();toast('已刷新桌面小组件');return;}
 if(id==='emote'){emote(emoList[emotionIndex++%emoList.length]);toast('正在切换 A1 角色状态');return;}
 }catch(e){const message=(e as Error).message||String(e);toast(message);if(id==='listen')setText('fw42-voice-status',message);else if(['login','signup','upload','download','saveconfig'].includes(id))setText('fw42-cloud-status',message);else if(id.startsWith('sqlite'))setText('fw42-db-status',message);console.warn('[FitWave 4.2]',e);}}

window.addEventListener('fitwave:data-saved',(e:Event)=>{const s=(e as CustomEvent<{data?:string}>).detail?.data||local();scheduleMirror(s);void updateWidget();queueAutoCloud();});
window.addEventListener('fitwave:session-finished',()=>{void flushNative(local(),true,'训练完成');void updateWidget();queueAutoCloud();});
document.addEventListener('visibilitychange',()=>{if(document.hidden){clearTimeout(pendingWrite);const s=local();if(s!==lastNativePayload)void flushNative(s);}else void updateWidget();});
window.addEventListener('fitwave:assistant-emote',(e:Event)=>{const state=(e as CustomEvent<{state?:string}>).detail?.state; if(state==='thinking'){/* render to UI through CSS */}});
setupUI();void initSqlite();void updateWidget();
console.info('[FitWave V4.2] Android SQLite, widget, voice and optional private cloud interface ready');
