package com.storagemanager.ml

import android.graphics.Bitmap
import android.graphics.Color
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Perceptual hash (pHash) tabanlı duplike fotoğraf tespit modülü.
 *
 * Görsellerin algısal parmak izini çıkararak benzer fotoğrafları tespit eder.
 * Hamming mesafesi düşük olan çiftler duplike kabul edilir.
 */
@Singleton
class DuplicateDetector @Inject constructor() {

    companion object {
        /** pHash için ölçekleme boyutu */
        private const val HASH_SIZE = 8
        /** DCT hesaplama için ölçekleme boyutu */
        private const val DCT_SIZE = 32
    }

    /**
     * Verilen bitmap için 64-bit perceptual hash hesaplar.
     *
     * Algoritma adımları:
     * 1. 32×32'ye yeniden boyutlandır
     * 2. Gri tonlamaya dönüştür
     * 3. DCT (Ayrık Kosinüs Dönüşümü) uygula
     * 4. Sol üst 8×8 bloğu al
     * 5. Medyan değeri hesapla
     * 6. Her pikseli medyanla karşılaştırarak 64-bit hash oluştur
     *
     * @param bitmap Hash'i hesaplanacak görüntü
     * @return 64-bit perceptual hash değeri
     */
    fun computeHash(bitmap: Bitmap): Long {
        return try {
            // Adım 1: 32×32'ye yeniden boyutlandır
            val resized = Bitmap.createScaledBitmap(bitmap, DCT_SIZE, DCT_SIZE, true)

            // Adım 2: Gri tonlamaya dönüştür
            val grayscale = Array(DCT_SIZE) { y ->
                DoubleArray(DCT_SIZE) { x ->
                    val pixel = resized.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    0.299 * r + 0.587 * g + 0.114 * b
                }
            }

            // Yeniden boyutlandırılmış bitmap'i serbest bırak (orijinalden farklıysa)
            if (resized != bitmap) {
                resized.recycle()
            }

            // Adım 3: 2D DCT uygula
            val dctResult = applyDCT(grayscale)

            // Adım 4: Sol üst 8×8 bloğu al (DC bileşeni hariç)
            val dctLowFreq = mutableListOf<Double>()
            for (y in 0 until HASH_SIZE) {
                for (x in 0 until HASH_SIZE) {
                    // DC bileşenini atla (0,0)
                    if (x == 0 && y == 0) continue
                    dctLowFreq.add(dctResult[y][x])
                }
            }

            // Adım 5: Medyan hesapla
            val sorted = dctLowFreq.sorted()
            val median = if (sorted.size % 2 == 0) {
                (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
            } else {
                sorted[sorted.size / 2]
            }

            // Adım 6: Hash oluştur
            var hash = 0L
            var bitIndex = 0
            for (y in 0 until HASH_SIZE) {
                for (x in 0 until HASH_SIZE) {
                    if (dctResult[y][x] > median) {
                        hash = hash or (1L shl bitIndex)
                    }
                    bitIndex++
                }
            }

            hash
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * İki hash değeri arasındaki Hamming mesafesini hesaplar.
     *
     * Hamming mesafesi, iki bit dizisinde farklı olan bitlerin sayısıdır.
     *
     * @param hash1 Birinci hash değeri
     * @param hash2 İkinci hash değeri
     * @return Farklı bit sayısı (0 = tamamen aynı)
     */
    fun hammingDistance(hash1: Long, hash2: Long): Int {
        val xor = hash1 xor hash2
        return java.lang.Long.bitCount(xor)
    }

    /**
     * İki hash değerinin duplike olup olmadığını kontrol eder.
     *
     * @param hash1 Birinci hash değeri
     * @param hash2 İkinci hash değeri
     * @param threshold Hamming mesafesi eşik değeri (varsayılan: 10)
     * @return true ise fotoğraflar duplike kabul edilir
     */
    fun areDuplicates(hash1: Long, hash2: Long, threshold: Int = 10): Boolean {
        return hammingDistance(hash1, hash2) <= threshold
    }

    /**
     * 2D Ayrık Kosinüs Dönüşümü (DCT-II) uygular.
     *
     * @param input Giriş matrisi (NxN)
     * @return DCT katsayıları matrisi
     */
    private fun applyDCT(input: Array<DoubleArray>): Array<DoubleArray> {
        val n = input.size
        val result = Array(n) { DoubleArray(n) }

        for (u in 0 until n) {
            for (v in 0 until n) {
                var sum = 0.0
                for (x in 0 until n) {
                    for (y in 0 until n) {
                        sum += input[x][y] *
                                cos((2.0 * x + 1) * u * Math.PI / (2.0 * n)) *
                                cos((2.0 * y + 1) * v * Math.PI / (2.0 * n))
                    }
                }
                val alphaU = if (u == 0) 1.0 / sqrt(n.toDouble()) else sqrt(2.0 / n)
                val alphaV = if (v == 0) 1.0 / sqrt(n.toDouble()) else sqrt(2.0 / n)
                result[u][v] = alphaU * alphaV * sum
            }
        }
        return result
    }
}
