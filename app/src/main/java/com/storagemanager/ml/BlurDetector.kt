package com.storagemanager.ml

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Laplacian varyansı hesabı — kenar/detay miktarının ölçüsü.
 *
 * Bulanıklık **kararı burada verilmez**. Laplacian varyansı çözünürlüğe duyarlıdır ve
 * küçültülmüş bir thumbnail üzerinde hesaplandığında odağı değil sahnedeki doku
 * miktarını ölçer; gerçek karar [SharpnessProbe] tarafından fotoğrafın kendi
 * çözünürlüğünden alınan bölgelerle verilir.
 *
 * Buradaki [roughSharpness] yalnızca hangi fotoğrafların pahalı ölçüme gönderileceğini
 * sıralamak için kullanılır.
 */
@Singleton
class BlurDetector @Inject constructor() {

    /**
     * Thumbnail üzerinden **kaba** netlik göstergesi.
     *
     * Ölçemediğinde [Double.MAX_VALUE] döner: aday sıralamasının sonuna düşsün, yani
     * kanıt yokken fotoğraf pahalı ölçüme ve dolayısıyla işaretlenmeye aday olmasın.
     */
    fun roughSharpness(bitmap: Bitmap): Double {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            if (width < MIN_DIMENSION || height < MIN_DIMENSION) return Double.MAX_VALUE

            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            laplacianVariance(pixels, width, height)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Kaba netlik hesaplanamadı", e)
            Double.MAX_VALUE
        }
    }

    companion object {

        /** 3x3 Laplacian çekirdeği için gereken en küçük kenar uzunluğu. */
        const val MIN_DIMENSION = 3

        private const val TAG = "BlurDetector"

        /**
         * ARGB piksel dizisi üzerinde Laplacian varyansını hesaplar.
         *
         * Saf hesaplama — Android bağımlılığı yoktur, birim testlerinde doğrudan kullanılır.
         *
         * @param pixels Satır sıralı ARGB piksel dizisi (boyut = width * height)
         * @param width Görüntü genişliği
         * @param height Görüntü yüksekliği
         * @return Laplacian yanıtlarının varyansı; kenar bilgisi arttıkça büyür
         */
        @JvmStatic
        fun laplacianVariance(pixels: IntArray, width: Int, height: Int): Double {
            if (width < MIN_DIMENSION || height < MIN_DIMENSION) return 0.0
            require(pixels.size >= width * height) {
                "piksel dizisi $width x $height için çok küçük"
            }

            val count = (width - 2) * (height - 2)
            if (count <= 0) return 0.0

            // Gri tonlamaya çevir (ITU-R BT.601 ağırlıkları)
            val gray = IntArray(width * height)
            for (i in 0 until width * height) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                gray[i] = (r * 299 + g * 587 + b * 114) / 1000
            }

            var sum = 0.0
            var sumSq = 0.0
            for (y in 1 until height - 1) {
                val row = y * width
                for (x in 1 until width - 1) {
                    val lap = (gray[row - width + x]
                            + gray[row + width + x]
                            + gray[row + x - 1]
                            + gray[row + x + 1]
                            - 4 * gray[row + x]).toDouble()
                    sum += lap
                    sumSq += lap * lap
                }
            }

            val mean = sum / count
            return ((sumSq / count) - (mean * mean)).coerceAtLeast(0.0)
        }
    }
}
