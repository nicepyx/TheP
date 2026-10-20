package com.fitwave.personal.pet;

import android.animation.ObjectAnimator;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.InputStream;
import java.util.Locale;

/** Local-only, permissioned Android overlay. No accessibility or usage-statistics API. */
public final class PetOverlayService extends Service {
    public static final String ACTION_START = "fitwave.pet.START";
    public static final String ACTION_UPDATE = "fitwave.pet.UPDATE";
    public static final String ACTION_HIDE = "fitwave.pet.HIDE";
    public static final String ACTION_STOP = "fitwave.pet.STOP";
    public static final String ACTION_VISIBILITY = "fitwave.pet.VISIBILITY";
    public static final String ACTION_SHOW = "fitwave.pet.SHOW";
    private static final String ACTION_EXTEND = "fitwave.pet.EXTEND";
    private static final String ACTION_COMMIT = "fitwave.pet.COMMIT";
    private static final int NOTIFICATION_ID = 4100, REST_NOTIFICATION_ID = 4101;
    private static final String CHANNEL = "fitwave_pet_service", ALERTS = "fitwave_pet_alerts";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private FrameLayout container;
    private ImageView sprite;
    private LinearLayout panel;
    private TextView countdown, exerciseLabel, targetLabel;
    private ObjectAnimator idleAnim;
    private android.content.SharedPreferences prefs;
    private String state="idle",exercise="",target="",mode="training";
    private long endAt=0;
    private boolean expanded=false,inApp=false,hidden=false,notified=false,animation=true;
    private int size=100, positionX=-1,positionY=-1;
    private long pressTime=0,lastTap=0;
    private float touchX,touchY;
    private boolean dragged=false;

