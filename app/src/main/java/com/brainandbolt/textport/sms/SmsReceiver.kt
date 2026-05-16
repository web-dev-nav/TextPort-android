package com.brainandbolt.textport.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OutOfQuotaPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.brainandbolt.textport.data.AppContainer
import com.brainandbolt.textport.worker.SyncSmsWorker

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val isDeliver = Telephony.Sms.Intents.SMS_DELIVER_ACTION == intent.action
        val isReceived = Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent.action
        if (!isDeliver && !isReceived) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        // When we are the default SMS handler (SMS_DELIVER), write messages into the
        // system SMS database so the native SMS app still displays them normally,
        // then show a notification (since the native app no longer does this).
        if (isDeliver) {
            messages.forEach { sms ->
                val sender = sms.displayOriginatingAddress ?: "unknown"
                val body = sms.displayMessageBody ?: ""

                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, sender)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.DATE_SENT, sms.timestampMillis)
                    put(Telephony.Sms.READ, 0)
                    put(Telephony.Sms.SEEN, 0)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                }
                context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)

                SmsNotificationHelper.showSmsNotification(context, sender, body)
            }
        }

        // Avoid duplicate sync: if we are the default handler, SMS_DELIVER already
        // triggered the sync above. Skip SMS_RECEIVED to prevent posting twice.
        if (isReceived && Telephony.Sms.getDefaultSmsPackage(context) == context.packageName) return

        val repo = AppContainer.repository(context)
        val prefs = repo.prefs()
        if (!prefs.syncEnabled()) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        messages.forEach { sms ->
            val request = OneTimeWorkRequestBuilder<SyncSmsWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, java.time.Duration.ofSeconds(15))
                .setInputData(
                    workDataOf(
                        SyncSmsWorker.KEY_SENDER to (sms.displayOriginatingAddress ?: "unknown"),
                        SyncSmsWorker.KEY_BODY to sms.displayMessageBody,
                        SyncSmsWorker.KEY_TIMESTAMP to sms.timestampMillis
                    )
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
