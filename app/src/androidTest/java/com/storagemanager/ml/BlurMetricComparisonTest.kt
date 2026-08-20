package com.storagemanager.ml

import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mevcut bulanıklık ölçütünü (128x128 küçültülmüş tam kare) gerçek çözünürlükten
 * alınan merkez kırpma ile karşılaştırır.
 *
 * Hipotez: küçültme bir alçak geçiren filtre olduğu için 128x128'te ölçülen Laplacian
 * varyansı odağı değil sahne dokusunu ölçüyor. Eğer öyleyse iki ölçüt birbiriyle
 * zayıf uyumlu olur ve işaretlenen fotoğraf kümesi büyük ölçüde değişir.
 */
@RunWith(AndroidJUnit4::class)
class BlurMetricComparisonTest {

    private companion object {
        const val TAG = "BlurCompare"
        const val SAMPLE_SIZE = 200
        const val THUMBNAIL_SIZE = 128

        /** Gerçek çözünürlükten alınan merkez kırpmanın kenarı. */
        const val CROP_SIZE = 512

        /** Karo yaklaşımında her bölgenin kenarı ve bölge sayısı. */
        const val TILE_SIZE = 256
        const val TILE_COUNT = 5
    }

    @Test
    fun compareThumbnailMetricAgainstNativeCrop() {
        val ids = queryPhotoIds()
        assumeTrue("Galeride fotoğraf yok veya izin verilmemiş", ids.isNotEmpty())
        val sample = ids.shuffled(kotlin.random.Random(7)).take(SAMPLE_SIZE)

        data class Row(val id: Long, val thumb: Double, val native: Double)
        val rows = ArrayList<Row>()

        for (id in sample) {
            val thumbVariance = thumbnailVariance(id) ?: continue
            val nativeVariance = nativeCropVariance(id) ?: continue
            rows += Row(id, thumbVariance, nativeVariance)
        }
        assumeTrue("Karşılaştırılabilir fotoğraf yok", rows.size >= 20)

        // Sıra korelasyonu (Spearman): iki ölçüt aynı fotoğrafları mı bulanık buluyor?
        val byThumb = rows.sortedBy { it.thumb }.withIndex().associate { it.value.id to it.index }
        val byNative = rows.sortedBy { it.native }.withIndex().associate { it.value.id to it.index }
        val n = rows.size
        var dSquaredSum = 0.0
        for (row in rows) {
            val d = (byThumb.getValue(row.id) - byNative.getValue(row.id)).toDouble()
            dSquaredSum += d * d
        }
        val spearman = 1 - (6 * dSquaredSum) / (n.toDouble() * (n * n - 1))

        // En bulanık %10'da kaç fotoğraf ortak?
        val topK = (n * 0.10).toInt().coerceAtLeast(3)
        val worstByThumb = rows.sortedBy { it.thumb }.take(topK).map { it.id }.toSet()
        val worstByNative = rows.sortedBy { it.native }.take(topK).map { it.id }.toSet()
        val overlap = worstByThumb.intersect(worstByNative).size

        val nativeSorted = rows.map { it.native }.sorted()
        Log.i(TAG, "======== Bulanıklık ölçütü karşılaştırması ========")
        Log.i(TAG, "Örneklem            : $n fotoğraf")
        Log.i(TAG, "Spearman korelasyonu: ${"%.3f".format(spearman)}")
        Log.i(TAG, "En bulanık %10 kesişimi: $overlap / $topK")
        Log.i(TAG, "128px varyans  : min ${"%.0f".format(rows.minOf { it.thumb })}, " +
                "medyan ${"%.0f".format(rows.map { it.thumb }.sorted()[n / 2])}, " +
                "max ${"%.0f".format(rows.maxOf { it.thumb })}")
        Log.i(TAG, "Yerel kırpma   : min ${"%.0f".format(nativeSorted.first())}, " +
                "p5 ${"%.0f".format(nativeSorted[(n * 0.05).toInt()])}, " +
                "p10 ${"%.0f".format(nativeSorted[(n * 0.10).toInt()])}, " +
                "medyan ${"%.0f".format(nativeSorted[n / 2])}, " +
                "max ${"%.0f".format(nativeSorted.last())}")
        Log.i(TAG, "==================================================")
    }

