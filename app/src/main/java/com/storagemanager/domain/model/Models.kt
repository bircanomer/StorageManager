package com.storagemanager.domain.model

import androidx.annotation.StringRes
import com.storagemanager.R

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
 *
 * Sekme etiketi ve açıklaması da burada tutulur; böylece yeni bir tür eklendiğinde
 * UI tarafında dağınık `when` bloklarını tek tek güncellemek gerekmez.
 */
enum class JunkType(
    @StringRes val displayNameRes: Int,
    val emoji: String,
    @StringRes val tabLabelRes: Int,
    @StringRes val descriptionRes: Int
) {
    BLURRY(R.string.junk_type_blurry, "📸", R.string.tab_blurry, R.string.photos_blurry_desc),
    DUPLICATE(R.string.junk_type_duplicate, "🔄", R.string.tab_duplicate, R.string.photos_duplicate_desc),
    SCREENSHOT(R.string.junk_type_screenshot, "📱", R.string.tab_screenshot, R.string.photos_screenshot_desc),
    OLD(R.string.junk_type_old, "🕰️", R.string.tab_old, R.string.photos_old_desc)
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
    val duplicateDocuments: List<DuplicateDocumentGroup> = emptyList(),
    val downloadJunks: List<DownloadJunk> = emptyList(),
    val systemJunks: List<SystemJunk> = emptyList(),
    val totalCleanableSize: Long = 0L,
    val scanDurationMs: Long = 0L
)

/**
 * Duplike belge grubu.
 */
data class DuplicateDocumentGroup(
    val groupId: String,
    val hash: String,
    val totalSize: Long,
    val files: List<LargeFile>
)

/**
 * İndirilenler klasöründeki gereksiz dosya (APK, eski arşiv vb.)
 */
data class DownloadJunk(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val category: String // "APK", "ARCHIVE", "DOCUMENT", "OTHER"
)

/**
 * Sistem çöp türü.
 */
enum class SystemJunkType(@StringRes val displayNameRes: Int, val emoji: String) {
    EMPTY_FOLDER(R.string.junk_empty_folder, "📁"),
    LOG_FILE(R.string.junk_log_file, "📝"),
    TEMP_FILE(R.string.junk_temp_file, "⏳"),
    CACHE_FILE(R.string.junk_system_cache, "🧹")
}

/**
 * Sistem çöpü nesnesi (boş klasör veya log/temp dosyası)
 */
data class SystemJunk(
    val path: String,
    val name: String,
    val size: Long,
    val type: SystemJunkType
)

/**
 * Çöp kutusundaki öge nesnesi.
 */
data class TrashItem(
    val id: Long = 0,
    val originalPath: String,
    val trashPath: String,
    val fileName: String,
    val fileSize: Long,
    val deletedTimestamp: Long,
    val mimeType: String? = null,
    val itemType: String // "PHOTO", "FILE", "DOCUMENT", "MESSENGER"
)

/**
 * Depolama haritası düğümü (Treemap node).
 */
data class StorageTreeNode(
    val path: String,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val children: List<StorageTreeNode> = emptyList(),
    val percentageOfParent: Float = 0f
)

/**
 * Tarama durumu — UI state yönetimi.
 */
sealed class ScanState {
    data object Idle : ScanState()
    data class Scanning(
        val progress: Float = 0f,
        val currentTask: String = "",
        /** Biten kategorilerin o ana kadarki sonucu — tarama sürerken de gösterilir. */
        val partial: ScanResult? = null
    ) : ScanState()
    data class Completed(val result: ScanResult) : ScanState()
    data class Error(val message: String) : ScanState()
}

/**
 * Seçmeli tarama kategorileri.
 */
enum class ScanCategory(
    @StringRes val labelRes: Int,
    val emoji: String,
    @StringRes val descriptionRes: Int
) {
    PHOTOS(R.string.category_photos, "🖼️", R.string.category_photos_desc),
    UNUSED_APPS(R.string.category_unused_apps, "📦", R.string.category_unused_apps_desc),
    CACHE(R.string.category_cache, "🗑️", R.string.category_cache_desc),
    LARGE_FILES(R.string.category_large_files, "📁", R.string.category_large_files_desc),
    DOWNLOADS(R.string.category_downloads, "📄", R.string.category_downloads_desc),
    SYSTEM_JUNK(R.string.category_system_junk, "🧹", R.string.category_system_junk_desc)
}

