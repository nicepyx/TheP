/**
 * FitWave 4.0: offline vault + optional Android local rest notifications.
 * Business state remains the V3.x JSON payload; no remote transfer is performed.
 */
import { Capacitor } from '@capacitor/core';
import { LocalNotifications } from '@capacitor/local-notifications';

const LEGACY_KEY = 'forge_training_v33_2026';
const DB_NAME = 'fitwave-v4-vault';
const DB_VERSION = 1;
const NOTIFICATION_ID = 400101;
const isNative = Capacitor.isNativePlatform();

type Snapshot = { id: string; at: number; reason: string; payload: string };
type StoreRow = { id: string; at: number; payload: string };

const validPayload = (s: string): boolean => {
  try {
    const d: unknown = JSON.parse(s);
    if (!d || typeof d !== 'object' || Array.isArray(d)) return false;
    const o = d as Record<string, unknown>;
    return !!o.sessions && typeof o.sessions === 'object' && !!o.cardio && typeof o.cardio === 'object' && Array.isArray(o.body);
  } catch { return false; }
};

function openDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (!('indexedDB' in window)) { reject(new Error('IndexedDB unavailable')); return; }
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains('meta')) db.createObjectStore('meta', { keyPath: 'id' });
      if (!db.objectStoreNames.contains('snapshots')) db.createObjectStore('snapshots', { keyPath: 'id' });
    };
    req.onerror = () => reject(req.error);
    req.onsuccess = () => resolve(req.result);
  });
}
function idbGet<T>(db: IDBDatabase, store: string, key: string): Promise<T | undefined> {
  return new Promise((resolve, reject) => {
    const request = db.transaction(store, 'readonly').objectStore(store).get(key);
    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve(request.result as T | undefined);
  });
}
function idbAll<T>(db: IDBDatabase, store: string): Promise<T[]> {
  return new Promise((resolve, reject) => {
    const request = db.transaction(store, 'readonly').objectStore(store).getAll();
    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve(request.result as T[]);
  });
}
function idbPut(db: IDBDatabase, store: string, record: StoreRow | Snapshot): Promise<void> {
  return new Promise((resolve, reject) => {
    const tx = db.transaction(store, 'readwrite');
    tx.objectStore(store).put(record);
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
    tx.oncomplete = () => resolve();
  });
}
function idbDelete(db: IDBDatabase, store: string, id: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const tx = db.transaction(store, 'readwrite');
    tx.objectStore(store).delete(id);
    tx.onerror = () => reject(tx.error);
    tx.oncomplete = () => resolve();
  });
}

let database: IDBDatabase | null = null;
let lastPayload = '';
let pendingWrite: number | undefined;
let vaultReady = false;

function showToast(message: string) {
  const t = document.getElementById('toast');
  if (t) { t.textContent = message; t.classList.add('show'); window.setTimeout(() => t.classList.remove('show'), 3000); }
}
function getJson(): string { try { return localStorage.getItem(LEGACY_KEY) || ''; } catch { return ''; } }

async function updateCurrent(payload: string) {
  if (!database || !validPayload(payload)) return;
  await idbPut(database, 'meta', { id: 'current', at: Date.now(), payload });
  lastPayload = payload;
}
function queueWrite(payload: string) {
  if (!validPayload(payload)) return;
  lastPayload = payload;
  window.clearTimeout(pendingWrite);
  pendingWrite = window.setTimeout(() => {
    void updateCurrent(lastPayload).catch(err => console.warn('[FitWave Vault] write failed', err));
  }, 500);
}

async function saveSnapshot(reason: string): Promise<boolean> {
  if (!database) return false;
  window.clearTimeout(pendingWrite);
  const payload = getJson();
  if (!validPayload(payload)) return false;
  const at = Date.now();
  await updateCurrent(payload);
  // Unique ID even when several checkpoints are created in one millisecond.
  const id = `${at}-${Math.random().toString(36).slice(2, 7)}`;
  await idbPut(database, 'snapshots', { id, at, payload, reason });
  const all = (await idbAll<Snapshot>(database, 'snapshots')).sort((a,b) => b.at - a.at);
  for (const old of all.slice(12)) await idbDelete(database, 'snapshots', old.id);
  return true;
}

