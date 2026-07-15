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

    /** Belirtilen süre içinde kullanılmamış uygulamaları listeler. */
    suspend fun scanUnusedApps(daysSinceLastUse: Int = 30): List<UnusedApp>

    /** Tüm uygulamaların önbellek bilgilerini döndürür. */
    suspend fun getCacheInfo(): List<CacheInfo>

    /** Belirtilen boyuttan büyük dosyaları listeler. */
    suspend fun scanLargeFiles(minSizeBytes: Long = 50L * 1024 * 1024): List<LargeFile>

    /** Seçili fotoğrafları siler ve silinen sayısını döndürür. */
    suspend fun deletePhotos(photos: List<JunkPhoto>): Result<Int>

    /** Seçili dosyaları siler ve silinen sayısını döndürür. */
    suspend fun deleteFiles(files: List<LargeFile>): Result<Int>

    /** Tam tarama yapar — ilerleme callback'i ile. */
    suspend fun fullScan(onProgress: (Float, String) -> Unit = { _, _ -> }): ScanResult
}
