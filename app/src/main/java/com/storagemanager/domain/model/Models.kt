package com.storagemanager.domain.model

/**
 * Genel depolama bilgisi — cihazın disk kullanım dağılımı.
 */
data class StorageInfo(
    val totalSpace: Long = 0L,
    val usedSpace: Long = 0L,
    val freeSpace: Long = 0L,
    val photosSize: Long = 0L,
    val videosSize: Long = 0L,
    val appsSize: Long = 0L,
    val cacheSize: Long = 0L,
    val audioSize: Long = 0L,
    val documentsSize: Long = 0L,
    val otherSize: Long = 0L
)

/**
 * Gereksiz fotoğraf türleri.
 */
enum class JunkType(val displayName: String, val emoji: String) {
    BLURRY("Bulanık Fotoğraflar", "📸"),
    DUPLICATE("Duplike Fotoğraflar", "🔄"),
    SCREENSHOT("Ekran Görüntüleri", "📱"),
    OLD("Eski Fotoğraflar", "🕰️")
}

/**
 * AI tarafından gereksiz olarak tespit edilen bir fotoğraf.
 */
data class JunkPhoto(
    val id: Long,
    val uri: String,
    val path: String,
    val name: String,
    val size: Long,
    val dateModified: Long,
    val width: Int = 0,
    val height: Int = 0,
    val junkType: JunkType,
    val score: Float = 0f,
    val groupId: String? = null
)

/**
 * Uzun süredir kullanılmayan bir uygulama.
 */
data class UnusedApp(
    val packageName: String,
    val appName: String,
    val appSize: Long,
    val cacheSize: Long,
    val lastUsed: Long,
    val installDate: Long
)

/**
 * Bir uygulamanın önbellek bilgisi.
 */
data class CacheInfo(
    val packageName: String,
    val appName: String,
    val cacheSize: Long
)

/**
 * Büyük boyutlu dosya bilgisi.
 */
data class LargeFile(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String? = null
)

/**
 * Tam tarama sonucu — tüm kategorilerdeki bulguları içerir.
 */
data class ScanResult(
    val storageInfo: StorageInfo = StorageInfo(),
    val junkPhotos: List<JunkPhoto> = emptyList(),
    val unusedApps: List<UnusedApp> = emptyList(),
    val cacheInfos: List<CacheInfo> = emptyList(),
    val largeFiles: List<LargeFile> = emptyList(),
    val totalCleanableSize: Long = 0L,
    val scanDurationMs: Long = 0L
)

/**
 * Tarama durumu — UI state yönetimi.
 */
sealed class ScanState {
    data object Idle : ScanState()
    data class Scanning(
        val progress: Float = 0f,
        val currentTask: String = ""
    ) : ScanState()
    data class Completed(val result: ScanResult) : ScanState()
    data class Error(val message: String) : ScanState()
}
