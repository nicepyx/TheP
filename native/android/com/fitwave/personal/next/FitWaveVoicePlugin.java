package com.fitwave.personal.next;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import java.util.ArrayList;
import java.util.Locale;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/** Push-to-talk, not a background microphone. Voice recognition is handled by Android speech services. */
@CapacitorPlugin(name="FitWaveVoice", permissions={@Permission(alias="mic",strings={Manifest.permission.RECORD_AUDIO})})
public final class FitWaveVoicePlugin extends Plugin {
    private SpeechRecognizer recognizer;
    private PluginCall waiting;
    private TextToSpeech tts;
    private boolean ttsReady=false;
    @Override public void load() {
        getActivity().runOnUiThread(()->{
            tts=new TextToSpeech(getContext(), status->{if(status==TextToSpeech.SUCCESS){ttsReady=true;tts.setLanguage(Locale.SIMPLIFIED_CHINESE);}});
        });
    }
    @PluginMethod public void listen(PluginCall call){
        if(waiting!=null){call.reject("正在识别中");return;}
        if(getPermissionState("mic")!=com.getcapacitor.PermissionState.GRANTED){requestPermissionForAlias("mic",call,"microphoneGranted");return;}
        begin(call);
    }
    @PermissionCallback private void microphoneGranted(PluginCall call){
        if(getPermissionState("mic")==com.getcapacitor.PermissionState.GRANTED)begin(call);else call.reject("没有麦克风权限");
    }
    private void releaseRecognizer(){if(recognizer!=null){try{recognizer.destroy();}catch(Exception ignored){}recognizer=null;}}
    private void finish(String text,String error){PluginCall c=waiting;waiting=null;releaseRecognizer();if(c==null)return;if(error!=null){c.reject(error);}else{JSObject o=new JSObject();o.put("text",text);c.resolve(o);}}
    private void begin(PluginCall call){
        getActivity().runOnUiThread(()->{
            if(!SpeechRecognizer.isRecognitionAvailable(getContext())){call.reject("未安装可用的 Android 语音识别服务");return;}
            try{
                waiting=call;
                releaseRecognizer();recognizer=SpeechRecognizer.createSpeechRecognizer(getContext());
                recognizer.setRecognitionListener(new RecognitionListener(){
                    @Override public void onReadyForSpeech(Bundle b){}
                    @Override public void onBeginningOfSpeech(){}
                    @Override public void onRmsChanged(float rms){}
                    @Override public void onBufferReceived(byte[] b){}
                    @Override public void onEndOfSpeech(){}
                    @Override public void onError(int err){finish(null,"语音识别失败，Android 错误码："+err);}
                    @Override public void onResults(Bundle b){ArrayList<String> r=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);finish(r!=null&&!r.isEmpty()?r.get(0):"",null);}
                    @Override public void onPartialResults(Bundle b){}
                    @Override public void onEvent(int type, Bundle b){}
                });
                Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"zh-CN");
                i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
                recognizer.startListening(i);
            }catch(Exception e){finish(null,"无法启动语音识别："+e.getMessage());}
        });
    }
    @PluginMethod public void speak(PluginCall call){String raw=call.getString("text","");String s=raw.length()>350?raw.substring(0,350):raw;if(!ttsReady){call.reject("语音播报尚未初始化，请稍后重试");return;}getActivity().runOnUiThread(()->{tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"fitwave_tts");call.resolve();});}
    @PluginMethod public void stop(PluginCall call){getActivity().runOnUiThread(()->{if(tts!=null)tts.stop();if(waiting!=null)finish(null,"已取消语音输入");call.resolve();});}
    @Override protected void handleOnDestroy(){getActivity().runOnUiThread(()->{if(tts!=null){tts.stop();tts.shutdown();}releaseRecognizer();});}
}
