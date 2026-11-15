package com.meilluer.glanceables.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.meilluer.glanceables.debug.AppLogger
import com.meilluer.glanceables.scraper.CricbuzzScraper
import com.meilluer.glanceables.storage.PreferencesManager
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that fetches cricket scores, CRR, and RRR from Cricbuzz,
 * pushes the data to the watch complication, and re-schedules itself every 6 minutes.
 */
class CricketSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "CricketSyncWorker"
        const val WORK_NAME = "cricket_sync_6min"
        const val INTERVAL_MINUTES = 6L

        /**
         * Starts the 6-minute background sync loop.
         */
        fun startPeriodic6MinSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<CricketSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
            AppLogger.i(TAG, "Started 6-minute recurring Cricket sync")
        }

        /**
         * Cancels the 6-minute background sync loop.
         */
        fun cancelPeriodic6MinSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            AppLogger.i(TAG, "Cancelled 6-minute Cricket sync")
        }

        /**
         * Schedules the next run 6 minutes from now.
         */
        private fun scheduleNext(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val nextRequest = OneTimeWorkRequestBuilder<CricketSyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                nextRequest
            )
            AppLogger.d(TAG, "Scheduled next Cricket sync in $INTERVAL_MINUTES minutes")
        }
    }

    override suspend fun doWork(): Result {
        val prefs = PreferencesManager(applicationContext)
        val followedTeams = prefs.getFollowedCricketTeams()

        if (followedTeams.isEmpty()) {
            AppLogger.d(TAG, "No cricket teams followed, skipping sync")
            // Still schedule next in case user adds teams later
            scheduleNext(applicationContext)
            return Result.success()
        }

        return try {
            AppLogger.i(TAG, "6-minute Cricket sync started for ${followedTeams.size} teams")
            val scraper = CricbuzzScraper()
            val matches = scraper.getMatchesForFollowedTeams(followedTeams)

            if (matches.isNotEmpty()) {
                val sender = WearableDataSender(applicationContext)
                val topMatch = matches.first()
                sender.sendCricketDataToWatch(topMatch)
                AppLogger.i(TAG, "Cricket sync successful: ${topMatch.title} -> ${topMatch.getComplicationSummary()}")
            } else {
                AppLogger.d(TAG, "No matches found for followed cricket teams")
            }

            // Always chain the next run 6 minutes later
            scheduleNext(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Cricket sync failed", e)
            AppLogger.w(TAG, "Cricket sync failed: ${e.message}", e)
            // Still schedule next to maintain 6-min cadence
            scheduleNext(applicationContext)
            Result.success()
        }
    }
}
