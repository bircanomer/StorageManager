package com.storagemanager.scanner

import android.app.Application
import android.app.usage.StorageStats
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process
import android.os.UserHandle
import android.os.storage.StorageManager
import android.util.Log
import com.storagemanager.domain.model.CacheInfo
import com.storagemanager.domain.model.UnusedApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

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

    private companion object {
        const val TAG = "AppAnalyzer"
    }

    /** Bir uygulamanın boyut + önbellek bilgisi — tek StorageStats sorgusundan üretilir. */
    private data class AppSizes(val appBytes: Long, val cacheBytes: Long)

    private val storageStatsManager: StorageStatsManager? by lazy {
        try {
            application.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        } catch (e: Exception) {
            Log.w(TAG, "StorageStatsManager alınamadı", e)
            null
        }
    }

    private val userHandle: UserHandle by lazy {
        UserHandle.getUserHandleForUid(Process.myUid())
    }

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

            val endTime = System.currentTimeMillis()
            val startTime = endTime - TimeUnit.DAYS.toMillis(365)

            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_YEARLY,
                startTime,
                endTime
            )

            val lastUsedMap = mutableMapOf<String, Long>()
            usageStats?.forEach { stats ->
                val existing = lastUsedMap[stats.packageName] ?: 0L
                if (stats.lastTimeUsed > existing) {
                    lastUsedMap[stats.packageName] = stats.lastTimeUsed
                }
            }

            val cutoffTime = endTime - TimeUnit.DAYS.toMillis(daysSinceLastUse.toLong())
            val packageManager = application.packageManager
            // GET_META_DATA her uygulamanın manifest meta-data'sını okur; burada
            // kullanılmadığı için bayrak 0 ile çağırmak sorguyu belirgin şekilde hızlandırır.
            val installedApps = packageManager.getInstalledApplications(0)
            val unusedApps = mutableListOf<UnusedApp>()

            for (appInfo in installedApps) {
                coroutineContext.ensureActive()
                try {
                    if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue

                    val packageName = appInfo.packageName
                    val lastUsed = lastUsedMap[packageName] ?: 0L
                    if (lastUsed >= cutoffTime) continue

                    // Tek sorgu: boyut ve önbellek birlikte okunur (eskiden iki ayrı IPC vardı)
                    val sizes = queryAppSizes(appInfo)
                    val installDate = try {
                        packageManager.getPackageInfo(packageName, 0).firstInstallTime
                    } catch (e: Exception) {
                        0L
                    }

                    unusedApps += UnusedApp(
                        packageName = packageName,
                        appName = packageManager.getApplicationLabel(appInfo).toString(),
                        appSize = sizes.appBytes,
                        cacheSize = sizes.cacheBytes,
                        lastUsed = lastUsed,
                        installDate = installDate
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Uygulama işlenemedi: ${appInfo.packageName}", e)
                }
            }

            unusedApps.sortByDescending { it.appSize }
            unusedApps
        } catch (e: Exception) {
            Log.e(TAG, "Kullanılmayan uygulama taraması başarısız", e)
            emptyList()
        }
    }

    /**
     * Tüm yüklü uygulamaların önbellek bilgilerini döndürür.
     *
     * @return Uygulamaların önbellek bilgileri (boyuta göre azalan sırada)
     */
    override suspend fun getCacheInfo(): List<CacheInfo> = withContext(Dispatchers.IO) {
        try {
            val packageManager = application.packageManager
            val installedApps = packageManager.getInstalledApplications(0)
            val cacheInfos = mutableListOf<CacheInfo>()

            for (appInfo in installedApps) {
                coroutineContext.ensureActive()
                try {
                    val cacheSize = queryAppSizes(appInfo).cacheBytes
                    if (cacheSize > 0) {
                        cacheInfos += CacheInfo(
                            packageName = appInfo.packageName,
                            appName = packageManager.getApplicationLabel(appInfo).toString(),
                            cacheSize = cacheSize
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Önbellek bilgisi alınamadı: ${appInfo.packageName}", e)
                }
            }

            cacheInfos.sortByDescending { it.cacheSize }
            cacheInfos
        } catch (e: Exception) {
            Log.e(TAG, "Önbellek taraması başarısız", e)
            emptyList()
        }
    }

    /**
     * Uygulamanın boyut ve önbellek bilgisini **tek** StorageStats sorgusuyla okur.
     *
     * Sorgu başarısız olursa (izin yoksa) APK boyutu + fiziksel cache klasörüne düşer.
     */
    private fun queryAppSizes(appInfo: ApplicationInfo): AppSizes {
        val stats: StorageStats? = try {
            storageStatsManager?.queryStatsForPackage(
                StorageManager.UUID_DEFAULT,
                appInfo.packageName,
                userHandle
            )
        } catch (e: Exception) {
            // PACKAGE_USAGE_STATS izni verilmemişse burası her uygulamada tetiklenir;
            // gürültü yapmamak için ayrıntılı log basmıyoruz.
            null
        }

        if (stats != null) {
            return AppSizes(appBytes = stats.appBytes + stats.dataBytes, cacheBytes = stats.cacheBytes)
        }

        return AppSizes(
            appBytes = apkSize(appInfo),
            cacheBytes = physicalCacheSize(appInfo)
        )
    }

    private fun apkSize(appInfo: ApplicationInfo): Long = try {
        appInfo.sourceDir?.let { java.io.File(it).length() } ?: 0L
    } catch (e: Exception) {
        0L
    }

    private fun physicalCacheSize(appInfo: ApplicationInfo): Long = try {
        val cacheDir = java.io.File(appInfo.dataDir, "cache")
        if (cacheDir.exists()) {
            cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else {
            0L
        }
    } catch (e: Exception) {
        0L
    }
}
