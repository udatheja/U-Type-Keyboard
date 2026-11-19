package com.udara.Utype.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager job running every 15 minutes to purge expired unpinned clipboard items.
 * Compares against persisted Room timestamps so expiry remains accurate across app kills and reboots.
 */
class ClipboardCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = UTypeDatabase.getInstance(applicationContext)
            val settingsRepo = SettingsRepository(applicationContext)
            val settings = settingsRepo.currentSettings()
            val repo = ClipboardRepository(db.clipboardDao(), AndroidKeystoreClipboardCrypto())
            repo.cleanupExpired(settings.clipboardDuration.millis)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "utype_clipboard_cleanup_15m"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ClipboardCleanupWorker>(
                15, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
