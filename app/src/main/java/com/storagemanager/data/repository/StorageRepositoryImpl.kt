package com.storagemanager.data.repository

import android.app.Application
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.storagemanager.data.local.CachedJunkPhoto
import com.storagemanager.data.local.CachedScanResults
import com.storagemanager.data.local.CachedStorageInfo
import com.storagemanager.data.local.ScanResultDao
import com.storagemanager.data.local.TrashDao
import com.storagemanager.data.local.TrashItemEntity
import com.storagemanager.data.preferences.ScanSettings
import com.storagemanager.domain.model.*
import com.storagemanager.domain.repository.StorageRepository
import com.storagemanager.scanner.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import com.storagemanager.R

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
    private val fileAnalyzer: FileAnalyzer,
    private val messengerAnalyzer: MessengerAnalyzer,
    private val documentAnalyzer: DocumentAnalyzer,
    private val systemJunkAnalyzer: SystemJunkAnalyzer,
    private val treemapAnalyzer: TreemapAnalyzer,
    private val scanResultDao: ScanResultDao,
    private val trashDao: TrashDao,
    private val scanSettings: ScanSettings,
    private val gson: com.google.gson.Gson
) : StorageRepository {

    private companion object {
        const val TAG = "StorageRepo"

        /** SQLite'ın sorgu başına bağlanabilir değişken sınırı (999) için güvenli parça boyutu. */
        const val SQL_VARIABLE_LIMIT = 500

        /** Henüz taranmamış kategorilerin JSON karşılığı. */
        const val EMPTY_JSON_ARRAY = "[]"
    }

    override suspend fun getStorageInfo(): StorageInfo = storageAnalyzer.analyze()

    override suspend fun scanJunkPhotos(): List<JunkPhoto> = withContext(Dispatchers.IO) {
        try {
            val photos = photoAnalyzer.scanAllPhotos()
            persistJunkPhotos(photos)
            photos
        } catch (e: Exception) {
            Log.e(TAG, "Fotoğraf taraması başarısız", e)
            emptyList()
        }
    }

    override suspend fun previewBlurryPhotos(sampleSize: Int, threshold: Float): List<JunkPhoto> =
        withContext(Dispatchers.IO) {
            try {
                photoAnalyzer.previewBlurry(sampleSize, threshold.toDouble())
            } catch (e: Exception) {
                Log.w(TAG, "Bulanıklık önizlemesi başarısız", e)
                emptyList()
            }
        }

    /**
     * Yalnızca fotoğraf sonuçlarını önbelleğe yazar, diğer kategorilere dokunmaz.
     *
     * Fotoğraflar ekranından başlatılan tarama eskiden hiç kaydedilmiyordu: dakikalarca
     * süren analiz uygulama kapanınca kayboluyor, dashboard da eski sayıyı göstermeye
     * devam ediyordu. [persist] burada kullanılamaz çünkü tüm [ScanResult]'ı yazar ve
     * elimizde yalnızca fotoğraflar olduğu için diğer kategorileri ezerdi.
     */
    private suspend fun persistJunkPhotos(photos: List<JunkPhoto>) {
        try {
            scanResultDao.replaceJunkPhotos(photos.map { it.toEntity() })

            // getCachedScanResults() satır yoksa null döner; hiç tam tarama yapılmadıysa
            // fotoğraflar yazılmış olsa bile okunamazdı. Boş bir kayıtla bunu garantiye al.
            if (scanResultDao.getScanResults() == null) {
                scanResultDao.insertScanResults(
                    CachedScanResults(
                        largeFilesJson = EMPTY_JSON_ARRAY,
                        unusedAppsJson = EMPTY_JSON_ARRAY,
                        cacheInfosJson = EMPTY_JSON_ARRAY
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fotoğraf önbelleği yazılamadı", e)
        }
    }

    override suspend fun scanUnusedApps(daysSinceLastUse: Int): List<UnusedApp> =
        appAnalyzer.findUnusedApps(daysSinceLastUse)

    override suspend fun getCacheInfo(): List<CacheInfo> = appAnalyzer.getCacheInfo()

    override suspend fun scanLargeFiles(minSizeBytes: Long): List<LargeFile> =
        fileAnalyzer.findLargeFiles(minSizeBytes)

    override suspend fun scanMessengerJunk(): List<MessengerJunk> =
        messengerAnalyzer.scanMessengerJunk()

    override suspend fun scanDuplicateDocuments(): List<DuplicateDocumentGroup> =
        documentAnalyzer.findDuplicateDocuments()

    override suspend fun scanDownloadJunk(): List<DownloadJunk> = documentAnalyzer.findDownloadJunk()

    override suspend fun scanSystemJunk(): List<SystemJunk> = systemJunkAnalyzer.findSystemJunk()

    override suspend fun generateTreemap(maxDepth: Int): StorageTreeNode =
        treemapAnalyzer.generateTreemap(maxDepth)

    override suspend fun deletePhotos(photos: List<JunkPhoto>): Result<Int> = withContext(Dispatchers.IO) {
        if (photos.isEmpty()) return@withContext Result.success(0)
        try {
            var deletedCount = 0
            var failCount = 0
            val contentResolver = application.contentResolver

            for (photo in photos) {
                try {
                    val file = File(photo.path)
                    if (file.exists() && file.delete()) {
                        deletedCount++
                        try {
                            contentResolver.delete(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                "${MediaStore.Images.Media._ID} = ?",
                                arrayOf(photo.id.toString())
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "MediaStore kaydı silinemedi: ${photo.id}", e)
                        }
                    } else {
                        val rowsDeleted = contentResolver.delete(Uri.parse(photo.uri), null, null)
                        if (rowsDeleted > 0) deletedCount++ else failCount++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Fotoğraf silinemedi: ${photo.path}", e)
                    failCount++
                }
            }

            // Hiçbiri silinemediyse çağıran taraf sistem izin diyaloğuna (createTrashRequest) düşer.
            if (deletedCount > 0) {
                Result.success(deletedCount)
            } else {
                Log.i(TAG, "$failCount fotoğraf doğrudan silinemedi, sistem izni gerekiyor")
                Result.failure(SecurityException("PendingIntentRequired"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fotoğraf silme işlemi başarısız", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteFiles(files: List<LargeFile>): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var deletedCount = 0
            val deletedPaths = mutableSetOf<String>()
            val deletedSizes = mutableMapOf<String, Long>()
            val deletedMimeTypes = mutableMapOf<String, String?>()

            for (largeFile in files) {
                try {
                    val file = File(largeFile.path)
                    if (file.exists() && file.delete()) {
                        deletedCount++
                        deletedPaths += largeFile.path
                        deletedSizes[largeFile.path] = largeFile.size
                        deletedMimeTypes[largeFile.path] = largeFile.mimeType

                        try {
                            application.contentResolver.delete(
                                MediaStore.Files.getContentUri("external"),
                                "${MediaStore.Files.FileColumns.DATA} = ?",
                                arrayOf(largeFile.path)
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "MediaStore kaydı silinemedi: ${largeFile.path}", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Dosya silinemedi: ${largeFile.path}", e)
                }
            }

            if (deletedPaths.isNotEmpty()) {
                updateCacheAfterDeletion(deletedPaths, deletedSizes, deletedMimeTypes)
            }

            Result.success(deletedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Dosya silme işlemi başarısız", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteSystemJunk(junks: List<SystemJunk>): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var deletedCount = 0
            for (junk in junks) {
                try {
                    val file = File(junk.path)
                    if (!file.exists()) continue
                    val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
                    if (deleted) deletedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Sistem çöpü silinemedi: ${junk.path}", e)
                }
            }
            Result.success(deletedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Sistem çöpü silme işlemi başarısız", e)
            Result.failure(e)
        }
    }

    // ── Trash & Recycle Bin ─────────────────────────────────────────────────

    /**
     * Çöp kutusu dizini.
     *
     * Uygulamaya özel **harici** dizin kullanılır: kullanıcı dosyalarıyla aynı bölümde
     * olduğu için taşıma `rename` ile anında yapılır (kopyalama sırasında geçici olarak
     * iki kat yer tutulmaz) ve dahili depolama kotası şişmez.
     */
    private fun getTrashDir(): File {
        val base = application.getExternalFilesDir(null) ?: application.filesDir
        return File(base, "recycle_bin").apply { if (!exists()) mkdirs() }
    }

    override fun getTrashItems(): Flow<List<TrashItem>> {
        return trashDao.getAllTrashItemsFlow().map { entities ->
            entities.map { entity ->
                TrashItem(
                    id = entity.id,
                    originalPath = entity.originalPath,
                    trashPath = entity.trashPath,
                    fileName = entity.fileName,
                    fileSize = entity.fileSize,
                    deletedTimestamp = entity.deletedTimestamp,
                    mimeType = entity.mimeType,
                    itemType = entity.itemType
                )
            }
        }
    }

    override suspend fun moveToTrash(filePath: String, itemType: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(filePath)
            if (!sourceFile.exists()) return@withContext Result.failure(Exception("Dosya bulunamadı"))
            val size = sourceFile.length()

            val destFile = File(getTrashDir(), "${System.currentTimeMillis()}_${sourceFile.name}")
            val moved = sourceFile.renameTo(destFile) || try {
                sourceFile.copyTo(destFile, overwrite = true)
                sourceFile.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Çöp kutusuna kopyalama başarısız: $filePath", e)
                false
            }

            if (!moved) return@withContext Result.failure(Exception("Dosya çöp kutusuna taşınamadı"))

            trashDao.insertTrashItem(
                TrashItemEntity(
                    originalPath = sourceFile.absolutePath,
                    trashPath = destFile.absolutePath,
                    fileName = sourceFile.name,
                    fileSize = destFile.length(),
                    deletedTimestamp = System.currentTimeMillis(),
                    itemType = itemType
                )
            )

            updateCacheAfterDeletion(
                deletedPaths = setOf(filePath),
                deletedSizes = mapOf(filePath to size),
                deletedMimeTypes = mapOf(filePath to null)
            )

            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Çöp kutusuna taşıma başarısız: $filePath", e)
            Result.failure(e)
        }
    }

    override suspend fun restoreFromTrash(trashId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val entity = trashDao.getTrashItemById(trashId)
                ?: return@withContext Result.failure(Exception("Öge çöp kutusunda yok"))
            val trashFile = File(entity.trashPath)
            val destFile = File(entity.originalPath)

            destFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

            val restored = trashFile.renameTo(destFile) || try {
                trashFile.copyTo(destFile, overwrite = true)
                trashFile.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Geri yükleme kopyalaması başarısız: ${entity.trashPath}", e)
                false
            }

            if (restored) {
                trashDao.deleteTrashItem(trashId)
                Result.success(true)
            } else {
                Result.failure(Exception("Dosya geri yüklenemedi"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geri yükleme başarısız: $trashId", e)
            Result.failure(e)
        }
    }

    override suspend fun deletePermanentlyFromTrash(trashId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val entity = trashDao.getTrashItemById(trashId)
                ?: return@withContext Result.failure(Exception("Öge bulunamadı"))
            File(entity.trashPath).takeIf { it.exists() }?.delete()
            trashDao.deleteTrashItem(trashId)
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Kalıcı silme başarısız: $trashId", e)
            Result.failure(e)
        }
    }

    override suspend fun clearTrash(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            trashDao.getAllTrashItems().forEach { item ->
                File(item.trashPath).takeIf { it.exists() }?.delete()
            }
            trashDao.clearTrash()
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Çöp kutusu boşaltılamadı", e)
            Result.failure(e)
        }
    }

    /**
     * Belirtilen günden eski çöp kutusu ögelerini kalıcı olarak siler.
     *
     * Çöp kutusu daha önce hiç temizlenmiyordu; silinen her dosya cihazda süresiz kalıyordu.
     */
    override suspend fun cleanupOldTrash(maxAgeDays: Int): Int = withContext(Dispatchers.IO) {
        try {
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(maxAgeDays.toLong())
            val expired = trashDao.getItemsOlderThan(cutoff)
            var removed = 0
            for (item in expired) {
                try {
                    File(item.trashPath).takeIf { it.exists() }?.delete()
                    trashDao.deleteTrashItem(item.id)
                    removed++
                } catch (e: Exception) {
                    Log.w(TAG, "Süresi dolan öge silinemedi: ${item.trashPath}", e)
                }
            }
            if (removed > 0) Log.i(TAG, "$removed adet süresi dolmuş çöp kutusu ögesi silindi")
            removed
        } catch (e: Exception) {
            Log.e(TAG, "Çöp kutusu bakımı başarısız", e)
            0
        }
    }

    // ── Tarama ──────────────────────────────────────────────────────────────

    override suspend fun fullScan(
        onProgress: (Float, String) -> Unit,
        onPartialResult: (ScanResult) -> Unit
    ): ScanResult = customScan(ScanCategory.entries.toSet(), onProgress, onPartialResult)

    override suspend fun customScan(
        categories: Set<ScanCategory>,
        onProgress: (Float, String) -> Unit,
        onPartialResult: (ScanResult) -> Unit
    ): ScanResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val settings = scanSettings.current()

        // Her kategori önceki tarama sonucuyla başlar ve yalnızca kendi adımı bitince
        // güncellenir. Böylece ne kapsam dışı kategoriler ne de tarama yarıda kesildiğinde
        // sırası gelmemiş kategoriler önbellekten silinir; en kötü ihtimalle eski değer kalır.
        val previous = runCatching { getCachedScanResults() }.getOrNull()

        var junkPhotos = previous?.junkPhotos.orEmpty()
        var unusedApps = previous?.unusedApps.orEmpty()
        var cacheInfos = previous?.cacheInfos.orEmpty()
        var largeFiles = previous?.largeFiles.orEmpty()
        var duplicateDocs = previous?.duplicateDocuments.orEmpty()
        var downloadJunks = previous?.downloadJunks.orEmpty()
        var systemJunks = previous?.systemJunks.orEmpty()

        var storageInfo = previous?.storageInfo ?: runCatching { storageAnalyzer.analyze() }
            .getOrDefault(StorageInfo())

        val stepWeight = 1.0f / categories.size.coerceAtLeast(1)
        var currentProgress = 0f

        fun snapshot(): ScanResult = ScanResult(
            storageInfo = storageInfo,
            junkPhotos = junkPhotos,
            unusedApps = unusedApps,
            cacheInfos = cacheInfos,
            largeFiles = largeFiles,
            duplicateDocuments = duplicateDocs,
            downloadJunks = downloadJunks,
            systemJunks = systemJunks,
            totalCleanableSize = totalCleanableSize(
                junkPhotos, unusedApps, cacheInfos, largeFiles, downloadJunks, systemJunks
            ),
            scanDurationMs = System.currentTimeMillis() - startTime
        )

        // Bir kategori biter bitmez sonucu kalıcılaştır ve arayüze bildir: tarama
        // yarıda kesilirse (uygulama kapatılsa bile) biten kategoriler kaybolmaz.
        suspend fun publishPartial() {
            val partial = snapshot()
            persist(partial)
            onPartialResult(partial)
        }

        // Sıra bilinçli olarak ucuzdan pahalıya: AI foto taraması dakikalar sürebildiği
        // için en sonda; büyük dosyalar gibi hızlı kategoriler saniyeler içinde görünür.
        if (ScanCategory.CACHE in categories) {
            onProgress(currentProgress, application.getString(R.string.progress_cache))
            cacheInfos = runCatching { appAnalyzer.getCacheInfo() }
                .onFailure { Log.e(TAG, "Önbellek taraması başarısız", it) }
                .getOrDefault(emptyList())
            currentProgress += stepWeight
            onProgress(currentProgress, application.getString(R.string.progress_cache_done, cacheInfos.size))
            publishPartial()
        }

        if (ScanCategory.UNUSED_APPS in categories) {
            onProgress(currentProgress, application.getString(R.string.progress_apps))
            // Ayarlardaki gün eşiği artık gerçekten kullanılıyor
            unusedApps = runCatching { appAnalyzer.findUnusedApps(settings.unusedAppDays) }
                .onFailure { Log.e(TAG, "Uygulama taraması başarısız", it) }
                .getOrDefault(emptyList())
            currentProgress += stepWeight
            onProgress(currentProgress, application.getString(R.string.progress_apps_done, unusedApps.size))
            publishPartial()
        }

        if (ScanCategory.LARGE_FILES in categories) {
            onProgress(currentProgress, application.getString(R.string.progress_large_files))
            largeFiles = runCatching { fileAnalyzer.findLargeFiles(settings.minLargeFileSizeBytes) }
                .onFailure { Log.e(TAG, "Büyük dosya taraması başarısız", it) }
                .getOrDefault(emptyList())
            currentProgress += stepWeight
            onProgress(currentProgress, application.getString(R.string.progress_large_files_done, largeFiles.size))
            publishPartial()
        }

        if (ScanCategory.DOWNLOADS in categories) {
            onProgress(currentProgress, application.getString(R.string.progress_downloads))
            duplicateDocs = runCatching { documentAnalyzer.findDuplicateDocuments() }
                .onFailure { Log.e(TAG, "Duplike belge taraması başarısız", it) }
                .getOrDefault(emptyList())
            downloadJunks = runCatching { documentAnalyzer.findDownloadJunk() }
                .onFailure { Log.e(TAG, "İndirilenler taraması başarısız", it) }
                .getOrDefault(emptyList())
            currentProgress += stepWeight
            onProgress(currentProgress, application.getString(R.string.progress_downloads_done))
            publishPartial()
        }

        if (ScanCategory.SYSTEM_JUNK in categories) {
            onProgress(currentProgress, application.getString(R.string.progress_system_junk))
            systemJunks = runCatching { systemJunkAnalyzer.findSystemJunk() }
                .onFailure { Log.e(TAG, "Sistem çöpü taraması başarısız", it) }
                .getOrDefault(emptyList())
            currentProgress += stepWeight
            onProgress(currentProgress, application.getString(R.string.progress_system_junk_done, systemJunks.size))
            publishPartial()
        }

        // En pahalı adım en sonda: buraya gelene kadar diğer kategoriler çoktan ekranda.
        if (ScanCategory.PHOTOS in categories) {
            val startP = currentProgress
            onProgress(startP, application.getString(R.string.progress_photos_scanning, 0))
            junkPhotos = runCatching {
                photoAnalyzer.scanAllPhotos { p, msg ->
                    onProgress((startP + stepWeight * p).coerceIn(0f, 1f), msg)
                }
            }.onFailure { Log.e(TAG, "Fotoğraf taraması başarısız", it) }.getOrDefault(emptyList())
            currentProgress += stepWeight
            onProgress(currentProgress, application.getString(R.string.progress_photos_done, junkPhotos.size))
            publishPartial()
        }

        // Silmeler taramadan sonra yapılacağı için depolama bilgisi taze okunmalı
        storageInfo = runCatching { storageAnalyzer.analyze(forceRefresh = true) }
            .onFailure { Log.e(TAG, "Depolama analizi başarısız", it) }
            .getOrDefault(storageInfo)

        onProgress(1.0f, application.getString(R.string.progress_complete))

        val result = snapshot()
        persist(result)
        result
    }

    private suspend fun persist(result: ScanResult) {
        try {
            scanResultDao.insertStorageInfo(
                CachedStorageInfo(
                    totalBytes = result.storageInfo.totalSpace,
                    freeBytes = result.storageInfo.freeSpace,
                    photosBytes = result.storageInfo.photosSize,
                    videosBytes = result.storageInfo.videosSize,
                    appsBytes = result.storageInfo.appsSize,
                    cacheBytes = result.storageInfo.cacheSize,
                    audioBytes = result.storageInfo.audioSize,
                    docsBytes = result.storageInfo.documentsSize,
                    otherBytes = result.storageInfo.otherSize,
                    lastScanTime = System.currentTimeMillis()
                )
            )

            scanResultDao.insertScanResults(
                CachedScanResults(
                    largeFilesJson = gson.toJson(result.largeFiles),
                    unusedAppsJson = gson.toJson(result.unusedApps),
                    cacheInfosJson = gson.toJson(result.cacheInfos),
                    duplicateDocsJson = gson.toJson(result.duplicateDocuments),
                    downloadJunksJson = gson.toJson(result.downloadJunks),
                    systemJunksJson = gson.toJson(result.systemJunks)
                )
            )

            scanResultDao.replaceJunkPhotos(result.junkPhotos.map { it.toEntity() })
        } catch (e: Exception) {
            Log.w(TAG, "Tarama sonucu kaydedilemedi", e)
        }
    }

    override suspend fun getCachedStorageInfo(): StorageInfo? = withContext(Dispatchers.IO) {
        try {
            scanResultDao.getStorageInfo()?.toStorageInfo()
        } catch (e: Exception) {
            Log.w(TAG, "Önbellekli depolama bilgisi okunamadı", e)
            null
        }
    }

    override suspend fun getCachedScanResults(): ScanResult? = withContext(Dispatchers.IO) {
        try {
            val entity = scanResultDao.getScanResults() ?: return@withContext null
            val storageInfo = scanResultDao.getStorageInfo()?.toStorageInfo() ?: StorageInfo()

            val junkPhotos = scanResultDao.getJunkPhotos().mapNotNull { it.toDomain() }
            val largeFiles: List<LargeFile> = fromJson(entity.largeFilesJson)
            val unusedApps: List<UnusedApp> = fromJson(entity.unusedAppsJson)
            val cacheInfos: List<CacheInfo> = fromJson(entity.cacheInfosJson)
            val duplicateDocs: List<DuplicateDocumentGroup> = fromJson(entity.duplicateDocsJson)
            val downloadJunks: List<DownloadJunk> = fromJson(entity.downloadJunksJson)
            val systemJunks: List<SystemJunk> = fromJson(entity.systemJunksJson)

            ScanResult(
                storageInfo = storageInfo,
                junkPhotos = junkPhotos,
                unusedApps = unusedApps,
                cacheInfos = cacheInfos,
                largeFiles = largeFiles,
                duplicateDocuments = duplicateDocs,
                downloadJunks = downloadJunks,
                systemJunks = systemJunks,
                totalCleanableSize = totalCleanableSize(
                    junkPhotos, unusedApps, cacheInfos, largeFiles, downloadJunks, systemJunks
                ),
                scanDurationMs = 0L
            )
        } catch (e: Exception) {
            Log.w(TAG, "Önbellekli tarama sonucu okunamadı", e)
            null
        }
    }

    /**
     * Silinen fotoğrafları önbellekten çıkarır.
     *
     * Artık tüm liste yeniden serileştirilmiyor; tek bir DELETE sorgusu yeterli.
     */
    override suspend fun removePhotosFromCache(photoIds: Set<Long>): Unit = withContext(Dispatchers.IO) {
        if (photoIds.isEmpty()) return@withContext
        try {
            // SQLite'ın sorgu başına değişken sınırına (999) takılmamak için parçalara ayır
            val chunks = photoIds.chunked(SQL_VARIABLE_LIMIT)
            var deletedSize = 0L
            for (chunk in chunks) {
                deletedSize += scanResultDao.sumSizeOf(chunk)
                scanResultDao.deleteJunkPhotos(chunk)
            }

            val storageInfoEntity = scanResultDao.getStorageInfo()
            if (storageInfoEntity != null && deletedSize > 0) {
                scanResultDao.insertStorageInfo(
                    storageInfoEntity.copy(
                        freeBytes = storageInfoEntity.freeBytes + deletedSize,
                        photosBytes = (storageInfoEntity.photosBytes - deletedSize).coerceAtLeast(0L)
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fotoğraf önbelleği güncellenemedi", e)
        }
    }

    private suspend fun updateCacheAfterDeletion(
        deletedPaths: Set<String>,
        deletedSizes: Map<String, Long>,
        deletedMimeTypes: Map<String, String?> = emptyMap()
    ) = withContext(Dispatchers.IO) {
        try {
            scanResultDao.getScanResults()?.let { entity ->
                val largeFiles: List<LargeFile> = fromJson(entity.largeFilesJson)
                val downloadJunks: List<DownloadJunk> = fromJson(entity.downloadJunksJson)
                val duplicateDocs: List<DuplicateDocumentGroup> = fromJson(entity.duplicateDocsJson)

                val updatedDuplicateDocs = duplicateDocs
                    .map { group ->
                        val updatedFiles = group.files.filterNot { it.path in deletedPaths }
                        group.copy(files = updatedFiles, totalSize = updatedFiles.sumOf { it.size })
                    }
                    .filter { it.files.size >= 2 }

                scanResultDao.insertScanResults(
                    entity.copy(
                        largeFilesJson = gson.toJson(largeFiles.filterNot { it.path in deletedPaths }),
                        downloadJunksJson = gson.toJson(downloadJunks.filterNot { it.path in deletedPaths }),
                        duplicateDocsJson = gson.toJson(updatedDuplicateDocs)
                    )
                )
            }

            val storageInfoEntity = scanResultDao.getStorageInfo() ?: return@withContext
            var photosDiff = 0L
            var videosDiff = 0L
            var audioDiff = 0L
            var docsDiff = 0L
            var otherDiff = 0L
            var totalDiff = 0L

            for ((path, size) in deletedSizes) {
                val mimeType = deletedMimeTypes[path]?.lowercase(java.util.Locale.ROOT)
                val extension = File(path).extension.lowercase(java.util.Locale.ROOT)
                when {
                    mimeType?.startsWith("image/") == true -> photosDiff += size
                    mimeType?.startsWith("video/") == true -> videosDiff += size
                    mimeType?.startsWith("audio/") == true -> audioDiff += size
                    mimeType?.contains("pdf") == true ||
                            mimeType?.contains("document") == true ||
                            mimeType?.contains("text") == true ||
                            extension in DOCUMENT_EXTENSIONS -> docsDiff += size
                    else -> otherDiff += size
                }
                totalDiff += size
            }

            scanResultDao.insertStorageInfo(
                storageInfoEntity.copy(
                    freeBytes = storageInfoEntity.freeBytes + totalDiff,
                    photosBytes = (storageInfoEntity.photosBytes - photosDiff).coerceAtLeast(0L),
                    videosBytes = (storageInfoEntity.videosBytes - videosDiff).coerceAtLeast(0L),
                    audioBytes = (storageInfoEntity.audioBytes - audioDiff).coerceAtLeast(0L),
                    docsBytes = (storageInfoEntity.docsBytes - docsDiff).coerceAtLeast(0L),
                    otherBytes = (storageInfoEntity.otherBytes - otherDiff).coerceAtLeast(0L)
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Silme sonrası önbellek güncellenemedi", e)
        }
    }

    // ── Yardımcılar ─────────────────────────────────────────────────────────

    private inline fun <reified T> fromJson(json: String): List<T> = try {
        val type = com.google.gson.reflect.TypeToken.getParameterized(
            List::class.java, T::class.java
        ).type
        gson.fromJson<List<T>>(json, type) ?: emptyList()
    } catch (e: Exception) {
        Log.w(TAG, "JSON çözümlenemedi", e)
        emptyList()
    }

    private fun totalCleanableSize(
        junkPhotos: List<JunkPhoto>,
        unusedApps: List<UnusedApp>,
        cacheInfos: List<CacheInfo>,
        largeFiles: List<LargeFile>,
        downloadJunks: List<DownloadJunk>,
        systemJunks: List<SystemJunk>
    ): Long = junkPhotos.sumOf { it.size } +
            unusedApps.sumOf { it.appSize + it.cacheSize } +
            cacheInfos.sumOf { it.cacheSize } +
            largeFiles.sumOf { it.size } +
            downloadJunks.sumOf { it.size } +
            systemJunks.sumOf { it.size }

    private fun CachedStorageInfo.toStorageInfo() = StorageInfo(
        totalSpace = totalBytes,
        usedSpace = totalBytes - freeBytes,
        freeSpace = freeBytes,
        photosSize = photosBytes,
        videosSize = videosBytes,
        appsSize = appsBytes,
        cacheSize = cacheBytes,
        audioSize = audioBytes,
        documentsSize = docsBytes,
        otherSize = otherBytes
    )

    private fun JunkPhoto.toEntity() = CachedJunkPhoto(
        photoId = id,
        uri = uri,
        path = path,
        name = name,
        size = size,
        dateModified = dateModified,
        width = width,
        height = height,
        junkType = junkType.name,
        score = score,
        groupId = groupId
    )

    private fun CachedJunkPhoto.toDomain(): JunkPhoto? {
        val type = JunkType.entries.firstOrNull { it.name == junkType } ?: return null
        return JunkPhoto(
            id = photoId,
            uri = uri,
            path = path,
            name = name,
            size = size,
            dateModified = dateModified,
            width = width,
            height = height,
            junkType = type,
            score = score,
            groupId = groupId
        )
    }
}

private val DOCUMENT_EXTENSIONS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt")
