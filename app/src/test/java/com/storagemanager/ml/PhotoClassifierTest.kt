package com.storagemanager.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PhotoClassifier.categorize] saf eşleme mantığının testleri.
 *
 * ML Kit modeli çalıştırılmaz; yalnızca etiket → [PhotoContent] indirgemesi doğrulanır.
 */
class PhotoClassifierTest {

    @Test
    fun `bos etiket listesi UNKNOWN doner`() {
        assertEquals(PhotoContent.UNKNOWN, PhotoClassifier.categorize(emptyList()))
    }

    @Test
    fun `eslesmeyen etiketler UNKNOWN doner`() {
        val labels = listOf("Sky" to 0.95f, "Mountain" to 0.88f)
        assertEquals(PhotoContent.UNKNOWN, PhotoClassifier.categorize(labels))
    }

    @Test
    fun `etiket eslemesi buyuk kucuk harften bagimsizdir`() {
        assertEquals(PhotoContent.PET, PhotoClassifier.categorize(listOf("DOG" to 0.7f)))
        assertEquals(PhotoContent.PEOPLE, PhotoClassifier.categorize(listOf("person" to 0.7f)))
    }

    @Test
    fun `en yuksek guvene sahip etiket kazanir`() {
        val labels = listOf("Dog" to 0.62f, "Person" to 0.91f)
        assertEquals(PhotoContent.PEOPLE, PhotoClassifier.categorize(labels))
    }

    @Test
    fun `sonuc etiket sirasindan bagimsizdir`() {
        val forward = listOf("Dog" to 0.75f, "Person" to 0.75f)
        val reversed = forward.reversed()
        assertEquals(PhotoClassifier.categorize(forward), PhotoClassifier.categorize(reversed))
    }

    @Test
    fun `kisi ve evcil hayvan korumali digerleri degil`() {
        assertTrue(PhotoClassifier.isProtected(PhotoContent.PEOPLE))
        assertTrue(PhotoClassifier.isProtected(PhotoContent.PET))
        assertFalse(PhotoClassifier.isProtected(PhotoContent.UNKNOWN))
    }
}