    @Override public void onCreate() { super.onCreate();wm=(WindowManager)getSystemService(WINDOW_SERVICE);prefs=getSharedPreferences("fitwave_pet",MODE_PRIVATE);endAt=prefs.getLong("endAt",0);size=prefs.getInt("size",100);mode=prefs.getString("mode","training");positionX=prefs.getInt("x",-1);positionY=prefs.getInt("y",-1);createChannels();handler.post(ticker); }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int color,int radius,int border){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(border>0)d.setStroke(dp(1),border);return d;}
    private int ink(String h){return Color.parseColor(h);}
    private TextView text(String t,int sp,int color){TextView v=new TextView(this);v.setText(t);v.setTextSize(sp);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void createChannels(){ if(Build.VERSION.SDK_INT<26)return;NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE); NotificationChannel c=new NotificationChannel(CHANNEL,"FitWave 桌面精灵",NotificationManager.IMPORTANCE_LOW);c.setDescription("控制显示、隐藏和关闭桌面精灵");nm.createNotificationChannel(c);NotificationChannel a=new NotificationChannel(ALERTS,"FitWave 训练提醒",NotificationManager.IMPORTANCE_DEFAULT);nm.createNotificationChannel(a); }
    private PendingIntent actionIntent(String action,int req){Intent i=new Intent(this,PetOverlayService.class).setAction(action);return PendingIntent.getService(this,req,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private PendingIntent openIntent(){Intent i=getPackageManager().getLaunchIntentForPackage(getPackageName());if(i==null)i=new Intent();i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);return PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private Notification ongoing(){return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_today).setContentTitle("FitWave · 奶霜丝带").setContentText(hidden?"精灵已隐藏 · 可在这里重新显示":"桌面精灵运行中 · 可随时停止").setContentIntent(openIntent()).setOngoing(true).addAction(android.R.drawable.ic_menu_view,hidden?"显示":"隐藏",actionIntent(hidden?ACTION_SHOW:ACTION_HIDE,1)).addAction(android.R.drawable.ic_menu_close_clear_cancel,"停止",actionIntent(ACTION_STOP,2)).build();}
    private void notifyOngoing(){try { ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID,ongoing()); } catch(SecurityException ex) { android.util.Log.w("FitWavePet","Notification permission denied",ex); }}
    private void ensureForeground(){Notification n=ongoing(); if(Build.VERSION.SDK_INT>=34)startForeground(NOTIFICATION_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(NOTIFICATION_ID,n);}
    @Override public int onStartCommand(Intent i,int flags,int startId){
        if(i==null) return START_NOT_STICKY;
        String a=i.getAction();
        if(ACTION_VISIBILITY.equals(a)){inApp=i.getBooleanExtra("inApp",inApp);refresh();return START_NOT_STICKY;}
        if(ACTION_STOP.equals(a)){endAt=0;stopSelf();return START_NOT_STICKY;}
        if(ACTION_HIDE.equals(a)){hidden=true;removeWindow();prefs.edit().putBoolean("visible",false).apply();notifyOngoing();return START_NOT_STICKY;}
        if(ACTION_SHOW.equals(a)){hidden=false;if(allowed())attachWindow();notifyOngoing();return START_NOT_STICKY;}
        if(ACTION_EXTEND.equals(a)){if(endAt>DateTime())endAt+=30000;else endAt=DateTime()+30000;notified=false;state="rest";persist();refresh();return START_NOT_STICKY;}
        if(ACTION_COMMIT.equals(a)){if(target.length()>0&&exercise.length()>0&&!target.contains("—")){prefs.edit().putLong("pendingCommit",DateTime()).putString("pendingExercise",exercise).putString("pendingTarget",target).apply();launchApp();}return START_NOT_STICKY;}
        if(ACTION_START.equals(a)){
            if(!allowed()){stopSelf();return START_NOT_STICKY;}
            endAt=0;notified=false;state="idle";
            mode=i.getStringExtra("mode")!=null?i.getStringExtra("mode"):"training";size=clamp(i.getIntExtra("size",100),64,160);hidden=false;inApp=true;
            prefs.edit().putBoolean("running",true).putString("mode",mode).putInt("size",size).apply();
            try{ensureForeground();attachWindow();}catch(Exception e){android.util.Log.e("FitWavePet","Overlay error",e);stopSelf();}
            return START_NOT_STICKY;
        }
        if(ACTION_UPDATE.equals(a)){
            String s=i.getStringExtra("state");if(s!=null)state=s;
            String ex=i.getStringExtra("exercise");if(ex!=null)exercise=ex;
            String t=i.getStringExtra("target");if(t!=null)target=t;
            endAt=i.getLongExtra("endAt",endAt);inApp=i.getBooleanExtra("inApp",inApp);animation=i.getBooleanExtra("animation",animation);
            int newSize=clamp(i.getIntExtra("size",size),64,160);if(size!=newSize){size=newSize;updateSize();}
            if("rest".equals(state))notified=false;
            persist();refresh();
        }
        return START_NOT_STICKY;
    }
    private long DateTime(){return System.currentTimeMillis();}
    private int clamp(int n,int l,int h){return Math.max(l,Math.min(h,n));}
    private boolean allowed(){return Build.VERSION.SDK_INT>=23&&Settings.canDrawOverlays(this);}
    private void persist(){prefs.edit().putLong("endAt",endAt).putInt("size",size).putString("mode",mode).apply();}
    private final Runnable ticker=new Runnable(){@Override public void run(){if(!allowed()){removeWindow();stopSelf();return;} if(endAt>0){long now=DateTime();if(now>=endAt){endAt=0;state="ready";persist();if(!notified){notified=true;Notification alert=new Notification.Builder(PetOverlayService.this,ALERTS).setSmallIcon(android.R.drawable.ic_menu_today).setContentTitle("休息结束 · 下一组准备好啦").setContentText(exercise.length()>0?exercise:"打开 FitWave 继续训练").setContentIntent(openIntent()).setAutoCancel(true).build();try { ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(REST_NOTIFICATION_ID,alert); } catch(SecurityException ex) { android.util.Log.w("FitWavePet","Rest notification denied",ex); }}}}refresh();handler.postDelayed(this,1000);}};
    private void launchApp(){try{Intent i=getPackageManager().getLaunchIntentForPackage(getPackageName());if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);}}catch(Exception e){android.util.Log.w("FitWavePet","Launch denied",e);}}
    private void attachWindow(){
        if(container!=null||hidden||!allowed())return;
        int sw=getResources().getDisplayMetrics().widthPixels,sh=getResources().getDisplayMetrics().heightPixels;
        positionX=positionX<0?sw-dp(135):positionX;positionY=positionY<0?Math.min(sh/3,dp(270)):positionY;
        lp=new WindowManager.LayoutParams(dp(size+16),dp(size+32),Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.LEFT;lp.x=clamp(positionX,0,Math.max(0,sw-lp.width));lp.y=clamp(positionY,dp(30),Math.max(dp(30),sh-lp.height-dp(60)));
        container=new FrameLayout(this);container.setClipChildren(false);
        LinearLayout stack=new LinearLayout(this);stack.setOrientation(LinearLayout.VERTICAL);stack.setGravity(Gravity.CENTER_HORIZONTAL);container.addView(stack,new FrameLayout.LayoutParams(-1,-1));
        sprite=new ImageView(this);sprite.setContentDescription("奶霜丝带桌面精灵；拖动移动，点击打开功能");sprite.setScaleType(ImageView.ScaleType.FIT_CENTER);
        stack.addView(sprite,new LinearLayout.LayoutParams(dp(size),dp(size+8)));
        sprite.setOnTouchListener((v,event)->{
          if(event.getActionMasked()==MotionEvent.ACTION_DOWN){pressTime=DateTime();touchX=event.getRawX();touchY=event.getRawY();dragged=false;return true;}
          if(event.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=event.getRawX()-touchX,dy=event.getRawY()-touchY;if(Math.abs(dx)+Math.abs(dy)>dp(7))dragged=true;if(dragged&&lp!=null){lp.x+=Math.round(dx);lp.y+=Math.round(dy);clampPosition();try{wm.updateViewLayout(container,lp);}catch(Exception ignored){}touchX=event.getRawX();touchY=event.getRawY();}return true;}
          if(event.getActionMasked()==MotionEvent.ACTION_UP){if(dragged){state=endAt>DateTime()?"rest":"idle";shownImage="";refresh();if(lp!=null){int width=getResources().getDisplayMetrics().widthPixels;lp.x=lp.x+lp.width/2<width/2?dp(2):Math.max(0,width-lp.width-dp(2));clampPosition();wm.updateViewLayout(container,lp);prefs.edit().putInt("x",lp.x).putInt("y",lp.y).apply();}return true;}long now=DateTime();if(now-lastTap<360){launchApp();lastTap=0;}else{lastTap=now;state="touch";shownImage="";toggleCard();handler.postDelayed(()->{if("touch".equals(state)){state=endAt>DateTime()?"rest":"idle";shownImage="";refresh();}},1600);}return true;}
          return true;
        });
        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(13),dp(9),dp(13),dp(9));panel.setBackground(bg(ink("#FFF8FB"),18,ink("#F0B7CF")));
        TextView title=text("FitWave · 奶霜丝带",13,ink("#962D5D"));title.setTypeface(null,1);panel.addView(title);
        exerciseLabel=text("训练陪伴",12,ink("#663A53"));exerciseLabel.setMaxLines(1);panel.addView(exerciseLabel);
        countdown=text("",23,ink("#B6296D"));countdown.setGravity(Gravity.CENTER);countdown.setTypeface(null,1);panel.addView(countdown,new LinearLayout.LayoutParams(-1,dp(34)));
        targetLabel=text("",10,ink("#815D71"));targetLabel.setGravity(Gravity.CENTER);panel.addView(targetLabel);
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);actions.setPadding(0,dp(6),0,0);panel.addView(actions);
        button(actions,"确认一组",()->onStartCommand(new Intent(this,PetOverlayService.class).setAction(ACTION_COMMIT),0,0));
        button(actions,"+30 秒",()->onStartCommand(new Intent(this,PetOverlayService.class).setAction(ACTION_EXTEND),0,0));
        button(actions,"返回训练",this::launchApp);
        button(actions,"收起",this::toggleCard);
        LinearLayout.LayoutParams pnl=new LinearLayout.LayoutParams(-1,-2);pnl.topMargin=dp(3);stack.addView(panel,pnl);panel.setVisibility(View.GONE);
        try{wm.addView(container,lp);prefs.edit().putBoolean("visible",true).apply();refresh();}
        catch(Exception e){container=null;android.util.Log.e("FitWavePet","Cannot add overlay",e);}
    }
    private void button(LinearLayout l,String label,Runnable cb){TextView b=text(label,10,ink("#9E2E65"));b.setGravity(Gravity.CENTER);b.setBackground(bg(ink("#FFE2EF"),9,0));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(34),1);p.setMargins(dp(2),0,dp(2),0);l.addView(b,p);b.setOnClickListener(v->cb.run());}
    private void toggleCard(){expanded=!expanded;updateSize();refresh();}
    private void clampPosition(){int w=getResources().getDisplayMetrics().widthPixels,h=getResources().getDisplayMetrics().heightPixels;lp.x=clamp(lp.x,0,Math.max(0,w-lp.width));lp.y=clamp(lp.y,dp(30),Math.max(dp(30),h-lp.height-dp(70)));}
    private void updateSize(){if(lp==null||container==null)return;lp.width=expanded?dp(285):dp(size+16);lp.height=expanded?dp(size+194):dp(size+32);clampPosition();sprite.getLayoutParams().width=dp(size);sprite.getLayoutParams().height=dp(size+8);sprite.requestLayout();panel.setVisibility(expanded?View.VISIBLE:View.GONE);try{wm.updateViewLayout(container,lp);}catch(Exception ignored){}}
    private String imageName(){switch(state){case "cheer":case "ready":case "greeting":case "drag":case "warning":return "a1_cheer.png";case "rest":return "a1_rest.png";case "done":case "touch":return "a1_done.png";default:return "a1_idle.png";}}
    private String shownImage="";
    private void refresh(){if(container==null)return;container.setVisibility(inApp?View.GONE:View.VISIBLE);String wanted=imageName();if(sprite!=null&&!wanted.equals(shownImage)){try(InputStream is=getAssets().open("mascot/"+wanted)){Bitmap b=BitmapFactory.decodeStream(is);sprite.setImageBitmap(b);shownImage=wanted; if(idleAnim!=null)idleAnim.cancel();idleAnim=ObjectAnimator.ofFloat(sprite,"translationY",0f,-dp(3),0f);idleAnim.setDuration("cheer".equals(state)?1200:3300);idleAnim.setRepeatCount(ObjectAnimator.INFINITE);if(animation)idleAnim.start();}catch(Exception e){android.util.Log.w("FitWavePet","Missing sprite",e);}}
      if(idleAnim!=null){if(animation&&!inApp&&!idleAnim.isRunning())idleAnim.start();if((!animation||inApp)&&idleAnim.isRunning()){idleAnim.cancel();sprite.setTranslationY(0);}}
      if(exerciseLabel!=null)exerciseLabel.setText(exercise.isEmpty()?"训练日常 · 轻松坚持":exercise);
      long secs=Math.max(0,(endAt-DateTime()+999)/1000);if(endAt>0&&secs<=10&&secs>0&&!"warning".equals(state)){state="warning";shownImage="";}
      if(countdown!=null)countdown.setText(endAt>0?String.format(Locale.ROOT,"%02d:%02d",secs/60,secs%60):("done".equals(state)?"训练完成 ♡":"一起加油 ♡"));
      if(targetLabel!=null)targetLabel.setText(target);
    }
    private void removeWindow(){if(idleAnim!=null){idleAnim.cancel();idleAnim=null;}if(container!=null){try{wm.removeViewImmediate(container);}catch(Exception ignored){}container=null;sprite=null;panel=null;shownImage="";}}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);removeWindow();prefs.edit().putBoolean("running",false).putBoolean("visible",false).apply();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
