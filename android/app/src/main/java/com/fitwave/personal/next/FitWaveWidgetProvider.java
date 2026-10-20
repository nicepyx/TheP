package com.fitwave.personal.next;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import com.fitwave.personal.R;

/** Android launcher widget; only reads sanitized metrics from local private preferences. */
public final class FitWaveWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){for(int id:ids)update(context,manager,id);}
    static void updateAll(Context context){AppWidgetManager m=AppWidgetManager.getInstance(context);int[] ids=m.getAppWidgetIds(new ComponentName(context,FitWaveWidgetProvider.class));for(int id:ids)update(context,m,id);}
    private static void update(Context c,AppWidgetManager m,int id){
        var prefs=c.getSharedPreferences("fitwave_widget",Context.MODE_PRIVATE);
        String title=prefs.getString("title","今天也要好好训练");
        String detail=prefs.getString("detail","打开 FitWave 查看今日计划");
        int done=prefs.getInt("done",0),total=prefs.getInt("total",4);
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.fitwave_widget);
        v.setTextViewText(R.id.fw_widget_title,title);
        v.setTextViewText(R.id.fw_widget_subtitle,detail);
        v.setTextViewText(R.id.fw_widget_progress,done+" / "+total+" 本周训练");
        Intent open=c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if(open!=null){open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);PendingIntent p=PendingIntent.getActivity(c,100,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);v.setOnClickPendingIntent(R.id.fw_widget_root,p);}
        m.updateAppWidget(id,v);
    }
}
