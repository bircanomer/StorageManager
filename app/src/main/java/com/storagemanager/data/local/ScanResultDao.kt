package com.storagemanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ScanResultDao {

    @Query("SELECT * FROM cached_storage_info WHERE id = :id LIMIT 1")
    suspend fun getStorageInfo(id: Int = 1): CachedStorageInfo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStorageInfo(storageInfo: CachedStorageInfo)

    @Query("SELECT * FROM cached_scan_results WHERE id = :id LIMIT 1")
    suspend fun getScanResults(id: Int = 1): CachedScanResults?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanResults(scanResults: CachedScanResults)

    @Query("DELETE FROM cached_storage_info")
    suspend fun clearStorageInfo()

    @Query("DELETE FROM cached_scan_results")
    suspend fun clearScanResults()

    // ── Gereksiz fotoğraflar ────────────────────────────────────────────────

    @Query("SELECT * FROM cached_junk_photos")
    suspend fun getJunkPhotos(): List<CachedJunkPhoto>

    @Query("SELECT COALESCE(SUM(size), 0) FROM cached_junk_photos WHERE photoId IN (:photoIds)")
    suspend fun sumSizeOf(photoIds: List<Long>): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJunkPhotos(photos: List<CachedJunkPhoto>)

    @Query("DELETE FROM cached_junk_photos")
    suspend fun clearJunkPhotos()

    @Query("DELETE FROM cached_junk_photos WHERE photoId IN (:photoIds)")
    suspend fun deleteJunkPhotos(photoIds: List<Long>)

    /** Fotoğraf önbelleğini tek işlemde değiştirir. */
    @Transaction
    suspend fun replaceJunkPhotos(photos: List<CachedJunkPhoto>) {
        clearJunkPhotos()
        // SQLite değişken sınırına takılmamak için parça parça yaz
        photos.chunked(500).forEach { insertJunkPhotos(it) }
    }
}
