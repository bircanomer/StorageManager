package com.storagemanager.scanner

import android.app.Application
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import com.storagemanager.domain.model.JunkPhoto
import com.storagemanager.domain.model.JunkType
import com.storagemanager.ml.BlurDetector
import com.storagemanager.ml.DuplicateDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fotoğraf analiz implementasyonu.
 *
 * AI destekli bulanıklık tespiti, perceptual hash tabanlı duplike tespiti,
 * ekran görüntüsü tespiti ve eski fotoğraf filtrelemesi yapar.
 */
@Singleton
class PhotoAnalyzerImpl @Inject constructor(
    private val application: Application,
    private val blurDetector: BlurDetector,
    private val duplicateDetector: DuplicateDetector
) : PhotoAnalyzer {

    companion object {
        /** Thumbnail yükleme için hedef genişlik */
        private const val THUMBNAIL_WIDTH = 256
        /** Thumbnail yükleme için hedef yükseklik */
        private const val THUMBNAIL_HEIGHT = 256
        /** Duplike tespit eşik değeri */
        private const val DUPLICATE_THRESHOLD = 10
    }

    /**
     * Bulanık fotoğrafları tespit eder.
     *
     * MediaStore'dan tüm görselleri sorgular, thumbnail yükler ve
     * Laplacian varyans ile bulanıklık skoru hesaplar.
     *
     * @return Bulanık olarak tespit edilen fotoğrafların listesi
     */
    override suspend fun findBlurryPhotos(): List<JunkPhoto> = withContext(Dispatchers.IO) {
        try {
            val photos = mutableListOf<JunkPhoto>()
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT
            )

            application.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: continue
                        val path = cursor.getString(dataCol) ?: continue
                        val size = cursor.getLong(sizeCol)
                        val dateModified = cursor.getLong(dateCol)
                        val width = cursor.getInt(widthCol)
                        val height = cursor.getInt(heightCol)

                        // Thumbnail yükle
                        val bitmap = loadThumbnail(path) ?: continue

                        // Bulanıklık tespiti
                        val (isBlurry, score) = blurDetector.isBlurry(bitmap)
                        bitmap.recycle()

                        if (isBlurry) {
                            val uri = android.content.ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                            ).toString()

                            photos.add(
                                JunkPhoto(
                                    id = id,
                                    uri = uri,
                                    path = path,
                                    name = name,
                                    size = size,
                                    dateModified = dateModified,
                                    width = width,
                                    height = height,
                                    junkType = JunkType.BLURRY,
                                    score = score
                                )
                            )
                        }
                    } catch (_: Exception) {
                        // Tek bir fotoğraf işlenemezse atla
                    }
                }
            }
            photos
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Duplike fotoğrafları tespit eder.
     *
     * Tüm görsellerin perceptual hash'ini hesaplar ve Hamming mesafesi
     * eşik değerinin altında olan çiftleri gruplar.
     *
     * @return Duplike olarak tespit edilen fotoğrafların listesi (groupId ile gruplandırılmış)
     */
    override suspend fun findDuplicatePhotos(): List<JunkPhoto> = withContext(Dispatchers.IO) {
        try {
            // Tüm fotoğrafları ve hash'lerini topla
            data class PhotoData(
                val id: Long,
                val name: String,
                val path: String,
                val size: Long,
                val dateModified: Long,
                val width: Int,
                val height: Int,
                val hash: Long
            )

            val allPhotos = mutableListOf<PhotoData>()
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT
            )

            application.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: continue
                        val path = cursor.getString(dataCol) ?: continue
                        val size = cursor.getLong(sizeCol)
                        val dateModified = cursor.getLong(dateCol)
                        val width = cursor.getInt(widthCol)
                        val height = cursor.getInt(heightCol)

                        val bitmap = loadThumbnail(path) ?: continue
                        val hash = duplicateDetector.computeHash(bitmap)
                        bitmap.recycle()

                        allPhotos.add(
                            PhotoData(id, name, path, size, dateModified, width, height, hash)
                        )
                    } catch (_: Exception) {
                        // Tek bir fotoğraf işlenemezse atla
                    }
                }
            }

            // Union-Find ile gruplandır
            val parent = IntArray(allPhotos.size) { it }

            fun find(i: Int): Int {
                var root = i
                while (parent[root] != root) root = parent[root]
                // Path compression
                var current = i
                while (current != root) {
                    val next = parent[current]
                    parent[current] = root
                    current = next
                }
                return root
            }

            fun union(i: Int, j: Int) {
                val rootI = find(i)
                val rootJ = find(j)
                if (rootI != rootJ) parent[rootI] = rootJ
            }

            // Tüm çiftleri karşılaştır
            for (i in allPhotos.indices) {
                for (j in i + 1 until allPhotos.size) {
                    if (duplicateDetector.areDuplicates(
                            allPhotos[i].hash,
                            allPhotos[j].hash,
                            DUPLICATE_THRESHOLD
                        )
                    ) {
                        union(i, j)
                    }
                }
            }

            // Grupları oluştur
            val groups = mutableMapOf<Int, MutableList<Int>>()
            for (i in allPhotos.indices) {
                val root = find(i)
                groups.getOrPut(root) { mutableListOf() }.add(i)
            }

            // Sadece birden fazla eleman içeren grupları al
            val duplicates = mutableListOf<JunkPhoto>()
            for ((_, members) in groups) {
                if (members.size < 2) continue

                val groupId = "dup_${members.first()}"
                for (idx in members) {
                    val photo = allPhotos[idx]
                    val uri = android.content.ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photo.id
                    ).toString()

                    duplicates.add(
                        JunkPhoto(
                            id = photo.id,
                            uri = uri,
                            path = photo.path,
                            name = photo.name,
                            size = photo.size,
                            dateModified = photo.dateModified,
                            width = photo.width,
                            height = photo.height,
                            junkType = JunkType.DUPLICATE,
                            score = 0f,
                            groupId = groupId
                        )
                    )
                }
            }
            duplicates
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Ekran görüntülerini tespit eder.
     *
     * Tipik ekran görüntüsü dizin yollarını kontrol eder ve
     * cihaz ekran boyutuna eşleşen görselleri filtreler.
     *
     * @return Ekran görüntüsü olarak tespit edilen fotoğrafların listesi
     */
    override suspend fun findScreenshots(): List<JunkPhoto> = withContext(Dispatchers.IO) {
        try {
            val photos = mutableListOf<JunkPhoto>()

            // Cihazın ekran boyutunu al
            val (screenWidth, screenHeight) = getScreenDimensions()

            // Ekran görüntüsü dizin yolları
            val screenshotPaths = listOf(
                "/Screenshots/",
                "/DCIM/Screenshots/",
                "/Pictures/Screenshots/",
                "/Screen captures/"
            )

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT
            )

            application.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: continue
                        val path = cursor.getString(dataCol) ?: continue
                        val size = cursor.getLong(sizeCol)
                        val dateModified = cursor.getLong(dateCol)
                        val width = cursor.getInt(widthCol)
                        val height = cursor.getInt(heightCol)

                        // Yol kontrolü veya ekran boyutu eşleşmesi
                        val isInScreenshotDir = screenshotPaths.any { path.contains(it, ignoreCase = true) }
                        val matchesScreenSize = (width == screenWidth && height == screenHeight) ||
                                (width == screenHeight && height == screenWidth)
                        val nameContainsScreenshot = name.contains("screenshot", ignoreCase = true) ||
                                name.contains("ekran görüntüsü", ignoreCase = true)

                        if (isInScreenshotDir || (matchesScreenSize && nameContainsScreenshot)) {
                            val uri = android.content.ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                            ).toString()

                            photos.add(
                                JunkPhoto(
                                    id = id,
                                    uri = uri,
                                    path = path,
                                    name = name,
                                    size = size,
                                    dateModified = dateModified,
                                    width = width,
                                    height = height,
                                    junkType = JunkType.SCREENSHOT,
                                    score = 1f
                                )
                            )
                        }
                    } catch (_: Exception) {
                        // Tek bir fotoğraf işlenemezse atla
                    }
                }
            }
            photos
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Belirtilen günden daha eski fotoğrafları bulur.
     *
     * @param olderThanDays Kaç günden eski fotoğrafların aranacağı
     * @return Eski fotoğrafların listesi
     */
    override suspend fun findOldPhotos(olderThanDays: Int): List<JunkPhoto> = withContext(Dispatchers.IO) {
        try {
            val photos = mutableListOf<JunkPhoto>()
            val cutoffTime = (System.currentTimeMillis() / 1000) -
                    TimeUnit.DAYS.toSeconds(olderThanDays.toLong())

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT
            )

            val selection = "${MediaStore.Images.Media.DATE_MODIFIED} < ?"
            val selectionArgs = arrayOf(cutoffTime.toString())

            application.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_MODIFIED} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: continue
                        val path = cursor.getString(dataCol) ?: continue
                        val size = cursor.getLong(sizeCol)
                        val dateModified = cursor.getLong(dateCol)
                        val width = cursor.getInt(widthCol)
                        val height = cursor.getInt(heightCol)

                        val uri = android.content.ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                        ).toString()

                        photos.add(
                            JunkPhoto(
                                id = id,
                                uri = uri,
                                path = path,
                                name = name,
                                size = size,
                                dateModified = dateModified,
                                width = width,
                                height = height,
                                junkType = JunkType.OLD,
                                score = 1f
                            )
                        )
                    } catch (_: Exception) {
                        // Tek bir fotoğraf işlenemezse atla
                    }
                }
            }
            photos
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Dosya yolundan küçültülmüş bitmap yükler (bellek optimizasyonu).
     *
     * @param path Dosya yolu
     * @return Küçültülmüş bitmap veya null
     */
    private fun loadThumbnail(path: String): android.graphics.Bitmap? {
        return try {
            // Önce boyutları oku
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)

            // Örnekleme oranını hesapla
            options.inSampleSize = calculateInSampleSize(options, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
            options.inJustDecodeBounds = false

            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Bellek tasarrufu için örnekleme oranını hesaplar.
     *
     * @param options BitmapFactory seçenekleri (boyut bilgileri yüklenmiş)
     * @param reqWidth Hedef genişlik
     * @param reqHeight Hedef yükseklik
     * @return Örnekleme oranı (2'nin katları)
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Cihaz ekran boyutlarını döndürür.
     *
     * @return Pair<Int, Int> — (genişlik, yükseklik) piksel cinsinden
     */
    @Suppress("DEPRECATION")
    private fun getScreenDimensions(): Pair<Int, Int> {
        return try {
            val windowManager = application.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = windowManager.currentWindowMetrics.bounds
                Pair(bounds.width(), bounds.height())
            } else {
                val metrics = DisplayMetrics()
                windowManager.defaultDisplay.getRealMetrics(metrics)
                Pair(metrics.widthPixels, metrics.heightPixels)
            }
        } catch (e: Exception) {
            Pair(1080, 1920) // Varsayılan Full HD
        }
    }
}
