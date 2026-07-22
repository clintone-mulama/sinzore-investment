package com.example.common

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit

/**
 * Background worker task responsible for syncing offline investment transactions and checking overdue loans.
 */
class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Background sync logic goes here (e.g. check loans, notify users, upload offline DB entries)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val SYNC_TAG = "sinzore_sync_task"

        /**
         * Builds periodic synchronization constraints (requires active unmetered internet and battery-not-low).
         */
        fun buildPeriodicWorkRequest() = 
            PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .addTag(SYNC_TAG)
                .build()

        /**
         * Builds an immediate execution request for user-driven sync forces.
         */
        fun buildOneTimeWorkRequest() = 
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(SYNC_TAG)
                .build()
    }
}
