package com.storagemanager.scanner

import android.app.Application
import android.os.Environment
import android.util.Log
import com.storagemanager.domain.model.DownloadJunk
import com.storagemanager.domain.model.DuplicateDocumentGroup
import com.storagemanager.domain.model.LargeFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class DocumentAnalyzerImpl @Inject constructor(
    private val application: Application
) : DocumentAnalyzer {

    private companion object {
        const val TAG = "DocumentAnalyzer"
        const val MAX_DEPTH = 4
        const val MAX_FILES = 20_000
        const val MIN_FILE_SIZE = 1024L

        /** Hızlı karşılaştırma için okunacak en fazla bayt. */
        const val HASH_SAMPLE_BYTES = 1024 * 1024
        const val BUFFER_SIZE = 8192
    }

    override suspend fun findDuplicateDocuments(): List<DuplicateDocumentGroup> = withContext(Dispatchers.IO) {
        try {
            val externalStorage = Environment.getExternalStorageDirectory()
                ?: return@withContext emptyList()

            val targetDirs = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                File(externalStorage, "Documents"),
                File(externalStorage, "Download")
            )
                .filterNotNull()
                .filter { it.isDirectory }
                .distinctBy { it.canonicalPath }

            val fileList = mutableListOf<File>()
            for (dir in targetDirs) {
                collectFiles(dir, fileList)
            }

            // Önce boyuta göre grupla — MD5 hesabı yalnızca aynı boyuttaki dosyalar için gerekir
            val sizeGroups = fileList
                .distinctBy { it.canonicalPath }
                .groupBy { it.length() }

            val duplicateGroups = mutableListOf<DuplicateDocumentGroup>()
            var groupCounter = 1

            for ((size, filesWithSize) in sizeGroups) {
                coroutineContext.ensureActive()
                if (filesWithSize.size < 2) continue

                for ((hash, filesWithHash) in filesWithSize.groupBy { computeHash(it) }) {
                    if (hash.isEmpty() || filesWithHash.size < 2) continue

                    val largeFiles = filesWithHash.map { file ->
                        LargeFile(
                            path = file.absolutePath,
                            name = file.name,
                            size = file.length(),
                            lastModified = file.lastModified() / 1000
                        )
                    }

                    duplicateGroups += DuplicateDocumentGroup(
                        groupId = "doc_dup_$groupCounter",
                        hash = hash,
                        totalSize = size * largeFiles.size,
                        files = largeFiles
                    )
                    groupCounter++
                }
            }
            duplicateGroups
        } catch (e: Exception) {
            Log.e(TAG, "Duplike belge taraması başarısız", e)
            emptyList()
        }
    }

    override suspend fun findDownloadJunk(): List<DownloadJunk> = withContext(Dispatchers.IO) {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir == null || !downloadsDir.isDirectory) return@withContext emptyList()

            val files = downloadsDir.listFiles() ?: return@withContext emptyList()
            val junkList = mutableListOf<DownloadJunk>()

            for (file in files) {
                coroutineContext.ensureActive()
                if (!file.isFile) continue

                val ext = file.extension.lowercase()
                val category = when (ext) {
                    "apk" -> "APK"
                    "zip", "rar", "7z", "tar", "gz" -> "ARCHIVE"
                    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt" -> "DOCUMENT"
                    else -> "OTHER"
                }

                val length = file.length()
                if (category == "APK" || category == "ARCHIVE" || length > 10 * 1024 * 1024) {
                    junkList += DownloadJunk(
                        path = file.absolutePath,
                        name = file.name,
                        size = length,
                        lastModified = file.lastModified() / 1000,
                        category = category
                    )
                }
            }

            junkList.sortByDescending { it.size }
            junkList
        } catch (e: Exception) {
            Log.e(TAG, "İndirilenler taraması başarısız", e)
            emptyList()
        }
    }

    /**
     * Dizini sınırlı derinlikte gezerek dosyaları toplar.
     */
    private suspend fun collectFiles(
        dir: File,
        list: MutableList<File>,
        currentDepth: Int = 0
    ) {
        if (currentDepth > MAX_DEPTH || list.size >= MAX_FILES || !dir.isDirectory) return
        coroutineContext.ensureActive()

        val files = dir.listFiles() ?: return
        for (f in files) {
            if (list.size >= MAX_FILES) return
            when {
                f.isFile && f.length() > MIN_FILE_SIZE -> list += f
                f.isDirectory -> collectFiles(f, list, currentDepth + 1)
            }
        }
    }

    /**
     * Dosyanın ilk [HASH_SAMPLE_BYTES] baytının MD5'ini hesaplar.
     *
     * Yalnızca aynı boyuttaki dosyalar karşılaştırıldığı için kısmi hash yeterlidir.
     * (MD5 burada kriptografik amaçla değil, hızlı içerik parmak izi olarak kullanılır.)
     */
    private fun computeHash(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(BUFFER_SIZE)
            var readTotal = 0

            // use{} ile akış istisna durumunda da kapatılır — önceki sürümde sızıntı vardı
            FileInputStream(file).use { input ->
                while (readTotal < HASH_SAMPLE_BYTES) {
                    val toRead = minOf(BUFFER_SIZE, HASH_SAMPLE_BYTES - readTotal)
                    val bytesRead = input.read(buffer, 0, toRead)
                    if (bytesRead == -1) break
                    digest.update(buffer, 0, bytesRead)
                    readTotal += bytesRead
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Hash hesaplanamadı: ${file.path}", e)
            ""
        }
    }
}
