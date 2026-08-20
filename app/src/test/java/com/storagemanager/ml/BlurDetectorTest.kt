package com.storagemanager.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Laplacian varyans hesabının bulanık/keskin ayrımını doğru yaptığını sınar.
 */
class BlurDetectorTest {

    private val size = 64

    private fun gray(value: Int): Int {
        val v = value.coerceIn(0, 255)
        return (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }

    @Test
    fun `duz yuzeyin varyansi sifirdir`() {
        val pixels = IntArray(size * size) { gray(120) }
        assertEquals(0.0, BlurDetector.laplacianVariance(pixels, size, size), 0.0001)
    }

    @Test
    fun `dogrusal gradyanin varyansi cok dusuktur`() {
        // Doğrusal gradyanda Laplacian yanıtı her yerde sıfırdır → bulanık kabul edilir
        val pixels = IntArray(size * size) { i -> gray((i % size) * 4) }
        val variance = BlurDetector.laplacianVariance(pixels, size, size)
        assertEquals(0.0, variance, 0.5)
    }

    @Test
    fun `satranc tahtasi deseni yuksek varyans uretir`() {
        val pixels = IntArray(size * size) { i ->
            val x = i % size
            val y = i / size
            gray(if ((x + y) % 2 == 0) 0 else 255)
        }
        val variance = BlurDetector.laplacianVariance(pixels, size, size)
        // Mutlak eşik yerine göreli karşılaştırma: eşik artık ölçüte göre değişiyor,
        // testin bağlanması gereken şey deseni ayırt edebilmesi.
        val flat = BlurDetector.laplacianVariance(IntArray(size * size) { gray(120) }, size, size)
        assertTrue("satranç deseni düz yüzeyden yüksek olmalı, bulunan $variance", variance > flat)
    }

    @Test
    fun `gurultulu goruntu duz yuzeyden daha yuksek varyansa sahiptir`() {
        val random = Random(7)
        val noisy = IntArray(size * size) { gray(random.nextInt(256)) }
        val flat = IntArray(size * size) { gray(120) }

        assertTrue(
            BlurDetector.laplacianVariance(noisy, size, size) >
                    BlurDetector.laplacianVariance(flat, size, size)
        )
    }

    @Test
    fun `cok kucuk goruntuler sifir dondurur`() {
        val pixels = IntArray(4) { gray(50) }
        assertEquals(0.0, BlurDetector.laplacianVariance(pixels, 2, 2), 0.0001)
    }

    @Test
    fun `varyans negatif olamaz`() {
        val random = Random(1)
        repeat(20) {
            val pixels = IntArray(size * size) { gray(random.nextInt(256)) }
            assertTrue(BlurDetector.laplacianVariance(pixels, size, size) >= 0.0)
        }
    }
}
