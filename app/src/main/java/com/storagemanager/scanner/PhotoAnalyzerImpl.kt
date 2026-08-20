package com.storagemanager.scanner

import android.app.Application
import android.content.ContentUris
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.storagemanager.data.preferences.ScanSettings
import com.storagemanager.domain.model.JunkPhoto
import com.storagemanager.domain.model.JunkType
import com.storagemanager.ml.BlurDetector
import com.storagemanager.ml.DuplicateDetector
import com.storagemanager.ml.DuplicateGrouper
import com.storagemanager.ml.PhotoClassifier
import com.storagemanager.ml.PhotoContent
import com.storagemanager.ml.SharpnessProbe
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import com.storagemanager.R

/**
 * Fotoğraf analiz implementasyonu.
 *
 * Tek MediaStore geçişinde ekran görüntüsü / eski fotoğraf tespiti yapar,
 * ardından paralel olarak thumbnail üretip bulanıklık, perceptual hash ve
 * (ayarlardan açıksa) ML Kit içerik sınıflandırması hesaplar.
 */
@Singleton
class PhotoAnalyzerImpl @Inject constructor(
    private val application: Application,
    private val blurDetector: BlurDetector,
    private val duplicateDetector: DuplicateDetector,
    private val photoClassifier: PhotoClassifier,
    private val sharpnessProbe: SharpnessProbe,
    private val scanSettings: ScanSettings
) : PhotoAnalyzer {

    private companion object {
        const val TAG = "PhotoAnalyzer"

        /**
         * Bulanıklık ve hash hesabının yapıldığı sabit thumbnail kenarı.
         * Laplacian varyansı çözünürlüğe duyarlı olduğu için tüm fotoğraflar
         * aynı boyuta normalize edilir; aksi halde eşik değeri anlamsızlaşır.
         */
        const val THUMBNAIL_SIZE = 128

        /**
         * ML Kit sınıflandırma girdisinin kenarı. Taban etiketleme modeli 224x224
         * ile eğitildiği için daha küçük girdide isabet belirgin düşüyor.
         * Sınıflandırma açıkken ham thumbnail bu boyutta çözülür, [THUMBNAIL_SIZE]
         * girdisi de aynı çözmeden türetilir — dosya iki kez okunmaz.
         */
        const val ML_INPUT_SIZE = 224

        /**
         * Gerçek çözünürlükte netlik ölçülecek aday dilimi (kaba skora göre en düşük %40).
         *
         * Cihazda ölçülen takas: tam geçiş %100 recall / ~6.2 dk, %40 dilimi %89 recall /
         * ~3.3 dk. Örneklem küçük olduğu için (9 "gerçekten bulanık" fotoğraf) recall
         * rakamları gürültülü; bu oran daha fazla veriyle yeniden değerlendirilmeli.
         */
        const val BLUR_PREFILTER_SHARE = 0.40

        /** Listeyi şişirmemek için "eski fotoğraf" sonuç sınırı. */
        const val MAX_OLD_PHOTOS = 200

        /** Paralel thumbnail işleyen iş parçacığı sayısı. */
        val WORKER_COUNT = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)

        val PROJECTION = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )

        val SCREENSHOT_DIRS = listOf(
            "/screenshots/",
            "/dcim/screenshots/",
            "/pictures/screenshots/",
            "/screen captures/",
            "/screencapture/"
        )
    }

    private data class PhotoMeta(
        val id: Long,
        val name: String,
        val path: String,
        val size: Long,
        val dateModified: Long,
        val width: Int,
        val height: Int
    ) {
        val uri: String
            get() = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
            ).toString()
    }

    /**
     * Tüm fotoğraf analizlerini (bulanık, duplike, ekran görüntüsü, eski) tek geçişte,
     * canlı ilerleme bildirimi ile gerçekleştirir.
     */
    override suspend fun scanAllPhotos(onProgress: ((Float, String) -> Unit)?): List<JunkPhoto> =
        withContext(Dispatchers.IO) {
            try {
                val settings = scanSettings.current()
                val results = mutableListOf<JunkPhoto>()

                // ── 1. Metadata taraması: tek cursor, ekran görüntüsü + eski tespiti ──
                onProgress?.invoke(0f, application.getString(R.string.progress_photos_listing))
                val metaList = collectMetadata(settings, results)
                if (metaList.isEmpty()) return@withContext results

                // ── 2. Paralel thumbnail: dHash + kaba netlik sıralaması ──
                val analyzed = analyzeThumbnails(metaList, onProgress)

                // ── 3. Adayların netliğini gerçek çözünürlükten doğrula (+ ML koruma) ──
                results += confirmBlurry(
                    candidates = analyzed.candidates,
                    threshold = settings.blurThreshold.toDouble(),
                    aiEnabled = settings.aiClassificationEnabled,
                    onProgress = onProgress
                )

                // ── 4. Duplike gruplama (bantlı aday üretimi) ──
                onProgress?.invoke(1f, application.getString(R.string.progress_photos_grouping))
                results += buildDuplicates(analyzed.hashedPhotos, analyzed.hashes)

                results
            } catch (e: Exception) {
                Log.e(TAG, "Fotoğraf taraması başarısız", e)
                emptyList()
            } finally {
                // Model belleği tarama boyunca açık kalır, sonunda serbest bırakılır.
                photoClassifier.close()
            }
        }

    override suspend fun previewBlurry(sampleSize: Int, threshold: Double): List<JunkPhoto> =
        withContext(Dispatchers.IO) {
            try {
                val sample = queryAllMeta().shuffled().take(sampleSize)
                if (sample.isEmpty()) return@withContext emptyList()

                // Ön eleme oranı örneklem içinde de aynı; böylece önizleme, tam taramanın
                // aynı fotoğrafı işaretleyip işaretlemeyeceğini doğru temsil eder.
                val analyzed = analyzeThumbnails(sample, onProgress = null)
                confirmBlurry(
                    candidates = analyzed.candidates,
                    threshold = threshold,
                    aiEnabled = false,
                    onProgress = null
                ).sortedBy { it.score }
            } catch (e: Exception) {
                Log.e(TAG, "Bulanıklık önizlemesi başarısız", e)
                emptyList()
            }
        }

    /** Tüm fotoğrafların metadata'sını okur — önizleme örneklemi için. */
    private fun queryAllMeta(): List<PhotoMeta> {
        val metaList = ArrayList<PhotoMeta>()
        query()?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val dateAddedCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                val path = cursor.getString(dataCol) ?: continue
                metaList += PhotoMeta(
                    id = cursor.getLong(idCol),
                    name = name,
                    path = path,
                    size = cursor.getLong(sizeCol),
                    dateModified = sanitizedDateModified(cursor, dateCol, dateAddedCol, path),
                    width = cursor.getInt(widthCol),
                    height = cursor.getInt(heightCol)
                )
            }
        }
        return metaList
    }

    /**
     * MediaStore'u tek geçişte okur. Ekran görüntüsü ve eski fotoğrafları anında
     * [results] listesine ekler, kalan tüm fotoğrafların metadata'sını döndürür.
     */
    private suspend fun collectMetadata(
        settings: ScanSettings.Snapshot,
        results: MutableList<JunkPhoto>
    ): List<PhotoMeta> {
        val metaList = ArrayList<PhotoMeta>()
        val (screenWidth, screenHeight) = getScreenDimensions()
        val cutoffTime = oldPhotoCutoffSeconds(settings.oldPhotoDays)
        var oldCount = 0

        query()?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val dateAddedCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

            var rowIndex = 0
            while (cursor.moveToNext()) {
                // Uzun taramalarda iptali destekle
                if (++rowIndex % 256 == 0) coroutineContext.ensureActive()

                val name = cursor.getString(nameCol) ?: continue
                val path = cursor.getString(dataCol) ?: continue
                val meta = PhotoMeta(
                    id = cursor.getLong(idCol),
                    name = name,
                    path = path,
                    size = cursor.getLong(sizeCol),
                    dateModified = sanitizedDateModified(cursor, dateCol, dateAddedCol, path),
                    width = cursor.getInt(widthCol),
                    height = cursor.getInt(heightCol)
                )

                if (isScreenshot(meta, screenWidth, screenHeight)) {
                    results += meta.toJunkPhoto(JunkType.SCREENSHOT, score = 1f)
                }

                // Not: sayaç kullanılıyor — önceki sürümdeki results.count{} çağrısı
                // her satırda tüm listeyi geziyor ve taramayı O(N²) yapıyordu.
                if (meta.dateModified in 1 until cutoffTime && oldCount < MAX_OLD_PHOTOS) {
                    results += meta.toJunkPhoto(JunkType.OLD, score = 1f)
                    oldCount++
                }

                metaList += meta
            }
        }
        return metaList
    }

    /** Birinci aşamanın çıktısı: kaba netlik skoruyla eşlenmiş fotoğraf. */
    private class BlurCandidate(val meta: PhotoMeta, val roughSharpness: Double)

    private class ThumbnailAnalysis(
        val candidates: List<BlurCandidate>,
        val hashedPhotos: List<PhotoMeta>,
        val hashes: LongArray
    )

    /** Tek bir işçinin ürettiği kısmi sonuç. */
    private class PartialResult(
        val candidates: ArrayList<BlurCandidate>,
        val hashed: ArrayList<PhotoMeta>,
        val hashes: ArrayList<Long>
    )

    /**
     * Birinci aşama: her fotoğrafın thumbnail'inden dHash ve **kaba** netlik göstergesi.
     *
     * Buradaki thumbnail varyansı bulanıklık kararı için kullanılmaz — küçültme yüksek
     * frekanslı detayı yok ettiği için o ölçek odağı değil sahne dokusunu ölçüyor
     * (bkz. [SharpnessProbe]). Yalnızca **aday sıralaması** için kullanılır: pahalı
     * gerçek-çözünürlük ölçümünü galerinin en düşük [BLUR_PREFILTER_SHARE] dilimine
     * yönlendirir. dHash için thumbnail zaten çözüldüğünden bu sıralama bedava gelir.
     */
    private suspend fun analyzeThumbnails(
        metaList: List<PhotoMeta>,
        onProgress: ((Float, String) -> Unit)?
    ): ThumbnailAnalysis = coroutineScope {
        val total = metaList.size
        val processed = AtomicInteger(0)
        val lastReportedPercent = AtomicInteger(-1)

        // Her işçi listenin bir dilimini işler; kilit gerektirmeyen yerel listelerde
        // biriktirip sonda birleştiriyoruz (senkronize liste erişimi darboğazdı).
        val chunkSize = (total + WORKER_COUNT - 1) / WORKER_COUNT
        val partials = metaList.chunked(chunkSize.coerceAtLeast(1)).map { chunk ->
            async(Dispatchers.Default) {
                val partial = PartialResult(ArrayList(chunk.size), ArrayList(chunk.size), ArrayList(chunk.size))

                for (meta in chunk) {
                    coroutineContext.ensureActive()
                    try {
                        analyzeOne(meta, partial)
                    } catch (e: Exception) {
                        Log.w(TAG, "Fotoğraf işlenemedi: ${meta.name}", e)
                    }

                    val done = processed.incrementAndGet()
                    val percent = done * 100 / total
                    // Yalnızca yüzde değiştiğinde bildir — aksi halde UI gereksiz yere yeniden çizilir
                    if (lastReportedPercent.getAndSet(percent) != percent) {
                        onProgress?.invoke(done.toFloat() / total, application.getString(R.string.progress_photos_scanning, percent))
                    }
                }
                partial
            }
        }.awaitAll()

        val hashedPhotos = ArrayList<PhotoMeta>(total)
        val hashList = ArrayList<Long>(total)
        val candidates = ArrayList<BlurCandidate>(total)
        for (partial in partials) {
            hashedPhotos += partial.hashed
            hashList += partial.hashes
            candidates += partial.candidates
        }

        ThumbnailAnalysis(candidates, hashedPhotos, hashList.toLongArray())
    }

    /** Tek bir fotoğrafın thumbnail'inden dHash ve kaba netlik göstergesi çıkarır. */
    private fun analyzeOne(meta: PhotoMeta, partial: PartialResult) {
        // yarı bellek; kaba netlik ve hash zaten gri tonlamaya iniyor
        val raw = loadRawThumbnail(meta, THUMBNAIL_SIZE, Bitmap.Config.RGB_565) ?: return
        var thumbnail: Bitmap? = null
        try {
            thumbnail = scaleToSquare(raw, THUMBNAIL_SIZE)
            partial.candidates += BlurCandidate(meta, blurDetector.roughSharpness(thumbnail))

            val hash = duplicateDetector.computeHash(thumbnail)
            if (hash != 0L) {
                partial.hashed += meta
                partial.hashes += hash
            }
        } finally {
            if (thumbnail !== raw) thumbnail?.recycle()
            raw.recycle()
        }
    }

    /**
     * İkinci aşama: aday fotoğrafların netliğini gerçek çözünürlükten ölçer ve
     * bulanık olanları döndürür.
     *
     * Yalnızca en düşük kaba skora sahip [BLUR_PREFILTER_SHARE] dilimi ölçülür.
     * Cihazda ölçülen denge (195 fotoğraflık örneklem): tam geçiş ~6.2 dk ve %100
     * recall; %40 aday dilimi ~3.3 dk ve %89 recall. Ön eleme yalnızca recall'ü
     * düşürür, precision'a dokunmaz — son kararı her zaman gerçek ölçüt verir. Bu
     * uygulamada doğru takas budur: bulanık bir fotoğrafı kaçırmak zararsız, net bir
     * fotoğrafı silmeye aday göstermek zararlıdır.
     */
    private suspend fun confirmBlurry(
        candidates: List<BlurCandidate>,
        threshold: Double,
        aiEnabled: Boolean,
        onProgress: ((Float, String) -> Unit)?
    ): List<JunkPhoto> = coroutineScope {
        if (candidates.isEmpty()) return@coroutineScope emptyList()

        val shortlistSize = (candidates.size * BLUR_PREFILTER_SHARE).toInt().coerceAtLeast(1)
        val shortlist = candidates.sortedBy { it.roughSharpness }.take(shortlistSize)

        val total = shortlist.size
        val processed = AtomicInteger(0)
        val lastReportedPercent = AtomicInteger(-1)
        val progressRes = if (aiEnabled) {
            R.string.progress_photos_sharpness_ai
        } else {
            R.string.progress_photos_sharpness
        }

        // Bölge çözme ve (açıksa) ML çıkarımı bloklayıcı; bloklamaya uygun havuz IO.
        val chunkSize = (total + WORKER_COUNT - 1) / WORKER_COUNT
        shortlist.chunked(chunkSize.coerceAtLeast(1)).map { chunk ->
            async(Dispatchers.IO) {
                val blurry = ArrayList<JunkPhoto>()
                for (candidate in chunk) {
                    coroutineContext.ensureActive()
                    try {
                        val sharpness = sharpnessProbe.sharpness(Uri.parse(candidate.meta.uri))
                        // null = ölçemedik. Kanıt yokken fotoğrafı silmeye aday gösterme.
                        if (sharpness != null && sharpness < threshold &&
                            !(aiEnabled && isProtectedContent(candidate.meta))
                        ) {
                            blurry += candidate.meta.toJunkPhoto(
                                JunkType.BLURRY,
                                score = sharpness.toFloat()
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Netlik doğrulanamadı: ${candidate.meta.name}", e)
                    }

                    val done = processed.incrementAndGet()
                    val percent = done * 100 / total
                    if (lastReportedPercent.getAndSet(percent) != percent) {
                        onProgress?.invoke(done.toFloat() / total, application.getString(progressRes, percent))
                    }
                }
                blurry
            }
        }.awaitAll().flatten()
    }

    /**
     * Fotoğraf kişi veya evcil hayvan içeriyor mu — yani bulanık olsa bile
     * silme adayı gösterilmemeli mi?
     *
     * Sınıflandırma için ayrı ve daha büyük bir thumbnail çözülür: taban etiketleme
     * modeli 224x224 ile eğitildiği için 128'lik girdide isabet belirgin düşüyor.
     * İkinci çözmenin maliyetini yalnızca bulanık adaylar öder.
     */
    private fun isProtectedContent(meta: PhotoMeta): Boolean {
        val input = loadRawThumbnail(meta, ML_INPUT_SIZE, Bitmap.Config.ARGB_8888) ?: return false
        val scaled = scaleToSquare(input, ML_INPUT_SIZE)
        return try {
            PhotoClassifier.isProtected(photoClassifier.classify(scaled))
        } finally {
            if (scaled !== input) scaled.recycle()
            input.recycle()
        }
    }

    /**
     * Hash'lere göre duplike gruplarını [JunkPhoto] listesine dönüştürür.
     */
    private fun buildDuplicates(photos: List<PhotoMeta>, hashes: LongArray): List<JunkPhoto> {
        if (photos.size < 2) return emptyList()

        val duplicates = mutableListOf<JunkPhoto>()
        for (group in DuplicateGrouper.group(hashes, DuplicateDetector.DEFAULT_THRESHOLD)) {
            val groupId = "dup_${photos[group.first()].id}"
            for (index in group) {
                duplicates += photos[index].toJunkPhoto(
                    JunkType.DUPLICATE,
                    score = 0f,
                    groupId = groupId
                )
            }
        }
        return duplicates
    }

    private fun PhotoMeta.toJunkPhoto(
        junkType: JunkType,
        score: Float,
        groupId: String? = null
    ) = JunkPhoto(
        id = id,
        uri = uri,
        path = path,
        name = name,
        size = size,
        dateModified = dateModified,
        width = width,
        height = height,
        junkType = junkType,
        score = score,
        groupId = groupId
    )

    private fun isScreenshot(meta: PhotoMeta, screenWidth: Int, screenHeight: Int): Boolean {
        val lowerPath = meta.path.lowercase()
        if (SCREENSHOT_DIRS.any { lowerPath.contains(it) }) return true

        val matchesScreenSize = (meta.width == screenWidth && meta.height == screenHeight) ||
                (meta.width == screenHeight && meta.height == screenWidth)
        val nameHints = meta.name.contains("screenshot", ignoreCase = true) ||
                meta.name.contains("ekran görüntüsü", ignoreCase = true)
        return matchesScreenSize && nameHints
    }

    /** "Eski fotoğraf" eşiği — ayarlardaki gün sayısı ile içinde bulunulan yılın başından erken olanı. */
    private fun oldPhotoCutoffSeconds(olderThanDays: Int): Long {
        val currentYearStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis / 1000

        return minOf(
            (System.currentTimeMillis() / 1000) - TimeUnit.DAYS.toSeconds(olderThanDays.toLong()),
            currentYearStart
        )
    }

    /**
     * Bitmap'i sabit kenarlı kareye ölçekler.
     *
     * Bulanıklık ve hash hesabı çözünürlüğe duyarlı olduğu için tüm girdiler aynı
     * boyuta normalize edilir; aksi halde eşik değerleri cihazdan cihaza kayar.
     * Zaten hedef boyuttaysa kaynak bitmap olduğu gibi döner — çağıran bu yüzden
     * geri dönen nesneyi kaynakla `!==` karşılaştırmadan recycle etmemelidir.
     */
    private fun scaleToSquare(source: Bitmap, size: Int): Bitmap {
        if (source.width == size && source.height == size) return source
        return try {
            Bitmap.createScaledBitmap(source, size, size, true)
        } catch (e: Exception) {
            Log.w(TAG, "Thumbnail $size boyutuna ölçeklenemedi", e)
            source
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Thumbnail ölçekleme için bellek yetersiz")
            source
        }
    }

    /**
     * Hedef kenar uzunluğuna en yakın ham thumbnail'i döndürür.
     *
     * @param targetSize İstenen kenar; ML açıkken [ML_INPUT_SIZE], değilse [THUMBNAIL_SIZE].
     *   Tek çözme işleminden hem ML girdisi hem de bulanıklık/hash girdisi türetilir.
     * @param config Dosyadan çözerken kullanılacak piksel formatı. Bulanıklık ve hash
     *   gri tonlamaya indiğinden RGB_565 yeterlidir; ML Kit ise ARGB_8888 bekler.
     */
    private fun loadRawThumbnail(meta: PhotoMeta, targetSize: Int, config: Bitmap.Config): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, meta.id
                )
                return application.contentResolver.loadThumbnail(
                    uri,
                    android.util.Size(targetSize, targetSize),
                    null
                )
            } catch (e: Exception) {
                // Sistem önbelleğinde yoksa dosyadan okumaya düşeriz
            }
        }

        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(meta.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds, targetSize, targetSize)
                inPreferredConfig = config
            }
            BitmapFactory.decodeFile(meta.path, options)
        } catch (e: Exception) {
            Log.w(TAG, "Thumbnail okunamadı: ${meta.path}", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Thumbnail için bellek yetersiz: ${meta.path}")
            null
        }
    }

    /** Hedef boyuta ulaşana kadar 2'nin katları şeklinde örnekleme oranı hesaplar. */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        var halfHeight = options.outHeight / 2
        var halfWidth = options.outWidth / 2
        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

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
            Log.w(TAG, "Ekran boyutu alınamadı, varsayılan kullanılıyor", e)
            Pair(1080, 1920)
        }
    }

    private fun sanitizedDateModified(cursor: Cursor, dateCol: Int, dateAddedCol: Int, path: String): Long {
        var dateModified = cursor.getLong(dateCol)
        if (dateModified <= 0 && dateAddedCol != -1) {
            dateModified = cursor.getLong(dateAddedCol)
        }
        if (dateModified <= 0) {
            dateModified = try {
                java.io.File(path).lastModified() / 1000L
            } catch (e: Exception) {
                0L
            }
        }
        return dateModified
    }

    /**
     * Tek sorgu ile tüm fotoğrafları döndürür.
     *
     * Önceki sürüm LIMIT/OFFSET ile sayfalıyordu; MediaStore her sayfada tüm tabloyu
     * yeniden sıralamak zorunda kaldığı için maliyet O(N²/sayfa) oluyordu.
     */
    private fun query(): Cursor? {
        return try {
            application.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                PROJECTION,
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore sorgusu başarısız", e)
            null
        }
    }
}