    /**
     * Karo (tile) yaklaşımını ölçer: tek merkez kırpma yerine birkaç bölgeden okur ve
     * **en yükseğini** alır.
     *
     * Gerekçesi iki yönlü. Merkez kırpma düz bir alana (gökyüzü, duvar) denk gelirse net
     * fotoğrafı bulanık sanır. Ayrıca kasıtlı arka plan bulanıklığı (portre modu) olan
     * fotoğraflarda net olan kısım yalnızca bir bölgedir; maksimum almak "herhangi bir
     * yeri net mi?" sorusuna cevap verir, ki bulanıklık için doğru soru budur.
     */
    @Test
    fun measureTiledMetricCostAndDistribution() {
        val ids = queryPhotoIds()
        assumeTrue("Galeride fotoğraf yok veya izin verilmemiş", ids.isNotEmpty())
        val sample = ids.shuffled(kotlin.random.Random(7)).take(SAMPLE_SIZE)

        val singleValues = ArrayList<Double>()
        val tiledValues = ArrayList<Double>()
        var singleMs = 0L
        var tiledMs = 0L
        var analysed = 0

        for (id in sample) {
            var single: Double? = null
            singleMs += kotlin.system.measureTimeMillis { single = nativeCropVariance(id) }
            if (single == null) continue
            var tiled: Double? = null
            tiledMs += kotlin.system.measureTimeMillis { tiled = tiledVariance(id) }
            if (tiled == null) continue
            singleValues += single!!
            tiledValues += tiled!!
            analysed++
        }
        assumeTrue("Karşılaştırılabilir fotoğraf yok", analysed >= 20)

        singleValues.sort()
        tiledValues.sort()
        fun pct(list: List<Double>, p: Double) = list[((list.size - 1) * p).toInt()]

        Log.i(TAG, "======== Karo ölçütü: maliyet ve dağılım ========")
        Log.i(TAG, "Örneklem      : $analysed fotoğraf")
        Log.i(TAG, "Tek kırpma    : ${"%.1f".format(singleMs.toDouble() / analysed)} ms/fotoğraf")
        Log.i(TAG, "Karo (${TILE_COUNT} bölge): ${"%.1f".format(tiledMs.toDouble() / analysed)} ms/fotoğraf")
        Log.i(TAG, "Tek kırpma dağılımı : p2 ${"%.0f".format(pct(singleValues, 0.02))}, " +
                "p5 ${"%.0f".format(pct(singleValues, 0.05))}, " +
                "p10 ${"%.0f".format(pct(singleValues, 0.10))}, " +
                "medyan ${"%.0f".format(pct(singleValues, 0.50))}")
        Log.i(TAG, "Karo dağılımı       : p2 ${"%.0f".format(pct(tiledValues, 0.02))}, " +
                "p5 ${"%.0f".format(pct(tiledValues, 0.05))}, " +
                "p10 ${"%.0f".format(pct(tiledValues, 0.10))}, " +
                "medyan ${"%.0f".format(pct(tiledValues, 0.50))}")
        Log.i(TAG, "17437 fotoğraf tahmini (karo, tek iş parçacığı): " +
                "${"%.0f".format(tiledMs.toDouble() / analysed * 17437 / 1000)} sn")
        Log.i(TAG, "================================================")
    }

    /**
     * İki aşamalı tasarımın uygulanabilirliğini ölçer.
     *
     * Fikir: ucuz 128px ölçütüyle bir aday listesi çıkar, pahalı karo ölçütünü yalnızca
     * o adaylara uygula. Bu ancak ucuz ölçütün **recall**'ü yüksekse işe yarar; yani
     * gerçekten bulanık olanları aday listesine alabiliyorsa. Kaçırdığı fotoğrafı ikinci
     * aşama asla geri getiremez.
     *
     * "Gerçekten bulanık" tanımı: karo ölçütünün en düşük %5'i.
     */
    @Test
    fun measurePrefilterRecall() {
        val ids = queryPhotoIds()
        assumeTrue("Galeride fotoğraf yok veya izin verilmemiş", ids.isNotEmpty())
        val sample = ids.shuffled(kotlin.random.Random(7)).take(SAMPLE_SIZE)

        data class Row(val id: Long, val thumb: Double, val tiled: Double)
        val rows = ArrayList<Row>()
        for (id in sample) {
            val t = thumbnailVariance(id) ?: continue
            val n = tiledVariance(id) ?: continue
            rows += Row(id, t, n)
        }
        assumeTrue("Karşılaştırılabilir fotoğraf yok", rows.size >= 40)

        val n = rows.size
        val trulyBlurryCount = (n * 0.05).toInt().coerceAtLeast(2)
        val trulyBlurry = rows.sortedBy { it.tiled }.take(trulyBlurryCount).map { it.id }.toSet()
        val byThumbAsc = rows.sortedBy { it.thumb }

        Log.i(TAG, "======== Ön eleme isabet oranı ========")
        Log.i(TAG, "Örneklem            : $n fotoğraf")
        Log.i(TAG, "'Gerçekten bulanık' : $trulyBlurryCount (karo ölçütünün en düşük %5'i)")
        for (share in listOf(0.10, 0.20, 0.30, 0.40, 0.50)) {
            val shortlist = byThumbAsc.take((n * share).toInt()).map { it.id }.toSet()
            val captured = trulyBlurry.count { it in shortlist }
            val costMs = share * 127.8 + 17.2
            Log.i(TAG, "Aday %${"%.0f".format(share * 100)}: " +
                    "yakalanan $captured/$trulyBlurryCount " +
                    "(recall %${"%.0f".format(captured * 100.0 / trulyBlurryCount)}), " +
                    "maliyet ~${"%.1f".format(costMs)} ms/fotoğraf, " +
                    "17437 foto / 6 işçi ≈ ${"%.0f".format(costMs * 17437 / 6 / 1000)} sn")
        }
        Log.i(TAG, "======================================")
    }

