package com.brainandbolt.textport.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Required to qualify as a default SMS handler on Android.
 * Handles RESPOND_VIA_MESSAGE intents (e.g. reply from call screen).
 * TextPort does not send SMS replies — this service exists solely to satisfy
 * Android's default SMS handler requirements.
 */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
