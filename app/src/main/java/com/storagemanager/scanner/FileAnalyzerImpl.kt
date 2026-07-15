package com.storagemanager.scanner

import android.app.Application
import android.os.Environment
import android.webkit.MimeTypeMap
import com.storagemanager.domain.model.LargeFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dosya analiz implementasyonu.
 *
 * Yaygın dizinleri özyinelemeli olarak tarayarak büyük dosyaları tespit eder.
 * MIME türü tespiti, boyut filtreleme ve sonuç sınırlama özelliklerine sahiptir.
 */
@Singleton
class FileAnalyzerImpl @Inject constructor(
    private val application: Application
) : FileAnalyzer {

    companion object {
        /** Sonuç listesindeki maksimum dosya sayısı */
        private const val MAX_RESULTS = 100
    }

    /**
     * Belirtilen boyuttan büyük dosyaları arar.
     *
     * Downloads, Documents, DCIM, Music, Movies ve diğer yaygın dizinleri
     * özyinelemeli olarak tarar.
     *
     * @param minSizeBytes Minimum dosya boyutu (byte). Varsayılan: 50 MB
     * @return Büyük dosyaların listesi (boyuta göre azalan sırada, en fazla 100 adet)
     */
    override suspend fun findLargeFiles(minSizeBytes: Long): List<LargeFile> = withContext(Dispatchers.IO) {
        try {
            val largeFiles = mutableListOf<LargeFile>()

            // Taranacak dizinler
            val directories = listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_AUDIOBOOKS)
            )

            for (dir in directories) {
                if (!dir.exists() || !dir.isDirectory) continue
                scanDirectory(dir, minSizeBytes, largeFiles)
            }

            // Boyuta göre azalan sırada sırala ve ilk 100 sonucu döndür
            largeFiles
                .sortedByDescending { it.size }
                .take(MAX_RESULTS)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Belirtilen dizini özyinelemeli olarak tarar ve büyük dosyaları listeye ekler.
     *
     * @param directory Taranacak dizin
     * @param minSizeBytes Minimum dosya boyutu eşiği
     * @param results Sonuçların ekleneceği liste
     */
    private fun scanDirectory(
        directory: File,
        minSizeBytes: Long,
        results: MutableList<LargeFile>
    ) {
        try {
            directory.walkTopDown()
                .onFail { _, _ -> /* Erişilemeyen dizinleri atla */ }
                .filter { it.isFile && it.length() >= minSizeBytes }
                .forEach { file ->
                    try {
                        val mimeType = detectMimeType(file)
                        results.add(
                            LargeFile(
                                path = file.absolutePath,
                                name = file.name,
                                size = file.length(),
                                lastModified = file.lastModified(),
                                mimeType = mimeType
                            )
                        )
                    } catch (_: Exception) {
                        // Tek bir dosya işlenemezse atla
                    }
                }
        } catch (_: Exception) {
            // Dizin erişim hatalarını yoksay
        }
    }

    /**
     * Dosyanın MIME türünü uzantısından tespit eder.
     *
     * @param file MIME türü tespit edilecek dosya
     * @return MIME türü string'i veya null
     */
    private fun detectMimeType(file: File): String? {
        return try {
            val extension = MimeTypeMap.getFileExtensionFromUrl(
                file.absolutePath.replace(" ", "%20")
            )
            if (extension != null) {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
