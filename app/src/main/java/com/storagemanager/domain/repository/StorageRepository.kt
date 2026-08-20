package com.storagemanager.domain.repository

import com.storagemanager.domain.model.*

/**
 * Ana repository arayüzü — tüm depolama işlemleri bu interface üzerinden yapılır.
 */
interface StorageRepository {

    /** Cihaz depolama bilgisini getirir. */
    suspend fun getStorageInfo(): StorageInfo

    /** Tüm fotoğrafları tarar ve gereksiz olanları döndürür (bulanık, duplike, ss, eski). */
    suspend fun scanJunkPhotos(): List<JunkPhoto>

    /**
     * Netlik eşiğinin ne işaretleyeceğini küçük bir rastgele örneklemde gösterir.
     * Önbelleğe hiçbir şey yazmaz — yalnızca eşiği ayarlarken önizleme içindir.
     */
    suspend fun previewBlurryPhotos(sampleSize: Int, threshold: Float): List<JunkPhoto>

    /** Belirtilen süre içinde kullanılmamış uygulamaları listeler. */
    suspend fun scanUnusedApps(daysSinceLastUse: Int = 30): List<UnusedApp>

    /** Tüm uygulamaların önbellek bilgilerini döndürür. */
    suspend fun getCacheInfo(): List<CacheInfo>

    /** Belirtilen boyuttan büyük dosyaları listeler. */
    suspend fun scanLargeFiles(minSizeBytes: Long = 50L * 1024 * 1024): List<LargeFile>

    /** Messenger (WhatsApp/Telegram) çöplerini tarar. */
    suspend fun scanMessengerJunk(): List<MessengerJunk>

    /** Duplike belgeleri tarar. */
    suspend fun scanDuplicateDocuments(): List<DuplicateDocumentGroup>

    /** İndirilenler klasöründeki gereksiz dosyaları tarar. */
    suspend fun scanDownloadJunk(): List<DownloadJunk>

    /** Sistem çöplerini (boş klasörler, log/temp) tarar. */
    suspend fun scanSystemJunk(): List<SystemJunk>

    /** Depolama haritasını (Treemap) oluşturur. */
    suspend fun generateTreemap(maxDepth: Int = 3): StorageTreeNode

    /** Seçili fotoğrafları siler ve silinen sayısını döndürür. */
    suspend fun deletePhotos(photos: List<JunkPhoto>): Result<Int>

    /** Seçili dosyaları siler ve silinen sayısını döndürür. */
    suspend fun deleteFiles(files: List<LargeFile>): Result<Int>

    /** Sistem çöplerini (boş klasör veya log) siler. */
    suspend fun deleteSystemJunk(junks: List<SystemJunk>): Result<Int>

    /** Çöp kutusu işlemleri */
    fun getTrashItems(): kotlinx.coroutines.flow.Flow<List<TrashItem>>
    suspend fun moveToTrash(filePath: String, itemType: String): Result<Boolean>
    suspend fun restoreFromTrash(trashId: Long): Result<Boolean>
    suspend fun deletePermanentlyFromTrash(trashId: Long): Result<Boolean>
    suspend fun clearTrash(): Result<Boolean>

    /** Belirtilen günden eski çöp kutusu ögelerini kalıcı siler; silinen sayıyı döndürür. */
    suspend fun cleanupOldTrash(maxAgeDays: Int = 30): Int

    /** Tam tarama yapar — ilerleme callback'i ile. */
    suspend fun fullScan(
        onProgress: (Float, String) -> Unit = { _, _ -> },
        onPartialResult: (ScanResult) -> Unit = {}
    ): ScanResult

    /**
     * Seçilen kategoriler bazında özel tarama yapar.
     *
     * Her kategori bittiğinde o ana kadarki sonuç hem önbelleğe yazılır hem de
     * [onPartialResult] ile bildirilir; böylece tarama yarıda kesilse de biten
     * kategorilerin sonucu korunur.
     */
    suspend fun customScan(
        categories: Set<ScanCategory>,
        onProgress: (Float, String) -> Unit = { _, _ -> },
        onPartialResult: (ScanResult) -> Unit = {}
    ): ScanResult

    /** Caching methods */
    suspend fun getCachedStorageInfo(): StorageInfo?
    suspend fun getCachedScanResults(): ScanResult?
    suspend fun removePhotosFromCache(photoIds: Set<Long>)
}
