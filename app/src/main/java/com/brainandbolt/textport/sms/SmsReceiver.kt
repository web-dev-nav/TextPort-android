package com.brainandbolt.textport.sms

import android.content.BroadcastReceiver
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
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) return

        val repo = AppContainer.repository(context)
        val prefs = repo.prefs()
        if (!prefs.syncEnabled()) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
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
