package com.storagemanager.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "cached_storage_info")
data class CachedStorageInfo(
    @PrimaryKey val id: Int = 1,
    val totalBytes: Long,
    val freeBytes: Long,
    val photosBytes: Long,
    val videosBytes: Long,
    val appsBytes: Long,
    val cacheBytes: Long,
    val audioBytes: Long,
    val docsBytes: Long,
    val otherBytes: Long,
    val lastScanTime: Long
)

@Entity(tableName = "cached_scan_results")
data class CachedScanResults(
    @PrimaryKey val id: Int = 1,
    val largeFilesJson: String,  // Serialized List<LargeFile>
    val unusedAppsJson: String,  // Serialized List<UnusedApp>
    val cacheInfosJson: String,  // Serialized List<CacheInfo>
    val duplicateDocsJson: String = "[]",
    val downloadJunksJson: String = "[]",
    val systemJunksJson: String = "[]"
)

/**
 * Önbelleğe alınan gereksiz fotoğraflar.
 *
 * Fotoğraflar eskiden diğer sonuçlarla birlikte tek bir JSON metninde tutuluyordu;
 * tek bir fotoğrafı silmek bile megabaytlarca JSON'un çözülüp yeniden yazılması
 * demekti. Ayrı tablo sayesinde silme işlemi tek bir DELETE sorgusuna indi.
 */
@Entity(
    tableName = "cached_junk_photos",
    indices = [Index("photoId"), Index("junkType")]
)
data class CachedJunkPhoto(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val photoId: Long,
    val uri: String,
    val path: String,
    val name: String,
    val size: Long,
    val dateModified: Long,
    val width: Int,
    val height: Int,
    /** [com.storagemanager.domain.model.JunkType] adı */
    val junkType: String,
    val score: Float,
    val groupId: String?
)
