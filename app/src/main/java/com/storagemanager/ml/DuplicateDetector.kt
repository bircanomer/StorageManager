package com.storagemanager.ml

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Perceptual hash (dHash - Difference Hash) tabanlı duplike fotoğraf tespit modülü.
 *
 * Görsellerin algısal parmak izini çıkararak benzer fotoğrafları çok hızlı tespit eder.
 * Hamming mesafesi düşük olan çiftler duplike kabul edilir.
 */
@Singleton
class DuplicateDetector @Inject constructor() {

    /**
     * Verilen bitmap için 64-bit Difference Hash (dHash) hesaplar.
     *
     * Bitmap zaten [HASH_WIDTH] x [HASH_HEIGHT] boyutunda değilse ölçeklenir.
     */
    fun computeHash(bitmap: Bitmap): Long {
        return try {
            val resized = if (bitmap.width == HASH_WIDTH && bitmap.height == HASH_HEIGHT) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, HASH_WIDTH, HASH_HEIGHT, true)
            }

            val pixels = IntArray(HASH_WIDTH * HASH_HEIGHT)
            resized.getPixels(pixels, 0, HASH_WIDTH, 0, 0, HASH_WIDTH, HASH_HEIGHT)

            if (resized !== bitmap) {
                resized.recycle()
            }

            computeHash(pixels)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Hash hesaplanamadı", e)
            0L
        }
    }

    fun hammingDistance(hash1: Long, hash2: Long): Int = java.lang.Long.bitCount(hash1 xor hash2)

    fun areDuplicates(hash1: Long, hash2: Long, threshold: Int = DEFAULT_THRESHOLD): Boolean =
        hammingDistance(hash1, hash2) <= threshold

    companion object {
        /** dHash için satır başına okunan piksel sayısı (bir fazlası komşu farkı içindir). */
        const val HASH_WIDTH = 9
        const val HASH_HEIGHT = 8
        const val DEFAULT_THRESHOLD = 4

        /**
         * Güvercin yuvası ilkesi gereği eşik + 1 bant kullanılır: iki hash arasındaki
         * Hamming mesafesi eşiği aşmıyorsa, bantlardan en az biri birebir aynıdır.
         */
        const val BAND_COUNT = DEFAULT_THRESHOLD + 1

        private const val TAG = "DuplicateDetector"

        /**
         * 9x8 ARGB piksel dizisinden 64-bit dHash üretir.
         *
         * Saf hesaplama — Android bağımlılığı yoktur, birim testlerinde doğrudan kullanılır.
         */
        @JvmStatic
        fun computeHash(pixels: IntArray): Long {
            require(pixels.size >= HASH_WIDTH * HASH_HEIGHT) {
                "dHash için ${HASH_WIDTH * HASH_HEIGHT} piksel gerekir, ${pixels.size} verildi"
            }

            val gray = IntArray(HASH_WIDTH * HASH_HEIGHT)
            for (i in gray.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                gray[i] = (r * 299 + g * 587 + b * 114) / 1000
            }

            var hash = 0L
            var bitIndex = 0
            for (y in 0 until HASH_HEIGHT) {
                val rowOffset = y * HASH_WIDTH
                // Her satırda komşu piksel farkı: HASH_WIDTH - 1 bit üretir.
                for (x in 0 until HASH_WIDTH - 1) {
                    if (gray[rowOffset + x] > gray[rowOffset + x + 1]) {
                        hash = hash or (1L shl bitIndex)
                    }
                    bitIndex++
                }
            }
            return hash
        }

        /**
         * Hash'i [BAND_COUNT] parçaya böler ve her parça için (bant indeksi ile etiketlenmiş)
         * bir arama anahtarı üretir.
         *
         * Aday eşleşmeleri O(N) sürede bulmayı sağlar: iki hash'in mesafesi
         * [DEFAULT_THRESHOLD] veya altındaysa en az bir bant anahtarları çakışır.
         */
        @JvmStatic
        fun bandKeys(hash: Long): LongArray {
            val bits = 64 / BAND_COUNT  // 12 bit; kalan bitler son banda eklenir
            val keys = LongArray(BAND_COUNT)
            for (band in 0 until BAND_COUNT) {
                val shift = band * bits
                val width = if (band == BAND_COUNT - 1) 64 - shift else bits
                val mask = if (width >= 64) -1L else (1L shl width) - 1
                val value = (hash ushr shift) and mask
                // Bant indeksini anahtara gömerek farklı bantların çakışmasını engelle
                keys[band] = (band.toLong() shl 58) or value
            }
            return keys
        }
    }
}
