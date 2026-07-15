package com.storagemanager.scanner

import android.app.Application
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.storagemanager.domain.model.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cihaz depolama alanı analiz implementasyonu.
 *
 * StatFs ile disk bilgilerini, MediaStore ile medya boyutlarını,
 * PackageManager ile uygulama boyutlarını toplar.
 */
@Singleton
class StorageAnalyzerImpl @Inject constructor(
    private val application: Application
) : StorageAnalyzer {

    /**
     * Cihazın depolama bilgisini analiz eder ve [StorageInfo] döndürür.
     *
     * @return Disk kullanım dağılımını içeren StorageInfo nesnesi
     */
    override suspend fun analyze(): StorageInfo = withContext(Dispatchers.IO) {
        try {
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
            StorageInfo()
        }
    }

    /**
     * MediaStore üzerinden belirtilen medya türünün toplam boyutunu sorgular.
     *
     * @param uri MediaStore content URI'si
     * @param sizeColumn Boyut sütunu adı
     * @return Toplam boyut (byte)
     */
    private fun queryMediaSize(uri: android.net.Uri, sizeColumn: String): Long {
        var totalSize = 0L
        try {
            val projection = arrayOf("SUM($sizeColumn) AS total_size")
            application.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    totalSize = cursor.getLong(0)
                }
            }
        } catch (e: Exception) {
            // MediaStore sorgusu başarısız olursa 0 döndür
        }
        return totalSize
    }

    /**
     * Tüm yüklü uygulamaların toplam boyutunu hesaplar.
     *
     * @return Uygulama boyutlarının toplamı (byte)
     */
    private fun calculateAppsSize(): Long {
        var totalSize = 0L
        try {
            val packages = application.packageManager.getInstalledApplications(0)
            for (appInfo in packages) {
                try {
                    appInfo.sourceDir?.let { sourceDir ->
                        val file = java.io.File(sourceDir)
                        if (file.exists()) {
                            totalSize += file.length()
                        }
                    }
                } catch (_: Exception) {
                    // Tek bir uygulama okunamazsa atla
                }
            }
        } catch (e: Exception) {
            // PackageManager erişimi başarısız olursa 0 döndür
        }
        return totalSize
    }

    /**
     * Tüm uygulamaların toplam önbellek boyutunu hesaplar.
     *
     * @return Toplam önbellek boyutu (byte)
     */
    private fun calculateCacheSize(): Long {
        var totalSize = 0L
        try {
            val cacheDir = application.cacheDir
            if (cacheDir.exists()) {
                totalSize += getDirSize(cacheDir)
            }
            application.externalCacheDir?.let { extCache ->
                if (extCache.exists()) {
                    totalSize += getDirSize(extCache)
                }
            }
        } catch (e: Exception) {
            // Önbellek boyutu hesaplanamazsa 0 döndür
        }
        return totalSize
    }

    /**
     * Belgeler klasörünün boyutunu hesaplar.
     *
     * @return Belgeler boyutu (byte)
     */
    private fun calculateDocumentsSize(): Long {
        return try {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            getDirSize(docsDir) + getDirSize(downloadsDir)
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Bir dizinin toplam boyutunu özyinelemeli olarak hesaplar.
     *
     * @param dir Hedef dizin
     * @return Dizin boyutu (byte)
     */
    private fun getDirSize(dir: java.io.File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        try {
            dir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    size += file.length()
                }
            }
        } catch (_: Exception) {
            // Dosya erişim hatalarını yoksay
        }
        return size
    }
}
