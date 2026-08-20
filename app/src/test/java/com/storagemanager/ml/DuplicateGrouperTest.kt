package com.storagemanager.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Bantlı gruplama algoritmasının, tüm çiftleri karşılaştıran naif yöntemle
 * aynı sonucu ürettiğini doğrular.
 */
class DuplicateGrouperTest {

    private val threshold = DuplicateDetector.DEFAULT_THRESHOLD

    /** Referans uygulama: her çifti karşılaştırıp birleşim bileşenlerini bulur. */
    private fun naiveGroups(hashes: LongArray, threshold: Int): Set<Set<Int>> {
        val parent = IntArray(hashes.size) { it }
        fun find(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            return r
        }
        for (i in hashes.indices) {
            for (j in i + 1 until hashes.size) {
                if (java.lang.Long.bitCount(hashes[i] xor hashes[j]) <= threshold) {
                    val a = find(i)
                    val b = find(j)
                    if (a != b) parent[a] = b
                }
            }
        }
        return hashes.indices
            .groupBy { find(it) }
            .values
            .filter { it.size >= 2 }
            .map { it.toSet() }
            .toSet()
    }

    private fun asSets(groups: List<List<Int>>): Set<Set<Int>> = groups.map { it.toSet() }.toSet()

    @Test
    fun `bos ve tek elemanli girdi grup uretmez`() {
        assertTrue(DuplicateGrouper.group(longArrayOf()).isEmpty())
        assertTrue(DuplicateGrouper.group(longArrayOf(42L)).isEmpty())
    }

    @Test
    fun `birebir ayni hashler tek grupta toplanir`() {
        val hashes = longArrayOf(7L, 7L, 7L, 999L)
        val groups = DuplicateGrouper.group(hashes, threshold)
        assertEquals(1, groups.size)
        assertEquals(setOf(0, 1, 2), groups.first().toSet())
    }

    @Test
    fun `esik disindaki hashler gruplanmaz`() {
        // 10 bit fark → eşiğin (4) çok üzerinde
        val a = 0L
        val b = (0 until 10).fold(0L) { acc, i -> acc or (1L shl i) }
        assertTrue(DuplicateGrouper.group(longArrayOf(a, b), threshold).isEmpty())
    }

    @Test
    fun `esik icindeki hashler gruplanir`() {
        val a = 0b1010_1010L
        val b = a xor 0b11L // 2 bit fark
        val groups = DuplicateGrouper.group(longArrayOf(a, b), threshold)
        assertEquals(1, groups.size)
        assertEquals(setOf(0, 1), groups.first().toSet())
    }

    @Test
    fun `gecisli benzerlik tek grupta birlestirilir`() {
        // a~b ve b~c ama a ile c arasındaki mesafe eşiğin üstünde olabilir;
        // birleşim-bulma bunları yine de tek grupta toplamalı.
        val a = 0L
        val b = 0b1111L                       // a'ya 4 bit
        val c = 0b1111_1111L                  // b'ye 4 bit
        val groups = DuplicateGrouper.group(longArrayOf(a, b, c), threshold)
        assertEquals(1, groups.size)
        assertEquals(setOf(0, 1, 2), groups.first().toSet())
    }

    @Test
    fun `naif yontemle ayni sonucu uretir - kume kume`() {
        val random = Random(123)
        repeat(30) {
            // Birkaç "temel" hash etrafında hafif mutasyonlarla gerçekçi bir küme üret
            val bases = List(6) { random.nextLong() }
            val hashes = ArrayList<Long>()
            for (base in bases) {
                hashes += base
                repeat(3) {
                    var mutated = base
                    repeat(random.nextInt(0, threshold + 3)) {
                        mutated = mutated xor (1L shl random.nextInt(64))
                    }
                    hashes += mutated
                }
            }
            val array = hashes.toLongArray()
            assertEquals(
                naiveGroups(array, threshold),
                asSets(DuplicateGrouper.group(array, threshold))
            )
        }
    }

    @Test
    fun `naif yontemle ayni sonucu uretir - tamamen rastgele`() {
        val random = Random(99)
        repeat(20) {
            val array = LongArray(60) { random.nextLong() }
            assertEquals(
                naiveGroups(array, threshold),
                asSets(DuplicateGrouper.group(array, threshold))
            )
        }
    }

    @Test
    fun `tum ogeler ayniysa tek grup dondurur`() {
        val array = LongArray(1000) { 0x0F0F_0F0F_0F0F_0F0FL }
        val groups = DuplicateGrouper.group(array, threshold)
        assertEquals(1, groups.size)
        assertEquals(1000, groups.first().size)
    }
}
