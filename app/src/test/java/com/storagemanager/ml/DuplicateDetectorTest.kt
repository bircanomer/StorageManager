package com.storagemanager.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * dHash hesaplaması ve bant anahtarlarının doğruluğunu sınar.
 */
class DuplicateDetectorTest {

    private val width = DuplicateDetector.HASH_WIDTH
    private val height = DuplicateDetector.HASH_HEIGHT

    private fun gray(value: Int): Int = (0xFF shl 24) or (value shl 16) or (value shl 8) or value

    /** Soldan sağa açılan yatay gradyan. */
    private fun gradientPixels(): IntArray = IntArray(width * height) { i ->
        gray((i % width) * 25)
    }

    @Test
    fun `duz renkli goruntude tum bitler sifir`() {
        val pixels = IntArray(width * height) { gray(128) }
        // Hiçbir komşu diğerinden büyük değil → hiçbir bit set edilmez
        assertEquals(0L, DuplicateDetector.computeHash(pixels))
    }

    @Test
    fun `artan gradyanda soldaki piksel hicbir zaman buyuk degil`() {
        assertEquals(0L, DuplicateDetector.computeHash(gradientPixels()))
    }

    @Test
    fun `azalan gradyanda tum karsilastirma bitleri set edilir`() {
        val pixels = IntArray(width * height) { i -> gray(200 - (i % width) * 20) }
        val hash = DuplicateDetector.computeHash(pixels)

        // Satır başına (HASH_WIDTH - 1) bit, toplam 64 bit üretilmeli
        assertEquals(64, java.lang.Long.bitCount(hash))
    }

    @Test
    fun `ayni goruntu ayni hash uretir`() {
        val pixels = gradientPixels()
        assertEquals(
            DuplicateDetector.computeHash(pixels),
            DuplicateDetector.computeHash(pixels.copyOf())
        )
    }

    @Test
    fun `farkli goruntuler farkli hash uretir`() {
        val a = IntArray(width * height) { i -> gray((i * 7) % 256) }
        val b = IntArray(width * height) { i -> gray((i * 31) % 256) }
        assertNotEquals(DuplicateDetector.computeHash(a), DuplicateDetector.computeHash(b))
    }

    @Test
    fun `hamming mesafesi farkli bit sayisini verir`() {
        val detector = DuplicateDetector()
        assertEquals(0, detector.hammingDistance(0b1011L, 0b1011L))
        // 1011 xor 0100 = 1111 → 4 bit fark
        assertEquals(4, detector.hammingDistance(0b1011L, 0b0100L))
        assertTrue(detector.areDuplicates(0b1011L, 0b1010L, threshold = 1))
        assertTrue(!detector.areDuplicates(0b1011L, 0b0100L, threshold = 2))
    }

    @Test
    fun `bant sayisi esik degerinden bir fazladir`() {
        assertEquals(DuplicateDetector.DEFAULT_THRESHOLD + 1, DuplicateDetector.BAND_COUNT)
        assertEquals(DuplicateDetector.BAND_COUNT, DuplicateDetector.bandKeys(123L).size)
    }

    @Test
    fun `esik icindeki her cift en az bir bant paylasir`() {
        // Güvercin yuvası ilkesinin garantisi: mesafe <= eşik ise bantlardan biri aynı olmalı.
        val random = Random(42)
        repeat(500) {
            val base = random.nextLong()
            var mutated = base
            repeat(DuplicateDetector.DEFAULT_THRESHOLD) {
                mutated = mutated xor (1L shl random.nextInt(64))
            }

            val shared = DuplicateDetector.bandKeys(base)
                .intersect(DuplicateDetector.bandKeys(mutated).toSet())
            assertTrue(
                "mesafe=${java.lang.Long.bitCount(base xor mutated)} için ortak bant bulunamadı",
                shared.isNotEmpty()
            )
        }
    }

    @Test
    fun `farkli bantlar cakismayan anahtar uretir`() {
        val keys = DuplicateDetector.bandKeys(-1L) // tüm bitler 1
        assertEquals(keys.size, keys.toSet().size)
    }
}
