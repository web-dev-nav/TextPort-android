package com.brainandbolt.textport.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.brainandbolt.textport.MainActivity
import com.brainandbolt.textport.R

object SmsNotificationHelper {

    private const val CHANNEL_ID = "textport_sms"
    private const val CHANNEL_NAME = "Incoming SMS"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for incoming SMS messages"
            enableVibration(true)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun showSmsNotification(context: Context, sender: String, body: String) {
        // Tapping the notification opens the native SMS app inbox
        val openSmsIntent = context.packageManager
            .getLaunchIntentForPackage(Telephony.Sms.getDefaultSmsPackage(context) ?: "")
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                type = "vnd.android-dir/mms-sms"
            }

        val pendingIntent = PendingIntent.getActivity(
            context,
            sender.hashCode(),
            openSmsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(sender)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context)
            .notify(sender.hashCode(), notification)
    }
}