async function setupVault() {
  try {
    database = await openDB();
    const current = await idbGet<StoreRow>(database, 'meta', 'current');
    const initial = getJson();
    // Backup restoration is automatic only if the old localStorage was missing *before*
    // the legacy app booted; this avoids overwriting an intentional reset/clear action.
    const startHadData = (window as typeof window & { __FW_STARTED_WITH_DATA__?: boolean }).__FW_STARTED_WITH_DATA__;
    if (!startHadData && current?.payload && validPayload(current.payload)) {
      localStorage.setItem(LEGACY_KEY, current.payload);
      location.reload();
      return;
    }
    if (validPayload(initial)) await updateCurrent(initial);
    vaultReady = true;
    window.addEventListener('fitwave:data-saved', (event: Event) => {
      const detail = (event as CustomEvent<{data: string}>).detail;
      queueWrite(detail?.data ?? getJson());
    });
    window.addEventListener('fitwave:session-finished', () => {
      void saveSnapshot('训练完成').catch(e => console.warn('[FitWave Vault] snapshot failed', e));
    });
    document.addEventListener('visibilitychange', () => {
      if (document.hidden && lastPayload) {
        window.clearTimeout(pendingWrite);
        void updateCurrent(lastPayload).catch(() => {});
      }
    });
  } catch (e) {
    console.warn('[FitWave Vault] unavailable', e);
    vaultReady = false;
  }
}

function renderDialog(title: string, content: string) {
  const overlay = document.getElementById('overlay');
  const modal = document.getElementById('modal');
  if (!overlay || !modal) return;
  modal.innerHTML = `<div class="eyebrow">FITWAVE 4.1 / LOCAL VAULT</div><h2>${title}</h2>${content}<div class="modal-actions"><button class="secondary" data-close-modal="1">关闭</button></div>`;
  overlay.classList.remove('hide');
  document.body.style.overflow = 'hidden';
}
async function renderVaultStatus() {
  if (!database) { renderDialog('数据保险箱暂不可用', '<p>此设备未能启用 IndexedDB。请先导出 JSON 备份，不要卸载应用。</p>'); return; }
  const all = await idbAll<Snapshot>(database, 'snapshots');
  const current = await idbGet<StoreRow>(database, 'meta', 'current');
  renderDialog('数据保险箱状态', `<p>本机 IndexedDB 数据镜像：<b>${current && validPayload(current.payload) ? '正常' : '无数据'}</b></p><p>已建立的恢复点：<b>${all.length}/12</b></p><p class="subtle-info">保存于当前应用数据目录，不上传到服务器。卸载或清除应用数据会删除这些备份，请另外导出 JSON 文件。</p>`);
}
async function showSnapshots() {
  if (!database) { await renderVaultStatus(); return; }
  const all = (await idbAll<Snapshot>(database, 'snapshots')).sort((a,b)=>b.at-a.at);
  const rows = all.map(s => `<div class="record-row"><div><b>${new Date(s.at).toLocaleString('zh-CN')}</b><p>${s.reason}</p></div><button class="secondary" data-vault-restore="${s.id}" style="padding:8px 11px">恢复</button></div>`).join('');
  renderDialog('历史恢复点', `<p>最多保留最近12个恢复点；恢复会覆盖当前页面的训练数据。</p><div class="card-list">${rows || '<p>尚无恢复点。可以在设置中手动建立。</p>'}</div>`);
}
async function restoreSnapshot(id: string) {
  if (!database) return;
  const saved = await idbGet<Snapshot>(database,'snapshots',id);
  if (!saved || !validPayload(saved.payload)) { showToast('恢复点损坏或不存在'); return; }
  const okay = window.confirm(`将恢复 ${new Date(saved.at).toLocaleString('zh-CN')} 的训练数据。当前数据会被覆盖；是否继续？`);
  if (!okay) return;
  // Preserve the user's latest data for potential rollback.
  await saveSnapshot('恢复前自动备份');
  localStorage.setItem(LEGACY_KEY, saved.payload);
  await updateCurrent(saved.payload);
  location.reload();
}

