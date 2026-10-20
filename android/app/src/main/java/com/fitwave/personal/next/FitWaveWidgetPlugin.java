package com.fitwave.personal.next;

import android.content.Context;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name="FitWaveWidget")
public final class FitWaveWidgetPlugin extends Plugin {
    @PluginMethod public void update(PluginCall call){
        String title=call.getString("title","今天也要好好训练");
        String detail=call.getString("detail","打开 FitWave 查看今日计划");
        int done=Math.max(0,Math.min(7,call.getInt("done",0)));
        int total=Math.max(1,Math.min(7,call.getInt("total",4)));
        if(title.length()>45)title=title.substring(0,45);
        if(detail.length()>65)detail=detail.substring(0,65);
        getContext().getSharedPreferences("fitwave_widget",Context.MODE_PRIVATE).edit().putString("title",title).putString("detail",detail).putInt("done",done).putInt("total",total).apply();
        FitWaveWidgetProvider.updateAll(getContext());call.resolve();
    }
}
