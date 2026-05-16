package com.brainandbolt.textport.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Required to qualify as a default SMS handler on Android.
 * Receives WAP push (MMS) broadcasts when this app is the default SMS app.
 * TextPort does not process MMS content — this receiver exists solely to satisfy
 * Android's default SMS handler requirements.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION != intent.action) return
        // MMS handling not implemented — TextPort only syncs SMS.
    }
}
