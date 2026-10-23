package com.ayush.aynamusic.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object CloudSyncScheduler {
    private const val ONE_TIME = "ayna_cloud_sync"
    private const val PERIODIC = "ayna_cloud_sync_periodic"

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(connected)
            .setInitialDelay(20, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(CloudSyncWorker.KEY_MANUAL to false))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .setInputData(workDataOf(CloudSyncWorker.KEY_MANUAL to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(connected)
            .setInputData(workDataOf(CloudSyncWorker.KEY_MANUAL to false))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    private val connected = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
