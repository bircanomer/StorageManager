package com.storagemanager.data.repository

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import com.storagemanager.domain.model.*
import com.storagemanager.domain.repository.StorageRepository
import com.storagemanager.scanner.AppAnalyzer
import com.storagemanager.scanner.FileAnalyzer
import com.storagemanager.scanner.PhotoAnalyzer
import com.storagemanager.scanner.StorageAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * StorageRepository implementasyonu.
 *
 * Tüm analizörleri koordine eder, tam tarama yapar, ilerleme raporlar
 * ve dosya/fotoğraf silme işlemlerini yönetir.
 */
@Singleton
class StorageRepositoryImpl @Inject constructor(
    private val application: Application,
    private val storageAnalyzer: StorageAnalyzer,
    private val photoAnalyzer: PhotoAnalyzer,
    private val appAnalyzer: AppAnalyzer,
    private val fileAnalyzer: FileAnalyzer
) : StorageRepository {

    /**
     * Cihaz depolama bilgisini döndürür.
     */
    override suspend fun getStorageInfo(): StorageInfo {
        return storageAnalyzer.analyze()
    }

    /**
     * Tüm gereksiz fotoğrafları tarar (bulanık, duplike, ekran görüntüsü, eski).
     *
     * @return Tüm kategorilerdeki gereksiz fotoğrafların birleştirilmiş listesi
     */
    override suspend fun scanJunkPhotos(): List<JunkPhoto> = withContext(Dispatchers.IO) {
        try {
            val blurry = photoAnalyzer.findBlurryPhotos()
            val duplicates = photoAnalyzer.findDuplicatePhotos()
            val screenshots = photoAnalyzer.findScreenshots()
            val old = photoAnalyzer.findOldPhotos()
            blurry + duplicates + screenshots + old
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Kullanılmamış uygulamaları listeler.
     *
     * @param daysSinceLastUse Son kullanımdan bu yana geçen gün sayısı
     */
    override suspend fun scanUnusedApps(daysSinceLastUse: Int): List<UnusedApp> {
        return appAnalyzer.findUnusedApps(daysSinceLastUse)
    }

    /**
     * Tüm uygulamaların önbellek bilgilerini döndürür.
     */
    override suspend fun getCacheInfo(): List<CacheInfo> {
        return appAnalyzer.getCacheInfo()
    }

    /**
     * Büyük dosyaları listeler.
     *
     * @param minSizeBytes Minimum dosya boyutu
     */
    override suspend fun scanLargeFiles(minSizeBytes: Long): List<LargeFile> {
        return fileAnalyzer.findLargeFiles(minSizeBytes)
    }

    /**
     * Seçili fotoğrafları ContentResolver üzerinden siler.
     *
     * @param photos Silinecek fotoğrafların listesi
     * @return Başarıyla silinen fotoğraf sayısı
     */
    override suspend fun deletePhotos(photos: List<JunkPhoto>): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var deletedCount = 0
            val contentResolver = application.contentResolver

            for (photo in photos) {
                try {
                    val uri = Uri.parse(photo.uri)
                    val rowsDeleted = contentResolver.delete(uri, null, null)
                    if (rowsDeleted > 0) {
                        deletedCount++
                    }
                } catch (_: Exception) {
                    // Tek bir fotoğraf silinemezse devam et
                }
            }
            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Seçili dosyaları dosya sisteminden siler.
     *
     * @param files Silinecek dosyaların listesi
     * @return Başarıyla silinen dosya sayısı
     */
    override suspend fun deleteFiles(files: List<LargeFile>): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var deletedCount = 0

            for (largeFile in files) {
                try {
                    val file = File(largeFile.path)
                    if (file.exists() && file.delete()) {
                        deletedCount++

                        // MediaStore'dan da kaldır
                        try {
                            application.contentResolver.delete(
                                MediaStore.Files.getContentUri("external"),
                                "${MediaStore.Files.FileColumns.DATA} = ?",
                                arrayOf(largeFile.path)
                            )
                        } catch (_: Exception) {
                            // MediaStore'dan kaldırma başarısız olsa bile dosya silindi
                        }
                    }
                } catch (_: Exception) {
                    // Tek bir dosya silinemezse devam et
                }
            }
            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Tam tarama yapar — tüm analizörleri sırayla çalıştırır ve ilerleme bildirir.
     *
     * İlerleme aralıkları:
     * - %0-20: Depolama analizi
     * - %20-60: Fotoğraf taraması
     * - %60-75: Uygulama kontrolü
     * - %75-90: Büyük dosya araması
     * - %90-100: Önbellek bilgileri
     *
     * @param onProgress İlerleme callback'i (yüzde, durum mesajı)
     * @return Tam tarama sonucu
     */
    override suspend fun fullScan(onProgress: (Float, String) -> Unit): ScanResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // 1. Depolama analizi (%0-20)
        onProgress(0f, "Depolama analiz ediliyor...")
        val storageInfo = try {
            storageAnalyzer.analyze()
        } catch (e: Exception) {
            StorageInfo()
        }
        onProgress(0.20f, "Depolama analizi tamamlandı")

        // 2. Fotoğraf taraması (%20-60)
        onProgress(0.20f, "Fotoğraflar taranıyor...")
        val junkPhotos = try {
            val blurry = photoAnalyzer.findBlurryPhotos()
            onProgress(0.30f, "Bulanık fotoğraflar bulundu: ${blurry.size}")

            val duplicates = photoAnalyzer.findDuplicatePhotos()
            onProgress(0.40f, "Duplike fotoğraflar bulundu: ${duplicates.size}")

            val screenshots = photoAnalyzer.findScreenshots()
            onProgress(0.50f, "Ekran görüntüleri bulundu: ${screenshots.size}")

            val old = photoAnalyzer.findOldPhotos()
            onProgress(0.60f, "Eski fotoğraflar bulundu: ${old.size}")

            blurry + duplicates + screenshots + old
        } catch (e: Exception) {
            emptyList()
        }

        // 3. Uygulama kontrolü (%60-75)
        onProgress(0.60f, "Uygulamalar kontrol ediliyor...")
        val unusedApps = try {
            appAnalyzer.findUnusedApps()
        } catch (e: Exception) {
            emptyList()
        }
        onProgress(0.75f, "Kullanılmayan uygulamalar bulundu: ${unusedApps.size}")

        // 4. Büyük dosya araması (%75-90)
        onProgress(0.75f, "Büyük dosyalar aranıyor...")
        val largeFiles = try {
            fileAnalyzer.findLargeFiles()
        } catch (e: Exception) {
            emptyList()
        }
        onProgress(0.90f, "Büyük dosyalar bulundu: ${largeFiles.size}")

        // 5. Önbellek bilgileri (%90-100)
        onProgress(0.90f, "Önbellek bilgileri alınıyor...")
        val cacheInfos = try {
            appAnalyzer.getCacheInfo()
        } catch (e: Exception) {
            emptyList()
        }
        onProgress(1.0f, "Tarama tamamlandı")

        // Toplam temizlenebilir alan hesapla
        val totalCleanableSize = junkPhotos.sumOf { it.size } +
                unusedApps.sumOf { it.appSize + it.cacheSize } +
                cacheInfos.sumOf { it.cacheSize } +
                largeFiles.sumOf { it.size }

        val scanDuration = System.currentTimeMillis() - startTime

        ScanResult(
            storageInfo = storageInfo,
            junkPhotos = junkPhotos,
            unusedApps = unusedApps,
            cacheInfos = cacheInfos,
            largeFiles = largeFiles,
            totalCleanableSize = totalCleanableSize,
            scanDurationMs = scanDuration
        )
    }
}
