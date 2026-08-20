package com.storagemanager.scanner

import android.app.Application
import android.content.ContentResolver
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import com.storagemanager.domain.model.LargeFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Dosya analiz implementasyonu.
 *
 * Önce MediaStore'u boyuta göre sıralı ve sınırlı sorgular; MediaStore boş dönerse
 * (izin yoksa veya indekslenmemişse) dizin taramasına düşer.
 */
@Singleton
class FileAnalyzerImpl @Inject constructor(
    private val application: Application
) : FileAnalyzer {

    private companion object {
        const val TAG = "FileAnalyzer"

        /** Sonuç listesindeki maksimum dosya sayısı. */
        const val MAX_RESULTS = 500

        /** Dizin taramasında gezilecek en fazla dosya — patholojik durumlarda tarama kilitlenmesin. */
        const val MAX_WALKED_FILES = 200_000
    }

    /**
     * Belirtilen boyuttan büyük dosyaları arar.
     *
     * @param minSizeBytes Minimum dosya boyutu (byte)
     * @return Büyük dosyaların listesi (boyuta göre azalan, en fazla [MAX_RESULTS] adet)
     */
    override suspend fun findLargeFiles(minSizeBytes: Long): List<LargeFile> = withContext(Dispatchers.IO) {
        try {
            val fromMediaStore = queryMediaStore(minSizeBytes)
            if (fromMediaStore.isNotEmpty()) return@withContext fromMediaStore

            // Fallback: dizin taraması
            val root = Environment.getExternalStorageDirectory()
            if (root == null || !root.isDirectory) return@withContext emptyList()
            scanDirectory(root, minSizeBytes)
        } catch (e: Exception) {
            Log.e(TAG, "findLargeFiles başarısız", e)
            emptyList()
        }
    }

    /**
     * MediaStore'dan boyuta göre azalan sırada **sınırlı** sayıda satır okur.
     *
     * Önceki sürüm tüm satırları belleğe alıp sonra kırpıyordu; on binlerce dosyası olan
     * cihazlarda hem gereksiz bellek hem de her satır için `File.exists()` syscall'ı demekti.
     */
    private suspend fun queryMediaStore(minSizeBytes: Long): List<LargeFile> {
        val results = mutableListOf<LargeFile>()
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        val selection = "${MediaStore.Files.FileColumns.SIZE} >= ?"
        val selectionArgs = arrayOf(minSizeBytes.toString())

        queryLimited(uri, projection, selection, selectionArgs, MAX_RESULTS)?.use { cursor ->
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

            while (cursor.moveToNext() && results.size < MAX_RESULTS) {
                coroutineContext.ensureActive()
                val path = cursor.getString(dataColumn) ?: continue
                val file = File(path)
                // Sadece gösterilecek olan sınırlı sayıda satır için dosya sistemine gidiyoruz
                if (!file.isFile) continue

                val dateModified = cursor.getLong(dateColumn) * 1000L
                results += LargeFile(
                    path = path,
                    name = cursor.getString(nameColumn) ?: file.name,
                    size = cursor.getLong(sizeColumn),
                    lastModified = if (dateModified > 0) dateModified else file.lastModified(),
                    mimeType = cursor.getString(mimeColumn) ?: detectMimeType(file)
                )
            }
        }
        return results.sortedByDescending { it.size }
    }

    /**
     * Dizini gezerek büyük dosyaları toplar. Yalnızca MediaStore boş dönerse kullanılır.
     */
    private suspend fun scanDirectory(directory: File, minSizeBytes: Long): List<LargeFile> {
        val results = mutableListOf<LargeFile>()
        var walked = 0
        try {
            val walker = directory.walkTopDown()
                .onEnter { dir ->
                    val path = dir.absolutePath
                    !path.contains("/Android/data", ignoreCase = true) &&
                            !path.contains("/Android/obb", ignoreCase = true)
                }
                .onFail { file, e -> Log.d(TAG, "Erişilemeyen dizin atlandı: ${file.path}", e) }

            for (file in walker) {
                if (++walked % 512 == 0) coroutineContext.ensureActive()
                if (walked > MAX_WALKED_FILES) {
                    Log.w(TAG, "Dizin taraması $MAX_WALKED_FILES dosyada durduruldu")
                    break
                }
                if (!file.isFile) continue

                val length = file.length()
                if (length < minSizeBytes) continue

                results += LargeFile(
                    path = file.absolutePath,
                    name = file.name,
                    size = length,
                    lastModified = file.lastModified(),
                    mimeType = detectMimeType(file)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "scanDirectory başarısız", e)
        }
        return results.sortedByDescending { it.size }.take(MAX_RESULTS)
    }

    /**
     * API 30+ üzerinde sorgu sınırını ContentResolver argümanlarıyla, altında
     * `LIMIT` içeren sortOrder ile uygular.
     */
    private fun queryLimited(
        uri: android.net.Uri,
        projection: Array<String>,
        selection: String,
        selectionArgs: Array<String>,
        limit: Int
    ): Cursor? {
        val sizeColumn = MediaStore.Files.FileColumns.SIZE
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val args = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                    putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(sizeColumn))
                    putInt(
                        ContentResolver.QUERY_ARG_SORT_DIRECTION,
                        ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                    )
                    putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                }
                application.contentResolver.query(uri, projection, args, null)
            } else {
                application.contentResolver.query(
                    uri,
                    projection,
                    selection,
                    selectionArgs,
                    "$sizeColumn DESC LIMIT $limit"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore sorgusu başarısız", e)
            null
        }
    }

    /**
     * Dosyanın MIME türünü uzantısından tespit eder.
     */
    private fun detectMimeType(file: File): String? {
        return try {
            val extension = MimeTypeMap.getFileExtensionFromUrl(
                file.absolutePath.replace(" ", "%20")
            )
            if (extension.isNullOrEmpty()) {
                null
            } else {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            }
        } catch (e: Exception) {
            null
        }
    }
}
