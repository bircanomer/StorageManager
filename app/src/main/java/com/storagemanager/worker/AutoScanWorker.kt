package com.storagemanager.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.storagemanager.domain.repository.StorageRepository
import com.storagemanager.service.NotificationHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.storagemanager.R

/**
 * Arka planda periyodik olarak çalışan hafif depolama kontrolü.
 *
 * Önemli: burada **tam fotoğraf taraması yapılmaz**. Önceki sürüm her çalıştığında
 * binlerce fotoğrafın thumbnail'ini üretip bulanıklık/hash hesaplıyordu; bu hem
 * WorkManager'ın 10 dakikalık bütçesini aşma riski taşıyor hem de bataryayı tüketiyordu.
 * Bunun yerine disk doluluğu ölçülür ve son taramanın önbelleğe alınmış sonucu okunur.
 */
class AutoScanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private companion object {
        const val TAG = "AutoScanWorker"
        const val ONE_GIGABYTE = 1024L * 1024L * 1024L
        const val DISK_USAGE_ALERT = 0.90
        const val TRASH_MAX_AGE_DAYS = 30
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AutoScanWorkerEntryPoint {
        fun storageRepository(): StorageRepository
        fun notificationHelper(): NotificationHelper
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                AutoScanWorkerEntryPoint::class.java
            )
            val repository = entryPoint.storageRepository()
            val notificationHelper = entryPoint.notificationHelper()

            // Bakım: süresi dolmuş çöp kutusu ögelerini kalıcı sil
            repository.cleanupOldTrash(TRASH_MAX_AGE_DAYS)

            val storageInfo = repository.getStorageInfo()
            val diskUsageRatio = if (storageInfo.totalSpace > 0) {
                storageInfo.usedSpace.toDouble() / storageInfo.totalSpace.toDouble()
            } else {
                0.0
            }

            // Son taramanın sonucu — yeni bir tarama başlatmadan
            val cleanableSize = repository.getCachedScanResults()?.totalCleanableSize ?: 0L

            when {
                diskUsageRatio > DISK_USAGE_ALERT -> notificationHelper.showCleanReminder(
                    title = applicationContext.getString(R.string.notification_storage_warning_title),
                    message = applicationContext.getString(
                        R.string.notification_storage_warning_message,
                        (diskUsageRatio * 100).toInt()
                    )
                )

                cleanableSize > ONE_GIGABYTE -> notificationHelper.showCleanReminder(
                    title = applicationContext.getString(R.string.notification_cleanable_title),
                    message = applicationContext.getString(
                        R.string.notification_cleanable_message,
                        (cleanableSize / ONE_GIGABYTE).toInt()
                    )
                )
            }

            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Otomatik kontrol başarısız, yeniden denenecek", e)
            Result.retry()
        }
    }
}
