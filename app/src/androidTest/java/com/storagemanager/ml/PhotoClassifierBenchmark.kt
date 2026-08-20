package com.storagemanager.ml

import android.content.ContentUris
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Cihaz üstünde ML Kit sınıflandırma hızını ve isabetini ölçer.
 *
 * Bir doğrulama testi değil, ölçüm aracıdır: gerçek galeriden örnek alır ve
 * sonuçları logcat'e "MLBench" etiketiyle yazar. Tarama süresinin kabul edilebilir
 * olup olmadığına bu sayılarla karar veriyoruz.
 *
 * Çalıştırmak için:
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.storagemanager.ml.PhotoClassifierBenchmark
 */
@RunWith(AndroidJUnit4::class)
class PhotoClassifierBenchmark {

    private companion object {
        const val TAG = "MLBench"
        const val SAMPLE_SIZE = 300
        const val ML_INPUT_SIZE = 224
        const val THUMBNAIL_SIZE = 128
    }

    @Test
    fun measureClassificationThroughput() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ids = queryPhotoIds(SAMPLE_SIZE)
        assumeTrue("Galeride fotoğraf yok veya izin verilmemiş", ids.isNotEmpty())

        val classifier = PhotoClassifier()
        val counts = mutableMapOf<PhotoContent, Int>()
        var decodeMs = 0L
        var classifyMs = 0L
        var analysed = 0

        try {
            for (id in ids) {
                var raw: Bitmap? = null
                decodeMs += measureTimeMillis {
                    raw = loadThumbnail(id)
                }
                val bitmap = raw ?: continue

                val input = if (bitmap.width == ML_INPUT_SIZE && bitmap.height == ML_INPUT_SIZE) {
                    bitmap
                } else {
                    Bitmap.createScaledBitmap(bitmap, ML_INPUT_SIZE, ML_INPUT_SIZE, true)
                }

                var content = PhotoContent.UNKNOWN
                classifyMs += measureTimeMillis {
                    content = classifier.classify(input)
                }
                counts[content] = (counts[content] ?: 0) + 1
                analysed++

                // Bulanıklık/hash girdisi de aynı ham kaynaktan türetiliyor — maliyeti ölçüme dahil
                val thumb = Bitmap.createScaledBitmap(bitmap, THUMBNAIL_SIZE, THUMBNAIL_SIZE, true)
                thumb.recycle()

                if (input !== bitmap) input.recycle()
                bitmap.recycle()
            }
        } finally {
            classifier.close()
        }

        assumeTrue("Hiç fotoğraf çözülemedi", analysed > 0)

        val totalMs = decodeMs + classifyMs
        val perPhoto = totalMs.toDouble() / analysed
        Log.i(TAG, "======== ML Kit sınıflandırma ölçümü ========")
        Log.i(TAG, "Örneklem            : $analysed fotoğraf")
        Log.i(TAG, "Çözme (decode)      : ${decodeMs}ms toplam, ${"%.1f".format(decodeMs.toDouble() / analysed)}ms/fotoğraf")
        Log.i(TAG, "Sınıflandırma       : ${classifyMs}ms toplam, ${"%.1f".format(classifyMs.toDouble() / analysed)}ms/fotoğraf")
        Log.i(TAG, "Tek iş parçacığı    : ${"%.1f".format(perPhoto)}ms/fotoğraf")
        for (content in PhotoContent.entries) {
            val n = counts[content] ?: 0
            Log.i(TAG, "  $content: $n (%${"%.1f".format(n * 100.0 / analysed)})")
        }
        // 6 işçiyle 17.4k fotoğrafın kabaca ne kadar süreceği
        val projectedSec = perPhoto * 17441 / 6 / 1000
        Log.i(TAG, "17441 fotoğraf / 6 işçi tahmini: ${"%.0f".format(projectedSec)} sn")
        Log.i(TAG, "============================================")
    }

    /**
     * Aynı işi 6 işçiyle paralel yapar.
     *
     * Kritik soru: ML Kit çıkarımı gerçekten paralel ölçekleniyor mu, yoksa içeride
     * tek bir kuyrukta mı seri hale geliyor? Tarama süresi tahmini buna bağlı.
     */
    @Test
    fun measureParallelSpeedup() {
        val ids = queryPhotoIds(SAMPLE_SIZE)
        assumeTrue("Galeride fotoğraf yok veya izin verilmemiş", ids.isNotEmpty())

        val classifier = PhotoClassifier()
        val workers = 6
        val analysed = java.util.concurrent.atomic.AtomicInteger(0)

        val wallMs = try {
            measureTimeMillis {
                runBlocking {
                    val chunkSize = (ids.size + workers - 1) / workers
                    ids.chunked(chunkSize).map { chunk ->
                        async(Dispatchers.IO) {
                            for (id in chunk) {
                                val bitmap = loadThumbnail(id) ?: continue
                                val input = if (bitmap.width == ML_INPUT_SIZE && bitmap.height == ML_INPUT_SIZE) {
                                    bitmap
                                } else {
                                    Bitmap.createScaledBitmap(bitmap, ML_INPUT_SIZE, ML_INPUT_SIZE, true)
                                }
                                classifier.classify(input)
                                analysed.incrementAndGet()
                                if (input !== bitmap) input.recycle()
                                bitmap.recycle()
                            }
                        }
                    }.awaitAll()
                }
            }
        } finally {
            classifier.close()
        }

        val count = analysed.get()
        assumeTrue("Hiç fotoğraf çözülemedi", count > 0)
        val perPhoto = wallMs.toDouble() / count
        Log.i(TAG, "======== $workers işçiyle paralel ölçüm ========")
        Log.i(TAG, "Örneklem      : $count fotoğraf")
        Log.i(TAG, "Duvar saati   : ${wallMs}ms, ${"%.1f".format(perPhoto)}ms/fotoğraf")
        Log.i(TAG, "17441 fotoğraf tahmini: ${"%.0f".format(perPhoto * 17441 / 1000)} sn")
        Log.i(TAG, "==============================================")
    }



    private fun queryPhotoIds(limit: Int): List<Long> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // limit Int.MAX_VALUE olabiliyor; ön tahsisi makul bir üst sınırla kapat
        val ids = ArrayList<Long>(limit.coerceAtMost(4096))
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            null,
            null,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext() && ids.size < limit) {
                ids += cursor.getLong(idCol)
            }
        }
        return ids
    }

    private fun loadThumbnail(id: Long, size: Int = ML_INPUT_SIZE): Bitmap? {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return try {
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            context.contentResolver.loadThumbnail(uri, Size(size, size), null)
        } catch (e: Exception) {
            null
        }
    }
}
