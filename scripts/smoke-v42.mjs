import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import ts from 'typescript';
const root=fileURLToPath(new URL('../',import.meta.url));
const required=[
 'src/v42.ts','src/v42.css','src/live2d.ts',
 'native/android/com/fitwave/personal/next/FitWaveStorePlugin.java',
 'native/android/com/fitwave/personal/next/FitWaveVoicePlugin.java',
 'native/android/com/fitwave/personal/next/FitWaveWidgetProvider.java',
 'native/android/com/fitwave/personal/next/FitWaveWidgetPlugin.java',
 'scripts/install-v42-android.mjs','backend/supabase/schema.sql','backend/supabase/functions/fitwave-ai/index.ts'
];
for(const f of required)assert.ok(fs.statSync(path.join(root,f)).size>150,`Missing ${f}`);
for(const f of ['src/v42.ts','src/live2d.ts','src/pet.ts','src/v4.ts','src/native.ts']){
 const source=fs.readFileSync(path.join(root,f),'utf8');
 const result=ts.transpileModule(source,{fileName:f,reportDiagnostics:true,compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext}});
 const errors=(result.diagnostics||[]).filter(x=>x.category===ts.DiagnosticCategory.Error);
 assert.equal(errors.length,0,`${f}: ${errors.map(x=>ts.flattenDiagnosticMessageText(x.messageText,' ')).join('; ')}`);
 new Function(result.outputText.replace(/^import .*?;\s*$/gm,'').replace(/\bexport\s+(?=(function|const|let|class|async))/g,''));
}
const temp=fs.mkdtempSync(path.join(os.tmpdir(),'fitwave-v42-test-'));
try{
 const mirror=(rel)=>{fs.mkdirSync(path.join(temp,path.dirname(rel)),{recursive:true});fs.copyFileSync(path.join(root,rel),path.join(temp,rel));};
 for(const f of ['scripts/install-android.mjs','scripts/install-v42-android.mjs',...required.filter(x=>x.startsWith('native/android/')&&!x.endsWith('Provider.java_DISABLED'))])mirror(f);
 fs.cpSync(path.join(root,'public/mascot'),path.join(temp,'public/mascot'),{recursive:true});
 fs.cpSync(path.join(root,'native/android/com/fitwave/personal/pet'),path.join(temp,'native/android/com/fitwave/personal/pet'),{recursive:true});
 const manifest=path.join(temp,'android/app/src/main/AndroidManifest.xml');const main=path.join(temp,'android/app/src/main/java/com/fitwave/personal/MainActivity.java');
 fs.mkdirSync(path.dirname(manifest),{recursive:true});fs.mkdirSync(path.dirname(main),{recursive:true});
 fs.writeFileSync(manifest,'<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:label="FitWave"></application></manifest>');
 fs.writeFileSync(main,'package com.fitwave.personal; public class MainActivity extends com.getcapacitor.BridgeActivity { }');
 for(let i=0;i<2;i++){
  execFileSync(process.execPath,[path.join(temp,'scripts/install-android.mjs')],{stdio:'pipe'});
  execFileSync(process.execPath,[path.join(temp,'scripts/install-v42-android.mjs')],{stdio:'pipe'});
 }
 const a=fs.readFileSync(main,'utf8'),m=fs.readFileSync(manifest,'utf8');
 for(const x of ['FitWaveStorePlugin','FitWaveVoicePlugin','FitWaveWidgetPlugin','FitWavePetPlugin'])assert.equal((a.match(new RegExp(`registerPlugin\\(.*?${x}\\.class\\)`, 'g'))||[]).length,1,`registration ${x}`);
 for(const x of ['FitWaveWidgetProvider','RECORD_AUDIO','PetOverlayService'])assert.equal((m.match(new RegExp(x,'g'))||[]).length,1,`manifest ${x}`);
 assert.ok(!a.includes('@Override protected void onResume'));
 for(const f of ['res/xml/fitwave_widget_info.xml','res/layout/fitwave_widget.xml','res/drawable/fitwave_widget_bg.xml'])assert.ok(fs.existsSync(path.join(temp,'android/app/src/main',f)));
 console.log('[V4.2 smoke] PASS — TypeScript syntax, native SQLite/voice/widget source, Android repeatable install and lifecycle visibility patch');
}finally{fs.rmSync(temp,{recursive:true,force:true});}
