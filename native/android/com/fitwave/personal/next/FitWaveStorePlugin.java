package com.fitwave.personal.next;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.Context;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.Iterator;

/** Offline-first database; JSON document preserves legacy format and supports transactional snapshots. */
@CapacitorPlugin(name="FitWaveStore")
public final class FitWaveStorePlugin extends Plugin {
    private Db db;
    private static final int MAX_BYTES=8*1024*1024;
    private static final class Db extends SQLiteOpenHelper {
        Db(Context ctx) { super(ctx,"fitwave_training_v42.db",null,2); }
        @Override public void onCreate(SQLiteDatabase d) {
            d.execSQL("CREATE TABLE IF NOT EXISTS state (id INTEGER PRIMARY KEY CHECK (id=1), payload TEXT NOT NULL, modified INTEGER NOT NULL)");
            d.execSQL("CREATE TABLE IF NOT EXISTS snapshots (id INTEGER PRIMARY KEY AUTOINCREMENT, payload TEXT NOT NULL, reason TEXT NOT NULL, created INTEGER NOT NULL)");
            d.execSQL("CREATE INDEX IF NOT EXISTS snapshots_created ON snapshots(created DESC)");
            createTrainingTables(d);
        }
        private static void createTrainingTables(SQLiteDatabase d){
            d.execSQL("CREATE TABLE IF NOT EXISTS workout_sessions (session_key TEXT PRIMARY KEY, date TEXT NOT NULL, day TEXT NOT NULL, completed_at TEXT, payload TEXT NOT NULL)");
            d.execSQL("CREATE INDEX IF NOT EXISTS sessions_date ON workout_sessions(date DESC)");
            d.execSQL("CREATE TABLE IF NOT EXISTS workout_sets (session_key TEXT NOT NULL, exercise_id TEXT NOT NULL, set_index INTEGER NOT NULL, kg REAL, reps INTEGER, done INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(session_key,exercise_id,set_index))");
            d.execSQL("CREATE INDEX IF NOT EXISTS completed_sets ON workout_sets(done)");
            d.execSQL("CREATE TABLE IF NOT EXISTS body_metrics (date TEXT PRIMARY KEY, weight REAL NOT NULL, waist REAL)");
        }
        @Override public void onUpgrade(SQLiteDatabase d,int a,int b) {if(a<2)createTrainingTables(d);}

    }
    @Override public void load() {db=new Db(getContext());}
    private boolean isValid(String s) {
        if(s==null||s.length()>MAX_BYTES)return false;
        try {JSONObject d=new JSONObject(s);return d.optJSONObject("sessions")!=null && d.optJSONObject("cardio")!=null && d.optJSONArray("body")!=null;}catch(Exception e){return false;}
    }
    private String get(SQLiteDatabase d) {
        try(Cursor c=d.rawQuery("SELECT payload FROM state WHERE id=1",null)) {return c.moveToFirst()?c.getString(0):null;}
    }
    private void put(SQLiteDatabase d,String payload) throws Exception {
        JSONObject root=new JSONObject(payload);
        ContentValues v=new ContentValues();v.put("id",1);v.put("payload",payload);v.put("modified",System.currentTimeMillis());
        d.insertWithOnConflict("state",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        // Materialized relational tables support native reports without querying a JSON string.
        d.delete("workout_sessions",null,null);d.delete("workout_sets",null,null);d.delete("body_metrics",null,null);
        JSONObject sessions=root.optJSONObject("sessions");
        for(Iterator<String> it=sessions.keys();it.hasNext();){
            String key=it.next();JSONObject obj=sessions.optJSONObject(key);if(obj==null)continue;
            String[] bits=key.split("__",2);if(bits.length!=2)continue;
            ContentValues session=new ContentValues();session.put("session_key",key);session.put("date",bits[0]);session.put("day",bits[1]);
            if(!obj.isNull("completedAt"))session.put("completed_at",obj.optString("completedAt",""));else session.putNull("completed_at");
            session.put("payload",obj.toString());d.insertWithOnConflict("workout_sessions",null,session,SQLiteDatabase.CONFLICT_REPLACE);
            JSONObject sets=obj.optJSONObject("sets");if(sets==null)continue;
            for(Iterator<String> exs=sets.keys();exs.hasNext();){String exercise=exs.next();JSONArray rows=sets.optJSONArray(exercise);if(rows==null)continue;
                for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;
                    ContentValues sv=new ContentValues();sv.put("session_key",key);sv.put("exercise_id",exercise);sv.put("set_index",i);
                    if(!row.isNull("weight")&&!row.optString("weight","").isEmpty())sv.put("kg",row.optDouble("weight"));else sv.putNull("kg");
                    if(!row.isNull("reps")&&!row.optString("reps","").isEmpty())sv.put("reps",row.optInt("reps"));else sv.putNull("reps");
                    sv.put("done",row.optBoolean("done",false)?1:0);d.insertWithOnConflict("workout_sets",null,sv,SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
        }
        JSONArray body=root.optJSONArray("body");
        for(int i=0;i<body.length();i++){JSONObject record=body.optJSONObject(i);if(record==null)continue;
            String date=record.optString("date","");if(!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))continue;
            ContentValues b=new ContentValues();b.put("date",date);b.put("weight",record.optDouble("weight",0));
            if(!record.isNull("waist"))b.put("waist",record.optDouble("waist"));else b.putNull("waist");
            d.insertWithOnConflict("body_metrics",null,b,SQLiteDatabase.CONFLICT_REPLACE);
        }
    }
    private void checkpoint(SQLiteDatabase d,String payload,String why){ContentValues v=new ContentValues();v.put("payload",payload);v.put("reason",why);v.put("created",System.currentTimeMillis());d.insert("snapshots",null,v);d.execSQL("DELETE FROM snapshots WHERE id NOT IN (SELECT id FROM snapshots ORDER BY id DESC LIMIT 20)");}
    @PluginMethod public void status(PluginCall call) {try(SQLiteDatabase d=db.getReadableDatabase()){JSObject o=new JSObject();String s=get(d);o.put("ready",true);o.put("hasData",isValid(s));o.put("bytes",s==null?0:s.getBytes("UTF-8").length);try(Cursor c=d.rawQuery("SELECT COUNT(*) FROM snapshots",null)){o.put("snapshots",c.moveToFirst()?c.getInt(0):0);}call.resolve(o);}catch(Exception e){call.reject("SQLite 查询失败",e);}}
    @PluginMethod public void read(PluginCall call){try(SQLiteDatabase d=db.getReadableDatabase()){JSObject o=new JSObject();String s=get(d);o.put("payload",isValid(s)?s:"");call.resolve(o);}catch(Exception e){call.reject("SQLite 读取失败",e);}}
    @PluginMethod public void write(PluginCall call){String s=call.getString("payload");if(!isValid(s)){call.reject("训练数据格式不正确或超出8MB限制");return;}boolean snapshot=call.getBoolean("snapshot",false);String reason=call.getString("reason","自动备份");try {SQLiteDatabase d=db.getWritableDatabase();d.beginTransaction();try{put(d,s);if(snapshot)checkpoint(d,s,reason);d.setTransactionSuccessful();}finally{d.endTransaction();}call.resolve();}catch(Exception e){call.reject("SQLite 写入失败",e);}}
    @PluginMethod public void stats(PluginCall call){try(SQLiteDatabase d=db.getReadableDatabase()){
        JSObject o=new JSObject();
        try(Cursor c=d.rawQuery("SELECT count(*) FROM workout_sessions WHERE completed_at IS NOT NULL AND completed_at<>''",null)){o.put("completedSessions",c.moveToFirst()?c.getInt(0):0);}
        try(Cursor c=d.rawQuery("SELECT count(*) FROM workout_sets WHERE done=1",null)){o.put("completedSets",c.moveToFirst()?c.getInt(0):0);}
        try(Cursor c=d.rawQuery("SELECT weight FROM body_metrics ORDER BY date DESC LIMIT 1",null)){if(c.moveToFirst())o.put("lastWeight",c.getDouble(0));}
        call.resolve(o);
    }catch(Exception e){call.reject("原生统计查询失败",e);}}
    @PluginMethod public void checkpoints(PluginCall call) {try(SQLiteDatabase d=db.getReadableDatabase();Cursor c=d.rawQuery("SELECT id,reason,created FROM snapshots ORDER BY id DESC LIMIT 20",null)) {org.json.JSONArray items=new org.json.JSONArray();while(c.moveToNext()){JSObject x=new JSObject();x.put("id",c.getInt(0));x.put("reason",c.getString(1));x.put("created",c.getLong(2));items.put(x);}JSObject o=new JSObject();o.put("items",items);call.resolve(o);}catch(Exception e){call.reject("读取恢复点失败",e);}}
    @PluginMethod public void restore(PluginCall call) {int id=call.getInt("id",-1);if(id<1){call.reject("无效恢复点");return;}SQLiteDatabase d=db.getWritableDatabase();d.beginTransaction();try{String saved=null;try(Cursor c=d.rawQuery("SELECT payload FROM snapshots WHERE id=?",new String[]{Integer.toString(id)})){if(c.moveToFirst())saved=c.getString(0);}if(!isValid(saved))throw new IllegalArgumentException("恢复点不存在或损坏");String old=get(d);if(isValid(old))checkpoint(d,old,"恢复前自动备份");put(d,saved);d.setTransactionSuccessful();JSObject o=new JSObject();o.put("payload",saved);call.resolve(o);}catch(Exception e){call.reject("恢复失败",e);}finally{d.endTransaction();}}
}
