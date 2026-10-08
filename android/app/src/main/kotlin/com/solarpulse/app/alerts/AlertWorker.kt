package com.solarpulse.app.alerts

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.solarpulse.app.container
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Periodic background evaluation of alert rules in demo mode (15 min is WorkManager's minimum). */
class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        if (!c.isDemo) return Result.success()
        if (c.prefs.demoUser.first().isNullOrBlank()) return Result.success()
        return try {
            c.alerts.evaluate()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "solarpulse-alerts"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
