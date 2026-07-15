package com.storagemanager.ml

import android.graphics.Bitmap
import android.graphics.Color
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bulanıklık tespit modülü.
 *
 * Laplacian varyans yöntemiyle bir bitmap'in bulanık olup olmadığını belirler.
 * Düşük varyans değeri = daha bulanık görüntü anlamına gelir.
 */
@Singleton
class BlurDetector @Inject constructor() {

    /**
     * Laplacian çekirdeği — kenar tespiti için kullanılır.
     * 3×3 boyutunda standart Laplacian kernel.
     */
    private val laplacianKernel = arrayOf(
        intArrayOf(0, 1, 0),
        intArrayOf(1, -4, 1),
        intArrayOf(0, 1, 0)
    )

    /**
     * Verilen bitmap'in bulanık olup olmadığını analiz eder.
     *
     * @param bitmap Analiz edilecek görüntü
     * @param threshold Bulanıklık eşik değeri. Bu değerin altındaki skorlar bulanık kabul edilir.
     * @return Pair<Boolean, Float> — (bulanık mı?, bulanıklık skoru)
     */
    fun isBlurry(bitmap: Bitmap, threshold: Double = 100.0): Pair<Boolean, Float> {
        return try {
            val grayscale = toGrayscale(bitmap)
            val laplacianVariance = computeLaplacianVariance(grayscale)
            val isBlurry = laplacianVariance < threshold
            Pair(isBlurry, laplacianVariance.toFloat())
        } catch (e: Exception) {
            // Hata durumunda bulanık olarak işaretle
            Pair(true, 0f)
        }
    }

    /**
     * Bitmap'i gri tonlama piksel dizisine dönüştürür.
     *
     * @param bitmap Kaynak bitmap
     * @return 2D gri tonlama piksel dizisi (0-255)
     */
    private fun toGrayscale(bitmap: Bitmap): Array<IntArray> {
        val width = bitmap.width
        val height = bitmap.height
        val grayscale = Array(height) { IntArray(width) }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                // Luma formülü ile gri tonlama
                grayscale[y][x] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            }
        }
        return grayscale
    }

    /**
     * Gri tonlama görüntü üzerinde Laplacian konvolüsyonu uygulayarak
     * varyansı hesaplar.
     *
     * @param grayscale Gri tonlama piksel dizisi
     * @return Laplacian varyans değeri (düşük = bulanık)
     */
    private fun computeLaplacianVariance(grayscale: Array<IntArray>): Double {
        val height = grayscale.size
        val width = grayscale[0].size

        if (width < 3 || height < 3) return 0.0

        val laplacianValues = mutableListOf<Double>()

        // Laplacian konvolüsyonu — kenarlardaki 1 piksellik sınır hariç
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var sum = 0
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        sum += grayscale[y + ky][x + kx] * laplacianKernel[ky + 1][kx + 1]
                    }
                }
                laplacianValues.add(sum.toDouble())
            }
        }

        if (laplacianValues.isEmpty()) return 0.0

        // Varyans hesaplama
        val mean = laplacianValues.average()
        val variance = laplacianValues.sumOf { (it - mean) * (it - mean) } / laplacianValues.size
        return variance
    }
}
