/**
 * FitWave 3.6 移动端增强层
 * 现有 V3.5 训练业务逻辑保留在 index.html，后续可逐步重构为 TypeScript 模块。
 */
import { Capacitor } from '@capacitor/core';
import { App } from '@capacitor/app';
import { Haptics, ImpactStyle, NotificationType } from '@capacitor/haptics';
import { StatusBar, Style } from '@capacitor/status-bar';

const isNative = Capacitor.isNativePlatform();

async function setupNativeStatusBar() {
  if (!isNative) return;
  try {
    await StatusBar.setStyle({ style: Style.Light }); // 浅色背景下使用深色状态栏文字
    if (Capacitor.getPlatform() === 'android') {
      // Android 16 开始强制 edge-to-edge，这些设置可能不再生效；布局需使用安全区域。
      await StatusBar.setBackgroundColor({ color: '#fff8fb' });
      await StatusBar.setOverlaysWebView({ overlay: false });
    }
  } catch (error) {
    console.warn('[FitWave] 状态栏配置未生效', error);
  }
}

function setupHaptics() {
  document.addEventListener('click', (event) => {
    const target = event.target;
    if (!(target instanceof Element)) return;
    const button = target.closest<HTMLButtonElement>('button');
    if (!button) return;
    if (button.dataset.focusDone || button.dataset.check) {
      if (isNative) void Haptics.impact({ style: ImpactStyle.Light }).catch(() => {});
      else navigator.vibrate?.(15);
    }
    if (button.dataset.confirmFinish) {
      if (isNative) void Haptics.notification({ type: NotificationType.Success }).catch(() => {});
      else navigator.vibrate?.([30, 60, 30]);
    }
  }, { capture: true });
}

let wakeLock: WakeLockSentinel | null = null;
async function setWakeLock(needed: boolean) {
  if (!('wakeLock' in navigator)) return;
  try {
    if (needed && !wakeLock && !document.hidden) wakeLock = await navigator.wakeLock.request('screen');
    if (!needed && wakeLock) { await wakeLock.release(); wakeLock = null; }
  } catch (error) {
    console.info('[FitWave] 此设备暂不支持保持屏幕常亮', error);
  }
}
function setupTrainingWakeLock() {
  const update = () => { void setWakeLock(document.body.classList.contains('focus-open')); };
  new MutationObserver(update).observe(document.body, { attributes: true, attributeFilter: ['class'] });
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) wakeLock = null;
    update();
  });
  update();
}

async function setupAndroidBackButton() {
  if (!isNative || Capacitor.getPlatform() !== 'android') return;
  await App.addListener('backButton', () => {
    if (document.body.classList.contains('focus-open')) {
      document.querySelector<HTMLButtonElement>('[data-focus-close]')?.click();
      return;
    }
    const overlay = document.getElementById('overlay');
    if (overlay && !overlay.classList.contains('hide')) {
      // V3.5 点击遮罩空白区域会关闭弹层
      overlay.click();
      return;
    }
    if (!document.getElementById('page-home')?.classList.contains('active')) {
      document.querySelector<HTMLButtonElement>('.bottomnav [data-go="home"]')?.click();
      return;
    }
    void App.exitApp();
  });
}

// 网站版也能正常运行，所有原生增强均以能力检测为前提。
void setupNativeStatusBar();
setupHaptics();
setupTrainingWakeLock();
void setupAndroidBackButton();

// 渐进式 Web 应用：在 HTTPS / localhost 上缓存资源，以便离线打开。
if ('serviceWorker' in navigator && /^https?:$/.test(location.protocol)) {
  window.addEventListener('load', () => {
    void navigator.serviceWorker.register('/sw.js').catch((error) => {
      console.info('[FitWave] 离线缓存未启用', error);
    });
  });
}
