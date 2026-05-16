package com.brainandbolt.textport.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.brainandbolt.textport.data.AppContainer

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Touch app prefs/repository on boot so sync settings remain warm after restart.
        AppContainer.repository(context).prefs().syncEnabled()
    }
}
