/** Run without npm dependencies: syntax checks, asset checks and repeatable Android integration fixture. */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../', import.meta.url));
const get = p => fs.readFileSync(path.join(root, p), 'utf8');
const pkg=JSON.parse(get('package.json'));
assert.equal(pkg.version, '4.2.0');
assert.match(get('capacitor.config.ts'), /appId:\s*'com\.fitwave\.personal'/);
for(const f of ['a1_idle.png','a1_cheer.png','a1_rest.png','a1_done.png','a1_hero.webp']) {
  assert.ok(fs.statSync(path.join(root,'public/mascot',f)).size>2000, `缺失角色素材: ${f}`);
}
for(const f of ['src/pet.ts','src/v4.ts','native/android/com/fitwave/personal/pet/FitWavePetPlugin.java','native/android/com/fitwave/personal/pet/PetOverlayService.java']) {
  assert.ok(fs.statSync(path.join(root,f)).size>500,`源码缺失: ${f}`);
}
assert.match(get('src/pet.ts'), /Pet\.show\(\)/);
assert.match(get('src/pet.ts'), /fitwave:rest-start/);
assert.match(get('native/android/com/fitwave/personal/pet/PetOverlayService.java'), /startForeground\(/);
const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'fitwave-overlay-smoke-'));
try {
  for(const d of ['scripts','native/android/com/fitwave/personal/pet','public/mascot','android/app/src/main/java/com/fitwave/personal']) fs.mkdirSync(path.join(tmp,d),{recursive:true});
  fs.copyFileSync(path.join(root,'scripts/install-android.mjs'),path.join(tmp,'scripts/install-android.mjs'));
  fs.cpSync(path.join(root,'native/android/com/fitwave/personal/pet'),path.join(tmp,'native/android/com/fitwave/personal/pet'),{recursive:true});
  fs.cpSync(path.join(root,'public/mascot'),path.join(tmp,'public/mascot'),{recursive:true});
  const manifest=path.join(tmp,'android/app/src/main/AndroidManifest.xml');
  const main=path.join(tmp,'android/app/src/main/java/com/fitwave/personal/MainActivity.java');
  fs.writeFileSync(manifest,'<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:label="FitWave"></application></manifest>');
  fs.writeFileSync(main,'package com.fitwave.personal;\npublic class MainActivity extends com.getcapacitor.BridgeActivity { }');
  for(let i=0;i<2;i++) execFileSync(process.execPath,[path.join(tmp,'scripts/install-android.mjs')],{stdio:'pipe'});
  const m=fs.readFileSync(manifest,'utf8');
  const a=fs.readFileSync(main,'utf8');
  assert.equal((m.match(/com\.fitwave\.personal\.pet\.PetOverlayService/g)||[]).length,1);
  assert.equal((m.match(/android\.permission\.POST_NOTIFICATIONS/g)||[]).length,1);
  assert.equal((m.match(/android\.permission\.SYSTEM_ALERT_WINDOW/g)||[]).length,1);
  assert.equal((a.match(/registerPlugin\(com\.fitwave\.personal\.pet\.FitWavePetPlugin\.class\)/g)||[]).length,1);
  assert.ok(fs.existsSync(path.join(tmp,'android/app/src/main/assets/mascot/a1_idle.png')));
  assert.ok(fs.existsSync(path.join(tmp,'android/app/src/main/java/com/fitwave/personal/pet/PetOverlayService.java')));
  console.log('[FitWave smoke] PASS — 原生 Java、A1 素材、Manifest、重复安装与入口注册均已检查');
} finally { fs.rmSync(tmp,{recursive:true,force:true}); }
