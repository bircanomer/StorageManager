package com.storagemanager.ml

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Netlik ölçümü — fotoğrafın **gerçek çözünürlüğünden** okur.
 *
 * Neden thumbnail yetmiyor: küçültme işleminin kendisi bir alçak geçiren filtredir ve
 * bulanıklığı ayırt eden yüksek frekanslı detayı yok eder. 128x128'e indirilmiş bir
 * karede ölçülen Laplacian varyansı odağı değil sahnedeki doku miktarını ölçer —
 * net bir düz duvar fotoğrafı düşük, bulanık bir şehir manzarası yüksek çıkar.
 *
 * Cihazda ölçüldü (195 fotoğraf): thumbnail ölçütü ile gerçek çözünürlük ölçütü
 * arasında Spearman korelasyonu yalnızca 0.476, en bulanık %10'da kesişim 7/19.
 * Yani thumbnail üzerinden verilen karar büyük ölçüde başka fotoğrafları işaretliyordu.
 *
 * Bu sınıf [BitmapRegionDecoder] ile birkaç küçük bölgeyi 1:1 çözer ve **en yüksek**
 * varyansı döndürür. Maksimum almanın iki gerekçesi var:
 *  - tek bir merkez kırpma düz bir alana (gökyüzü, duvar) denk gelirse net fotoğrafı
 *    bulanık sanar
 *  - portre modunda arka plan kasıtlı bulanıktır; doğru soru "her yeri net mi" değil,
 *    "herhangi bir yeri net mi" sorusudur
 */
@Singleton
class SharpnessProbe @Inject constructor(
    private val application: Application
) {

    /**
     * Fotoğrafın netlik skorunu döndürür; yüksek değer daha net demektir.
     *
     * @return Ölçülen en yüksek Laplacian varyansı, ölçüm yapılamazsa `null`.
     *   `null` "bulanık" anlamına gelmez — çağıran bu durumda fotoğrafı işaretlememeli,
     *   çünkü okunamayan bir dosya hakkında elimizde kanıt yoktur.
     */
    fun sharpness(uri: Uri): Double? {
        return try {
            application.contentResolver.openInputStream(uri)?.use { stream ->
                @Suppress("DEPRECATION")
                val decoder = BitmapRegionDecoder.newInstance(stream, false) ?: return null
                try {
                    probe(decoder)
                } finally {
                    decoder.recycle()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Netlik ölçülemedi: $uri", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Netlik ölçümü için bellek yetersiz: $uri")
            null
        }
    }

    private fun probe(decoder: BitmapRegionDecoder): Double? {
        val width = decoder.width
        val height = decoder.height
        // Karolar sığmıyorsa ölçüm anlamsız; küçük görüntülerde zaten bulanıklık kararı
        // vermeye çalışmıyoruz.
        if (width < TILE_SIZE * 2 || height < TILE_SIZE * 2) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }

        var best = 0.0
        var measured = 0
        for ((cx, cy) in tileCenters(width, height)) {
            val left = (cx - TILE_SIZE / 2).coerceIn(0, width - TILE_SIZE)
            val top = (cy - TILE_SIZE / 2).coerceIn(0, height - TILE_SIZE)
            val tile = try {
                decoder.decodeRegion(Rect(left, top, left + TILE_SIZE, top + TILE_SIZE), options)
            } catch (e: Exception) {
                null
            } ?: continue

            val pixels = IntArray(tile.width * tile.height)
            tile.getPixels(pixels, 0, tile.width, 0, 0, tile.width, tile.height)
            val variance = BlurDetector.laplacianVariance(pixels, tile.width, tile.height)
            tile.recycle()
            measured++
            if (variance > best) best = variance
        }
        return if (measured == 0) null else best
    }

    /** Merkez ve dört çeyreğin merkezi — kadraj neresi olursa olsun bir öznesi yakalanır. */
    private fun tileCenters(width: Int, height: Int): List<Pair<Int, Int>> = listOf(
        (width / 2) to (height / 2),
        (width / 4) to (height / 4),
        (3 * width / 4) to (height / 4),
        (width / 4) to (3 * height / 4),
        (3 * width / 4) to (3 * height / 4)
    )

    companion object {
        private const val TAG = "SharpnessProbe"

        /**
         * Her bölgenin kenarı. Bölge başına çözme çağrısının sabit bir maliyeti var,
         * bu yüzden çok sayıda küçük karo yerine az sayıda orta boy karo tercih edildi.
         * Cihazda ölçülen maliyet: 5 karo ≈ 128 ms/fotoğraf.
         */
        const val TILE_SIZE = 256

        /**
         * Varsayılan netlik eşiği — altındaki skorlar bulanık sayılır.
         *
         * Gerçek galeride ölçülen dağılım (195 fotoğraf): p2 30, p5 54, p10 97,
         * medyan 745. 60 kabaca en bulanık %5'e denk geliyor.
         *
         * Not: bu ölçek eski thumbnail tabanlı eşikle **karşılaştırılamaz**; ayrı bir
         * ayar anahtarında saklanmasının sebebi de bu.
         */
        const val DEFAULT_THRESHOLD = 60.0

        /** Kullanıcının ayarlayabileceği aralık. */
        const val MIN_THRESHOLD = 20f
        const val MAX_THRESHOLD = 500f
    }
}
