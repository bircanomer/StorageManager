package com.storagemanager.scanner

import android.app.Application
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import com.storagemanager.domain.model.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Cihaz depolama alanı analiz implementasyonu.
 *
 * StatFs ile disk bilgilerini, MediaStore ile medya boyutlarını,
 * PackageManager ile uygulama boyutlarını toplar.
 *
 * Sonuç kısa süreli olarak önbelleğe alınır: bu analiz yüzlerce dosya sistemi
 * çağrısı içerir ve Dashboard her açılışında tetiklenir.
 */
@Singleton
class StorageAnalyzerImpl @Inject constructor(
    private val application: Application
) : StorageAnalyzer {

    private companion object {
        const val TAG = "StorageAnalyzer"

        /** Önbellek ömrü — bu süre içindeki tekrar çağrılar diskten okumaz. */
        const val CACHE_TTL_MS = 60_000L

        /** Belgeler/İndirilenler için gezilecek en fazla klasör derinliği. */
        const val DOCS_MAX_DEPTH = 5

        /** Klasör boyutu hesabında gezilecek en fazla dosya sayısı. */
        const val MAX_FILES_PER_DIR = 20_000
    }

    private val cacheMutex = Mutex()
    private var cachedInfo: StorageInfo? = null
    private var cachedAt = 0L

    /**
     * Cihazın depolama bilgisini analiz eder ve [StorageInfo] döndürür.
     *
     * @param forceRefresh true ise önbellek yok sayılır
     */
    override suspend fun analyze(forceRefresh: Boolean): StorageInfo = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            val cached = cachedInfo
            if (!forceRefresh && cached != null &&
                System.currentTimeMillis() - cachedAt < CACHE_TTL_MS
            ) {
                return@withLock cached
            }

            val fresh = computeStorageInfo()
            cachedInfo = fresh
            cachedAt = System.currentTimeMillis()
            fresh
        }
    }

    private suspend fun computeStorageInfo(): StorageInfo {
        return try {
            val statFs = StatFs(Environment.getExternalStorageDirectory().absolutePath)
            val totalSpace = statFs.totalBytes
            val freeSpace = statFs.availableBytes
            val usedSpace = totalSpace - freeSpace

            val photosSize = queryMediaSize(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Images.Media.SIZE
            )
            val videosSize = queryMediaSize(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.SIZE
            )
            val audioSize = queryMediaSize(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Media.SIZE
            )

            val appsSize = calculateAppsSize()
            val cacheSize = calculateCacheSize()
            val documentsSize = calculateDocumentsSize()

            val knownSize = photosSize + videosSize + audioSize + appsSize + cacheSize + documentsSize
            val otherSize = (usedSpace - knownSize).coerceAtLeast(0L)

            StorageInfo(
                totalSpace = totalSpace,
                usedSpace = usedSpace,
                freeSpace = freeSpace,
                photosSize = photosSize,
                videosSize = videosSize,
                appsSize = appsSize,
                cacheSize = cacheSize,
                audioSize = audioSize,
                documentsSize = documentsSize,
                otherSize = otherSize
            )
        } catch (e: Exception) {
            Log.e(TAG, "Depolama analizi başarısız", e)
            StorageInfo()
        }
    }

    /**
     * MediaStore üzerinden belirtilen medya türünün toplam boyutunu sorgular.
     */
    private fun queryMediaSize(uri: android.net.Uri, sizeColumn: String): Long {
        return try {
            val projection = arrayOf("SUM($sizeColumn) AS total_size")
            application.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            } ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Medya boyutu sorgulanamadı: $uri", e)
            0L
        }
    }

    /**
     * Tüm yüklü uygulamaların APK boyutlarının toplamını hesaplar.
     */
    private suspend fun calculateAppsSize(): Long {
        var totalSize = 0L
        try {
            val packages = application.packageManager.getInstalledApplications(0)
            for (appInfo in packages) {
                coroutineContext.ensureActive()
                try {
                    appInfo.sourceDir?.let { totalSize += File(it).length() }
                } catch (e: Exception) {
                    // Tek bir uygulama okunamazsa atla
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Uygulama boyutları hesaplanamadı", e)
        }
        return totalSize
    }

    /**
     * Uygulamanın kendi önbellek klasörlerinin toplam boyutunu hesaplar.
     */
    private suspend fun calculateCacheSize(): Long {
        return try {
            getDirSize(application.cacheDir) + (application.externalCacheDir?.let { getDirSize(it) } ?: 0L)
        } catch (e: Exception) {
            Log.w(TAG, "Önbellek boyutu hesaplanamadı", e)
            0L
        }
    }

    /**
     * Belgeler ve İndirilenler klasörlerinin boyutunu hesaplar.
     */
    private suspend fun calculateDocumentsSize(): Long {
        return try {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            getDirSize(docsDir) + getDirSize(downloadsDir)
        } catch (e: Exception) {
            Log.w(TAG, "Belge boyutu hesaplanamadı", e)
            0L
        }
    }

    /**
     * Dizin boyutunu sınırlı derinlik ve dosya sayısıyla hesaplar.
     *
     * Sınırsız `walkTopDown` derin klasör ağaçlarında taramayı dakikalarca uzatabiliyordu.
     */
    private suspend fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.isDirectory) return 0L
        var size = 0L
        var visited = 0
        try {
            for (file in dir.walkTopDown().maxDepth(DOCS_MAX_DEPTH)) {
                if (++visited % 512 == 0) coroutineContext.ensureActive()
                if (visited > MAX_FILES_PER_DIR) break
                if (file.isFile) size += file.length()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Dizin boyutu kısmi hesaplandı: ${dir.path}", e)
        }
        return size
    }
}