let notificationEnabled = false;
try { notificationEnabled = localStorage.getItem('fitwave:v4:notif') === 'on'; } catch { /* localStorage denied; no notifications by default */ }
async function requestNotificationPermission(): Promise<boolean> {
  if (!isNative) { showToast('通知功能需要安装安卓 App；浏览器可使用页面内倒计时'); return false; }
  try {
    let p = await LocalNotifications.checkPermissions();
    if (p.display !== 'granted') p = await LocalNotifications.requestPermissions();
    notificationEnabled = p.display === 'granted';
    localStorage.setItem('fitwave:v4:notif', notificationEnabled ? 'on' : 'off');
    showToast(notificationEnabled ? '系统通知已启用' : '未取得系统通知权限');
    return notificationEnabled;
  } catch (e) {
    console.warn('[FitWave] local notifications permission', e);
    showToast('暂时无法启用通知，请检查系统设置');
    return false;
  }
}
async function cancelRestNotification() {
  if (!isNative) return;
  try { await LocalNotifications.cancel({notifications:[{id:NOTIFICATION_ID}]}); } catch (e) { console.info('[FitWave] cancel notification', e); }
}
async function scheduleRest(at: number, isTest=false) {
  if (!isNative || !notificationEnabled) return;
  if (!isTest && (window as Window & {__FW_PET_OVERLAY_VISIBLE__?:boolean}).__FW_PET_OVERLAY_VISIBLE__) return; // foreground pet service owns the rest alert; avoid duplicate system notifications
  if (!Number.isFinite(at) || at < Date.now()+1500) return;
  try {
    if (!isTest) await cancelRestNotification();
    await LocalNotifications.schedule({ notifications: [{
      id: isTest ? NOTIFICATION_ID + 1 : NOTIFICATION_ID,
      title: isTest ? '小澪的提醒测试' : '小澪提醒：休息结束',
      body: isTest ? '通知功能可以正常工作。' : '可以准备下一组了，记得保持动作质量。',
      schedule: { at: new Date(at) }, // OS-level notification, precision varies by OEM power-saving settings
    }] });
  } catch (e) { console.warn('[FitWave] local notification schedule failed', e); showToast('系统提醒未能设置，仍可使用页面内计时'); }
}

window.addEventListener('fitwave:rest-start', e => {
  const endAt = (e as CustomEvent<{endAt: number}>).detail?.endAt;
  if (endAt) void scheduleRest(endAt);
});
window.addEventListener('fitwave:rest-cancel', () => void cancelRestNotification());
window.addEventListener('fitwave:rest-finished', () => void cancelRestNotification());

// One native-aware listener handles the additional control surface; existing V3.x
// click handling continues to control the original training interface.
document.addEventListener('click', (e) => {
  const target = e.target;
  if (!(target instanceof Element)) return;
  const b = target.closest<HTMLButtonElement>('button');
  if (!b) return;
  if (b.dataset.vaultBackup) {
    void saveSnapshot('手动备份').then(ok=>showToast(ok?'已建立本地恢复点':'数据库不可用，请导出JSON')).catch(()=>showToast('建立恢复点失败'));
  }
  if (b.dataset.vaultList) void showSnapshots().catch(()=>showToast('无法读取恢复点'));
  if (b.dataset.vaultStatus) void renderVaultStatus().catch(()=>showToast('无法读取保险箱状态'));
  if (b.dataset.vaultRestore) void restoreSnapshot(b.dataset.vaultRestore).catch(()=>showToast('恢复失败'));
  if (b.dataset.v4Notify) void requestNotificationPermission();
  if (b.dataset.v4NotifyTest) {
    void (async () => {
      if (!notificationEnabled && !await requestNotificationPermission()) return;
      await scheduleRest(Date.now()+10000,true);
      showToast('已安排10秒后的测试通知，请锁屏等待');
    })();
  }
});

void setupVault();
console.info('[FitWave 4.0] storage vault initialization and native notification integration enabled');
