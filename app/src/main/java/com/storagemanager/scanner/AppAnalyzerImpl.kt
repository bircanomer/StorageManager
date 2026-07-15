package com.storagemanager.scanner

import android.app.Application
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.storage.StorageManager
import com.storagemanager.domain.model.CacheInfo
import com.storagemanager.domain.model.UnusedApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uygulama analiz implementasyonu.
 *
 * UsageStatsManager ile kullanım istatistiklerini, PackageManager ve
 * StorageStatsManager ile uygulama boyut ve önbellek bilgilerini toplar.
 */
@Singleton
class AppAnalyzerImpl @Inject constructor(
    private val application: Application
) : AppAnalyzer {

    /**
     * Belirtilen süredir kullanılmamış uygulamaları tespit eder.
     *
     * @param daysSinceLastUse Kaç gündür kullanılmamış uygulamaların aranacağı
     * @return Kullanılmamış uygulamaların listesi (boyuta göre azalan sırada)
     */
    override suspend fun findUnusedApps(daysSinceLastUse: Int): List<UnusedApp> = withContext(Dispatchers.IO) {
        try {
            val usageStatsManager = application.getSystemService(Context.USAGE_STATS_SERVICE)
                    as? UsageStatsManager ?: return@withContext emptyList()

            // Son 1 yılın kullanım istatistiklerini sorgula
            val endTime = System.currentTimeMillis()
            val startTime = endTime - TimeUnit.DAYS.toMillis(365)

            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_YEARLY,
                startTime,
                endTime
            )

            // Paket adı → son kullanım zamanı haritası
            val lastUsedMap = mutableMapOf<String, Long>()
            usageStats?.forEach { stats ->
                val existing = lastUsedMap[stats.packageName] ?: 0L
                if (stats.lastTimeUsed > existing) {
                    lastUsedMap[stats.packageName] = stats.lastTimeUsed
                }
            }

            val cutoffTime = endTime - TimeUnit.DAYS.toMillis(daysSinceLastUse.toLong())

            // Yüklü uygulamaları al ve filtrele
            val packageManager = application.packageManager
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

            val unusedApps = mutableListOf<UnusedApp>()

            for (appInfo in installedApps) {
                try {
                    // Sistem uygulamalarını atla
                    if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue

                    val packageName = appInfo.packageName
                    val lastUsed = lastUsedMap[packageName] ?: 0L

                    // Son kullanım zamanı eşik değerinden eskiyse
                    if (lastUsed < cutoffTime) {
                        val appName = packageManager.getApplicationLabel(appInfo).toString()
                        val appSize = getAppSize(packageName)
                        val cacheSize = getAppCacheSize(packageName)
                        val installDate = try {
                            packageManager.getPackageInfo(packageName, 0).firstInstallTime
                        } catch (_: Exception) {
                            0L
                        }

                        unusedApps.add(
                            UnusedApp(
                                packageName = packageName,
                                appName = appName,
                                appSize = appSize,
                                cacheSize = cacheSize,
                                lastUsed = lastUsed,
                                installDate = installDate
                            )
                        )
                    }
                } catch (_: Exception) {
                    // Tek bir uygulama işlenemezse atla
                }
            }

            // Boyuta göre azalan sırada sırala
            unusedApps.sortByDescending { it.appSize }
            unusedApps
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Tüm yüklü uygulamaların önbellek bilgilerini döndürür.
     *
     * API 26+ cihazlarda StorageStatsManager kullanılır.
     *
     * @return Uygulamaların önbellek bilgileri (boyuta göre azalan sırada)
     */
    override suspend fun getCacheInfo(): List<CacheInfo> = withContext(Dispatchers.IO) {
        try {
            val packageManager = application.packageManager
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            val cacheInfos = mutableListOf<CacheInfo>()

            for (appInfo in installedApps) {
                try {
                    val packageName = appInfo.packageName
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val cacheSize = getAppCacheSize(packageName)

                    if (cacheSize > 0) {
                        cacheInfos.add(
                            CacheInfo(
                                packageName = packageName,
                                appName = appName,
                                cacheSize = cacheSize
                            )
                        )
                    }
                } catch (_: Exception) {
                    // Tek bir uygulama işlenemezse atla
                }
            }

            // Önbellek boyutuna göre azalan sırada sırala
            cacheInfos.sortByDescending { it.cacheSize }
            cacheInfos
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Bir uygulamanın toplam boyutunu hesaplar.
     *
     * API 26+ cihazlarda StorageStatsManager, daha eski cihazlarda
     * APK dosya boyutu kullanılır.
     *
     * @param packageName Uygulama paket adı
     * @return Uygulama boyutu (byte)
     */
    private fun getAppSize(packageName: String): Long {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val storageStatsManager = application.getSystemService(Context.STORAGE_STATS_SERVICE)
                        as StorageStatsManager
                val storageManager = application.getSystemService(Context.STORAGE_SERVICE)
                        as StorageManager
                val uuid = storageManager.getUuidForPath(application.filesDir)
                val uid = application.packageManager.getApplicationInfo(packageName, 0).uid
                val stats = storageStatsManager.queryStatsForUid(uuid, uid)
                stats.appBytes + stats.dataBytes
            } else {
                val appInfo = application.packageManager.getApplicationInfo(packageName, 0)
                val file = java.io.File(appInfo.sourceDir)
                if (file.exists()) file.length() else 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Bir uygulamanın önbellek boyutunu hesaplar.
     *
     * API 26+ cihazlarda StorageStatsManager, daha eski cihazlarda
     * uygulama cache dizini boyutu kullanılır.
     *
     * @param packageName Uygulama paket adı
     * @return Önbellek boyutu (byte)
     */
    private fun getAppCacheSize(packageName: String): Long {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val storageStatsManager = application.getSystemService(Context.STORAGE_STATS_SERVICE)
                        as StorageStatsManager
                val storageManager = application.getSystemService(Context.STORAGE_SERVICE)
                        as StorageManager
                val uuid = storageManager.getUuidForPath(application.filesDir)
                val uid = application.packageManager.getApplicationInfo(packageName, 0).uid
                val stats = storageStatsManager.queryStatsForUid(uuid, uid)
                stats.cacheBytes
            } else {
                val appInfo = application.packageManager.getApplicationInfo(packageName, 0)
                val cacheDir = java.io.File(appInfo.dataDir, "cache")
                if (cacheDir.exists()) {
                    var size = 0L
                    cacheDir.walkTopDown().forEach { file ->
                        if (file.isFile) size += file.length()
                    }
                    size
                } else {
                    0L
                }
            }
        } catch (e: Exception) {
            0L
        }
    }
}
