package me.lgcode.ianua.data

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import me.lgcode.ianua.ianua
import me.lgcode.ianua.rules.RefreshResult
import java.util.concurrent.TimeUnit

/** Daily rule-pack refresh from GitHub (ADR-0003). WorkManager runs on JobScheduler, no GMS. */
class RuleRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val results = applicationContext.ianua.rules.refresh()
        Log.i(TAG, "rule refresh: $results")
        return if (results.all { it is RefreshResult.FetchFailed }) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG = "Ianua"
        private const val WORK_NAME = "refresh-rules"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RuleRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
