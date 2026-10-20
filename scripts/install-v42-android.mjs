/** V4.2 native installer, runs after base V4.1 overlay install. Repeat-safe. */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url));
const app=path.join(root,'android/app/src/main');
const activity=path.join(app,'java/com/fitwave/personal/MainActivity.java');
if(!fs.existsSync(activity)){console.error('请先创建 Android 平台，并运行基础原生安装脚本');process.exit(1);}
const source=path.join(root,'native/android/com/fitwave/personal/next');
const target=path.join(app,'java/com/fitwave/personal/next');
fs.mkdirSync(target,{recursive:true});
for(const f of fs.readdirSync(source))if(f.endsWith('.java'))fs.copyFileSync(path.join(source,f),path.join(target,f));
const xmlRoot=path.join(app,'res/xml'),layoutRoot=path.join(app,'res/layout'),drawableRoot=path.join(app,'res/drawable');
for(const p of [xmlRoot,layoutRoot,drawableRoot])fs.mkdirSync(p,{recursive:true});
const put=(file,s)=>fs.writeFileSync(file,s);
put(path.join(xmlRoot,'fitwave_widget_info.xml'),`<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
  android:minWidth="190dp" android:minHeight="110dp"
  android:initialLayout="@layout/fitwave_widget" android:previewLayout="@layout/fitwave_widget"
  android:resizeMode="horizontal|vertical" android:widgetCategory="home_screen"
  android:updatePeriodMillis="1800000" />\n`);
put(path.join(drawableRoot,'fitwave_widget_bg.xml'),`<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
  <solid android:color="#FFF3F8"/><corners android:radius="22dp"/>
  <stroke android:width="1dp" android:color="#F6CADB"/>
</shape>\n`);
put(path.join(layoutRoot,'fitwave_widget.xml'),`<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
  android:id="@+id/fw_widget_root" android:layout_width="match_parent" android:layout_height="match_parent"
  android:orientation="vertical" android:padding="15dp" android:gravity="center_vertical"
  android:background="@drawable/fitwave_widget_bg">
  <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
    android:text="✦ FitWave · 奶霜丝带" android:textSize="11sp" android:textStyle="bold" android:textColor="#C45A88"/>
  <TextView android:id="@+id/fw_widget_title" android:layout_width="match_parent" android:layout_height="wrap_content"
    android:text="今天也要好好训练" android:textSize="18sp" android:textStyle="bold"
    android:textColor="#8E315B" android:layout_marginTop="7dp" android:maxLines="1"/>
  <TextView android:id="@+id/fw_widget_subtitle" android:layout_width="match_parent" android:layout_height="wrap_content"
    android:text="打开 FitWave 查看今日计划" android:textSize="11sp" android:textColor="#966E82"
    android:layout_marginTop="3dp" android:maxLines="1"/>
  <TextView android:id="@+id/fw_widget_progress" android:layout_width="match_parent" android:layout_height="wrap_content"
    android:text="0 / 4 本周训练" android:textSize="12sp" android:textStyle="bold"
    android:textColor="#DA6796" android:layout_marginTop="8dp" />
</LinearLayout>\n`);
let manifest=fs.readFileSync(path.join(app,'AndroidManifest.xml'),'utf8');
const mic='android.permission.RECORD_AUDIO';
if(!manifest.includes(`android:name="${mic}"`))manifest=manifest.replace(/<application\b/,`<uses-permission android:name="${mic}" />\n    <application`);
if(!manifest.includes('FitWaveWidgetProvider'))manifest=manifest.replace('</application>',`<receiver android:name="com.fitwave.personal.next.FitWaveWidgetProvider" android:exported="true">
      <intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE" /></intent-filter>
      <meta-data android:name="android.appwidget.provider" android:resource="@xml/fitwave_widget_info" />
    </receiver>
    </application>`);
if(!manifest.includes('android.speech.RecognitionService')) manifest=manifest.replace(/<application\b/, `<queries><intent><action android:name="android.speech.RecognitionService" /></intent></queries>\n    <application`);
fs.writeFileSync(path.join(app,'AndroidManifest.xml'),manifest);
let a=fs.readFileSync(activity,'utf8');
const classes=['FitWaveStorePlugin','FitWaveVoicePlugin','FitWaveWidgetPlugin'];
for(const cl of classes){const statement=`registerPlugin(com.fitwave.personal.next.${cl}.class);`;
 if(!a.includes(statement)){
  const marker='registerPlugin(com.fitwave.personal.pet.FitWavePetPlugin.class);';
  if(!a.includes(marker))throw new Error('缺失 FitWavePetPlugin 注册，请先运行 V4.1 安装脚本');
  a=a.replace(marker,marker+'\n        '+statement);
 }
}
// Fix Capacitor BridgeActivity public lifecycle overrides introduced in V4.1.
a=a.replace(/@Override\s+protected\s+void\s+onPause\(/g,'@Override public void onPause(')
   .replace(/@Override\s+protected\s+void\s+onResume\(/g,'@Override public void onResume(');
fs.writeFileSync(activity,a);
console.log('[FitWave V4.2] SQLite + voice + widget plugin registered, assets & permissions installed.');
