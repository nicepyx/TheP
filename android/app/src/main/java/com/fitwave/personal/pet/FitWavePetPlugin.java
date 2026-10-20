package com.fitwave.personal.pet;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.content.Context;
import android.content.SharedPreferences;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Capacitor bridge. The overlay is started ONLY after an explicit tap in the foreground Activity. */
@CapacitorPlugin(name = "FitWavePet")
public class FitWavePetPlugin extends Plugin {
    private SharedPreferences prefs() { return getContext().getSharedPreferences("fitwave_pet", Context.MODE_PRIVATE); }
    private boolean permitted() { return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(getContext()); }

    @PluginMethod public void status(PluginCall call) {
        JSObject o = new JSObject();
        SharedPreferences p = prefs();
        o.put("supported", Build.VERSION.SDK_INT >= 26);
        o.put("permission", permitted());
        o.put("running", p.getBoolean("running", false));
        o.put("visible", p.getBoolean("visible", false));
        o.put("endAt", p.getLong("endAt", 0));
        o.put("pendingCommit", p.getLong("pendingCommit", 0));
        o.put("pendingExercise", p.getString("pendingExercise", ""));
        o.put("pendingTarget", p.getString("pendingTarget", ""));
        o.put("size", p.getInt("size", 100));
        call.resolve(o);
    }

    @PluginMethod public void requestOverlayPermission(PluginCall call) {
        if (permitted()) { JSObject o = new JSObject(); o.put("permission", true);call.resolve(o);return; }
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getContext().getPackageName()));
        try { getActivity().startActivity(intent); JSObject o = new JSObject(); o.put("openedSettings", true); call.resolve(o); }
        catch (Exception ex) { call.reject("无法打开悬浮窗权限设置", ex); }
    }

    @PluginMethod public void start(PluginCall call) {
        if (!permitted()) { call.reject("请先在系统设置中允许显示在其他应用上层"); return; }
        if (Build.VERSION.SDK_INT < 26) { call.reject("需要 Android 8 或更高版本"); return; }
        Intent intent = new Intent(getContext(), PetOverlayService.class);
        intent.setAction(PetOverlayService.ACTION_START);
        intent.putExtra("mode", call.getString("mode", "training"));
        intent.putExtra("size", call.getInt("size", 100));
        try { prefs().edit().putBoolean("running", true).apply(); getContext().startForegroundService(intent); call.resolve(); }
        catch (Exception ex) { prefs().edit().putBoolean("running",false).apply(); call.reject("系统限制启动悬浮服务，请在 FitWave 前台点击启用", ex); }
    }

    @PluginMethod public void update(PluginCall call) {
        if (!prefs().getBoolean("running",false)) { call.resolve();return; }
        Intent intent = new Intent(getContext(), PetOverlayService.class);
        intent.setAction(PetOverlayService.ACTION_UPDATE);
        intent.putExtra("state", call.getString("state", "idle"));
        intent.putExtra("exercise", call.getString("exercise", ""));
        intent.putExtra("target", call.getString("target", ""));
        intent.putExtra("endAt", call.getLong("endAt", 0L));
        intent.putExtra("inApp", call.getBoolean("inApp", false));
        intent.putExtra("size", call.getInt("size", 100));
        intent.putExtra("animation", call.getBoolean("animation", true));
        getContext().startService(intent);
        call.resolve();
    }

    @PluginMethod public void show(PluginCall call) {
        if (!permitted()) { call.reject("请先允许系统悬浮窗权限"); return; }
        if (!prefs().getBoolean("running", false)) { call.reject("精灵尚未启动，请先点击启动"); return; }
        Intent i = new Intent(getContext(), PetOverlayService.class);
        i.setAction(PetOverlayService.ACTION_SHOW);
        try { getContext().startService(i); call.resolve(); }
        catch (Exception ex) { call.reject("无法恢复显示悬浮精灵", ex); }
    }
    @PluginMethod public void hide(PluginCall call) {
        Intent i = new Intent(getContext(), PetOverlayService.class);
        i.setAction(PetOverlayService.ACTION_HIDE);
        if (prefs().getBoolean("running",false)) getContext().startService(i);
        call.resolve();
    }
    @PluginMethod public void stop(PluginCall call) {
        Intent i = new Intent(getContext(), PetOverlayService.class);
        i.setAction(PetOverlayService.ACTION_STOP);
        boolean wasRunning = prefs().getBoolean("running",false);
        prefs().edit().putBoolean("running",false).apply();
        if (wasRunning) getContext().startService(i);
        call.resolve();
    }
    @PluginMethod public void acknowledgeCommit(PluginCall call) {
        long expected = call.getLong("id", -1L);
        if (expected > 0 && expected == prefs().getLong("pendingCommit", 0L)) prefs().edit().putLong("pendingCommit",0L).apply();
        call.resolve();
    }
}
