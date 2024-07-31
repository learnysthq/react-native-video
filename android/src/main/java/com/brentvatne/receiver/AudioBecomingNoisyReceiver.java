package com.brentvatne.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;

public class AudioBecomingNoisyReceiver extends BroadcastReceiver {

    private final Context context;
    private BecomingNoisyListener listener = BecomingNoisyListener.NO_OP;

    public AudioBecomingNoisyReceiver(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
            listener.onAudioBecomingNoisy();
        }
    }

    public void setListener(BecomingNoisyListener listener) {
        this.listener = listener;
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
       
          if (Build.VERSION.SDK_INT >= 34 && context.getApplicationInfo().targetSdkVersion >= 34) {
             context.registerReceiver(this, intentFilter, /* RECEIVER_EXPORTED*/  2);
        } else {
             context.registerReceiver(this, intentFilter);
        }
    }

    public void removeListener() {
        this.listener = BecomingNoisyListener.NO_OP;
        try {
            context.unregisterReceiver(this);
        } catch (Exception ignore) {
            // ignore if already unregistered
        }
    }
}
