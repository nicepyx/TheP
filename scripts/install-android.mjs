/** Idempotent native installation AFTER `npx cap add android`. Do not copy Android platform between releases. */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url));
const android=path.join(root,'android');
if(!fs.existsSync(path.join(android,'app/src/main/AndroidManifest.xml'))){console.error('未找到 android 平台；请先运行 npx cap add android');process.exit(1)}
const javaSrc=path.join(root,'native/android/com/fitwave/personal/pet');
const javaDst=path.join(android,'app/src/main/java/com/fitwave/personal/pet');
fs.mkdirSync(javaDst,{recursive:true});
for(const f of fs.readdirSync(javaSrc))fs.copyFileSync(path.join(javaSrc,f),path.join(javaDst,f));
const imgDst=path.join(android,'app/src/main/assets/mascot');
fs.mkdirSync(imgDst,{recursive:true});
for(const f of fs.readdirSync(path.join(root,'public/mascot')))if(f.endsWith('.png'))fs.copyFileSync(path.join(root,'public/mascot',f),path.join(imgDst,f));
const mp=path.join(android,'app/src/main/AndroidManifest.xml');
let manifest=fs.readFileSync(mp,'utf8');
const permissions=['android.permission.SYSTEM_ALERT_WINDOW','android.permission.FOREGROUND_SERVICE','android.permission.FOREGROUND_SERVICE_SPECIAL_USE','android.permission.POST_NOTIFICATIONS'];
for(const p of permissions)if(!manifest.includes(`android:name="${p}"`))manifest=manifest.replace(/<application\b/,`<uses-permission android:name="${p}" />\n    <application`);
if(!manifest.includes('PetOverlayService')){
  const s=`\n        <service android:name="com.fitwave.personal.pet.PetOverlayService" android:enabled="true" android:exported="false" android:stopWithTask="false" android:foregroundServiceType="specialUse">\n            <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="user_enabled_floating_workout_companion" />\n        </service>\n`;
  manifest=manifest.replace(/<\/application>/,s+'    </application>');
}
fs.writeFileSync(mp,manifest);
const activity=path.join(android,'app/src/main/java/com/fitwave/personal/MainActivity.java');
if(!fs.existsSync(activity)) throw new Error(`找不到 ${activity}；平台模板可能已变化，需要人工检查`);
let a=fs.readFileSync(activity,'utf8');
if(!a.includes('FitWavePetPlugin')){
 const c=`\n    @Override\n    public void onCreate(android.os.Bundle savedInstanceState) {\n        registerPlugin(com.fitwave.personal.pet.FitWavePetPlugin.class);\n        super.onCreate(savedInstanceState);\n    }\n    private void setPetForegroundState(boolean inApp) {\n        if (!getSharedPreferences("fitwave_pet", MODE_PRIVATE).getBoolean("running", false)) return;\n        android.content.Intent intent = new android.content.Intent(this,com.fitwave.personal.pet.PetOverlayService.class);\n        intent.setAction(com.fitwave.personal.pet.PetOverlayService.ACTION_VISIBILITY);\n        intent.putExtra("inApp", inApp);\n        try { startService(intent); } catch (Exception ignored) {}\n    }\n    @Override protected void onPause() { setPetForegroundState(false); super.onPause(); }\n    @Override protected void onResume() { super.onResume(); setPetForegroundState(true); }\n`;
 a=a.replace(/\}\s*$/,c+'\n}');
 fs.writeFileSync(activity,a);
}
console.log('FitWave A1 native overlay installed: Java plugin, foreground service, sprites, manifest, MainActivity.');