    /** Birkaç bölgeden okuyup en yüksek varyansı döndürür. */
    private fun tiledVariance(id: Long): Double? {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                @Suppress("DEPRECATION")
                val decoder = BitmapRegionDecoder.newInstance(stream, false) ?: return null
                val w = decoder.width
                val h = decoder.height
                if (w < TILE_SIZE * 2 || h < TILE_SIZE * 2) {
                    decoder.recycle()
                    return null
                }
                // Merkez + dört çeyrek merkezi
                val centers = listOf(
                    (w / 2) to (h / 2),
                    (w / 4) to (h / 4),
                    (3 * w / 4) to (h / 4),
                    (w / 4) to (3 * h / 4),
                    (3 * w / 4) to (3 * h / 4)
                )
                var best = 0.0
                val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                for ((cx, cy) in centers) {
                    val left = (cx - TILE_SIZE / 2).coerceIn(0, w - TILE_SIZE)
                    val top = (cy - TILE_SIZE / 2).coerceIn(0, h - TILE_SIZE)
                    val tile = decoder.decodeRegion(
                        Rect(left, top, left + TILE_SIZE, top + TILE_SIZE), options
                    ) ?: continue
                    val pixels = IntArray(tile.width * tile.height)
                    tile.getPixels(pixels, 0, tile.width, 0, 0, tile.width, tile.height)
                    val v = BlurDetector.laplacianVariance(pixels, tile.width, tile.height)
                    tile.recycle()
                    if (v > best) best = v
                }
                decoder.recycle()
                best
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Mevcut üretim yolu: sistem thumbnail'i, 128x128 kareye normalize. */
    private fun thumbnailVariance(id: Long): Double? {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = try {
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            context.contentResolver.loadThumbnail(uri, Size(THUMBNAIL_SIZE, THUMBNAIL_SIZE), null)
        } catch (e: Exception) {
            return null
        }
        val square = if (bitmap.width == THUMBNAIL_SIZE && bitmap.height == THUMBNAIL_SIZE) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, THUMBNAIL_SIZE, THUMBNAIL_SIZE, true)
        }
        val pixels = IntArray(square.width * square.height)
        square.getPixels(pixels, 0, square.width, 0, 0, square.width, square.height)
        val variance = BlurDetector.laplacianVariance(pixels, square.width, square.height)
        if (square !== bitmap) square.recycle()
        bitmap.recycle()
        return variance
    }

    /** Önerilen yol: gerçek çözünürlükten merkez kırpma, küçültme yok. */
    private fun nativeCropVariance(id: Long): Double? {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                @Suppress("DEPRECATION")
                val decoder = BitmapRegionDecoder.newInstance(stream, false) ?: return null
                val w = decoder.width
                val h = decoder.height
                if (w < CROP_SIZE || h < CROP_SIZE) {
                    decoder.recycle()
                    return null
                }
                val left = (w - CROP_SIZE) / 2
                val top = (h - CROP_SIZE) / 2
                val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                val crop = decoder.decodeRegion(
                    Rect(left, top, left + CROP_SIZE, top + CROP_SIZE), options
                )
                decoder.recycle()
                if (crop == null) return null

                val pixels = IntArray(crop.width * crop.height)
                crop.getPixels(pixels, 0, crop.width, 0, 0, crop.width, crop.height)
                val variance = BlurDetector.laplacianVariance(pixels, crop.width, crop.height)
                crop.recycle()
                variance
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun queryPhotoIds(): List<Long> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ids = ArrayList<Long>(4096)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            null,
            null,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) ids += cursor.getLong(idCol)
        }
        return ids
    }
}
