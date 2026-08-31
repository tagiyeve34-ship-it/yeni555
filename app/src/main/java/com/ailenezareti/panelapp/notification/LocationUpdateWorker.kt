package com.ailenezareti.panelapp.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class LocationUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            LocationUpdateChecker.check(applicationContext, notifyOnChange = true)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
