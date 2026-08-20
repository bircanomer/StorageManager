package com.storagemanager.scanner

import com.storagemanager.domain.model.*

/**
 * Cihaz depolama alanı analiz arayüzü.
 */
interface StorageAnalyzer {
    /**
     * @param forceRefresh true ise kısa ömürlü önbellek atlanır (tarama sonrası kullanılır)
     */
    suspend fun analyze(forceRefresh: Boolean = false): StorageInfo
}

/**
 * Fotoğraf analiz arayüzü — AI destekli bulanık, duplike, ekran görüntüsü ve eski fotoğraf tespiti.
 *
 * Dört tür de tek geçişte tespit edilir; ayrı ayrı tarama metotları kaldırıldı
 * çünkü her biri MediaStore'u baştan okuyup aynı thumbnail'leri yeniden üretiyordu.
 */
interface PhotoAnalyzer {
    suspend fun scanAllPhotos(onProgress: ((Float, String) -> Unit)? = null): List<JunkPhoto>

    /**
     * Verilen eşiğin galeride ne işaretleyeceğini küçük bir rastgele örneklem üzerinde
     * gösterir — eşiği ayarlarken tüm taramayı beklemeden sonucu görebilmek için.
     *
     * Üretimdeki iki aşamalı yolun aynısını çalıştırır; aksi halde önizleme gerçek
     * davranışı temsil etmezdi. ML koruması bilinçli olarak devre dışıdır: burada
     * ölçülmek istenen şey netlik eşiği, korumanın etkisi değil.
     *
     * @param sampleSize Galeriden rastgele seçilecek fotoğraf sayısı
     * @param threshold Denenecek netlik eşiği
     */
    suspend fun previewBlurry(sampleSize: Int, threshold: Double): List<JunkPhoto>
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
    suspend fun findLargeFiles(minSizeBytes: Long = 10L * 1024 * 1024): List<LargeFile>
}

/**
 * Belge ve İndirilenler analiz arayüzü.
 */
interface DocumentAnalyzer {
    suspend fun findDuplicateDocuments(): List<DuplicateDocumentGroup>
    suspend fun findDownloadJunk(): List<DownloadJunk>
}

/**
 * Sistem çöpü (boş klasörler ve log/temp dosyaları) analiz arayüzü.
 */
interface SystemJunkAnalyzer {
    suspend fun findSystemJunk(): List<SystemJunk>
}

/**
 * Depolama haritası (Treemap) analiz arayüzü.
 */
interface TreemapAnalyzer {
    suspend fun generateTreemap(maxDepth: Int = 3): StorageTreeNode
}

