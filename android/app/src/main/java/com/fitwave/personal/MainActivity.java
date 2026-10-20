package com.fitwave.personal;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        registerPlugin(com.fitwave.personal.pet.FitWavePetPlugin.class);
        registerPlugin(com.fitwave.personal.next.FitWaveWidgetPlugin.class);
        registerPlugin(com.fitwave.personal.next.FitWaveVoicePlugin.class);
        registerPlugin(com.fitwave.personal.next.FitWaveStorePlugin.class);
        super.onCreate(savedInstanceState);
    }
    private void setPetForegroundState(boolean inApp) {
        if (!getSharedPreferences("fitwave_pet", MODE_PRIVATE).getBoolean("running", false)) return;
        android.content.Intent intent = new android.content.Intent(this,com.fitwave.personal.pet.PetOverlayService.class);
        intent.setAction(com.fitwave.personal.pet.PetOverlayService.ACTION_VISIBILITY);
        intent.putExtra("inApp", inApp);
        try { startService(intent); } catch (Exception ignored) {}
    }
    @Override public void onPause() { setPetForegroundState(false); super.onPause(); }
    @Override public void onResume() { super.onResume(); setPetForegroundState(true); }

}