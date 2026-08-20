package com.storagemanager.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Periyodik depolama kontrolünü WorkManager'a kaydeder.
 *
 * Ayarlardaki "otomatik tarama" anahtarı bu nesne üzerinden çalışır; daha önce
 * anahtar yalnızca DataStore'a yazıyor, hiçbir iş planlanmıyordu.
 */
object AutoScanScheduler {

    private const val WORK_NAME = "auto_scan_periodic"
    private const val DEFAULT_INTERVAL_HOURS = 24L

    fun schedule(context: Context, intervalHours: Long = DEFAULT_INTERVAL_HOURS) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<AutoScanWorker>(intervalHours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(intervalHours, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Ayardaki duruma göre planlar veya iptal eder. */
    fun apply(context: Context, enabled: Boolean) {
        if (enabled) schedule(context) else cancel(context)
    }
}
