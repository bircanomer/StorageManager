package com.storagemanager.scanner

import com.storagemanager.domain.model.*

/**
 * Cihaz depolama alanı analiz arayüzü.
 */
interface StorageAnalyzer {
    suspend fun analyze(): StorageInfo
}

/**
 * Fotoğraf analiz arayüzü — AI destekli bulanık, duplike, ekran görüntüsü ve eski fotoğraf tespiti.
 */
interface PhotoAnalyzer {
    suspend fun findBlurryPhotos(): List<JunkPhoto>
    suspend fun findDuplicatePhotos(): List<JunkPhoto>
    suspend fun findScreenshots(): List<JunkPhoto>
    suspend fun findOldPhotos(olderThanDays: Int = 180): List<JunkPhoto>
}

/**
 * Uygulama analiz arayüzü — kullanım istatistikleri ve önbellek bilgileri.
 */
interface AppAnalyzer {
    suspend fun findUnusedApps(daysSinceLastUse: Int = 30): List<UnusedApp>
    suspend fun getCacheInfo(): List<CacheInfo>
}

/**
 * Dosya analiz arayüzü — büyük ve eski dosya tespiti.
 */
interface FileAnalyzer {
    suspend fun findLargeFiles(minSizeBytes: Long = 50L * 1024 * 1024): List<LargeFile>
}
