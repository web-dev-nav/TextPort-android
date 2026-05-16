package com.brainandbolt.textport.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.brainandbolt.textport.data.AppContainer
import com.brainandbolt.textport.data.SmsSyncItem

class SyncSmsWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY) ?: ""
        val timestamp = inputData.getLong(KEY_TIMESTAMP, System.currentTimeMillis())

        val repo = AppContainer.repository(applicationContext)
        val prefs = repo.prefs()
        if (!prefs.syncEnabled()) return Result.success()
        if (prefs.token().isNullOrBlank()) return Result.failure()

        return repo.sync(listOf(SmsSyncItem(sender, body, timestamp))).fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    companion object {
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        const val KEY_TIMESTAMP = "timestamp"
    }
}
