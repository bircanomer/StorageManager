package com.storagemanager.ui.components

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.storagemanager.R
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Videolar arası gezinme sonrası oynatmanın hâlâ çalıştığını doğrular.
 *
 * Hata senaryosu (kullanıcı bildirimi): bir videoyu oynattıktan sonra ok tuşuyla
 * başka bir videoya geçilince "Oynat" butonu artık videoyu başlatmıyordu.
 *
 * Tıklamalar bilerek UI Automator ile (gerçek dokunma olayı) yapılıyor; Compose'un
 * semantik tıklaması, ekranda kalan yabancı bir pencerenin dokunuşu yutmasını
 * gizleyeceği için hatayı yakalayamazdı. Aynı sebeple Compose test kuralı (ve onun
 * duraklatılmış kare saati) kullanılmıyor; diyalog gerçek bir Activity içinde,
 * üretimdeki gibi normal Compose çalışma zamanıyla gösteriliyor.
 */
@RunWith(AndroidJUnit4::class)
class MediaPreviewVideoPlaybackTest {

    private lateinit var device: UiDevice
    private lateinit var clips: List<File>
    private var scenario: ActivityScenario<ComponentActivity>? = null

    // Cihazın dili İngilizce olmayabilir; etiketleri uygulamanın kendi kaynaklarından okuyoruz.
    private lateinit var playDesc: String
    private lateinit var nextDesc: String
    private lateinit var previousDesc: String

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        device.wakeUp()

        val targetContext = instrumentation.targetContext
        playDesc = targetContext.getString(R.string.preview_play)
        nextDesc = targetContext.getString(R.string.preview_next)
        previousDesc = targetContext.getString(R.string.preview_previous)

        clips = listOf("clip_a.mp4", "clip_b.mp4").map { name ->
            File(targetContext.cacheDir, name).also { out ->
                instrumentation.context.assets.open(name).use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
            }
        }
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun playWorksOnSecondVideoAfterPlayingFirstOne() {
        showDialog()

        // 1. Adım: ilk videoyu oynat
        tapPlay("ilk video")
        assertPlayerVisible("ilk video")

        // 2. Adım: oynatma sürerken sonraki videoya geç
        val next = device.wait(Until.findObject(By.desc(nextDesc)), TIMEOUT)
        assertNotNull("'$nextDesc' oku bulunamadı", next)
        next.click()

        // Yeni video için önizleme (Oynat butonu) geri gelmeli
        assertTrue(
            "Sonraki videoya geçince 'Oynat' butonu geri gelmedi",
            device.wait(Until.hasObject(By.desc(playDesc)), TIMEOUT)
        )

        // 3. Adım: ikinci videoyu oynat — hata burada ortaya çıkıyordu
        tapPlay("ikinci video")
        assertPlayerVisible("ikinci video")
    }

    /**
     * Kullanıcının gerçek akışı: video oynarken ekrana dokunulur (oynatma kontrolleri
     * belirir), sonra sonraki videoya geçilir.
     */
    @Test
    fun playWorksOnSecondVideoAfterMediaControlsWereShown() {
        showDialog()

        tapPlay("ilk video")
        assertPlayerVisible("ilk video")

        // Oynatma kontrollerini görünür kıl (VideoView'a dokunma)
        device.click(device.displayWidth / 2, device.displayHeight / 2)
        device.waitForIdle()

        val next = device.wait(Until.findObject(By.desc(nextDesc)), TIMEOUT)
        assertNotNull("'$nextDesc' oku bulunamadı", next)
        next.click()

        assertTrue(
            "Sonraki videoya geçince 'Oynat' butonu geri gelmedi",
            device.wait(Until.hasObject(By.desc(playDesc)), TIMEOUT)
        )

        tapPlay("ikinci video")
        assertPlayerVisible("ikinci video")
    }

    @Test
    fun playWorksAfterNavigatingBackToAPreviouslyPlayedVideo() {
        showDialog()

        tapPlay("ilk video")
        assertPlayerVisible("ilk video")

        device.wait(Until.findObject(By.desc(nextDesc)), TIMEOUT)?.click()
        device.wait(Until.findObject(By.desc(previousDesc)), TIMEOUT)?.click()

        assertTrue(
            "Geri dönünce 'Oynat' butonu geri gelmedi",
            device.wait(Until.hasObject(By.desc(playDesc)), TIMEOUT)
        )

        tapPlay("geri dönülen ilk video")
        assertPlayerVisible("geri dönülen ilk video")
    }

    private fun showDialog() {
        scenario = ActivityScenario.launch(ComponentActivity::class.java).also { launched ->
            launched.onActivity { activity ->
                activity.setContent {
                    var index by remember { mutableIntStateOf(0) }
                    MediaPreviewDialog(
                        item = MediaItemPreview(
                            uriOrPath = clips[index].absolutePath,
                            title = clips[index].name,
                            subtitle = "test",
                            isVideo = true
                        ),
                        onDismiss = {},
                        onPrevious = if (index > 0) ({ index-- }) else null,
                        onNext = if (index < clips.lastIndex) ({ index++ }) else null
                    )
                }
            }
        }
        assertTrue(
            "Önizleme diyaloğu açılmadı",
            device.wait(Until.hasObject(By.desc(playDesc)), TIMEOUT)
        )
    }

    /** Gerçek dokunma olayı ile Oynat butonuna basar. */
    private fun tapPlay(step: String) {
        val play: UiObject2? = device.wait(Until.findObject(By.desc(playDesc)), TIMEOUT)
        assertNotNull("[$step] 'Oynat' butonu ekranda bulunamadı", play)
        play!!.click()
    }

    /**
     * Oynatıcının gerçekten devreye girdiğini doğrular. Yalnızca VideoView'in ağaçta
     * olmasına bakmak yetmiyor: hata durumunda VideoView oluşuyor ama hiç yerleşim
     * almadığı için yüzey yaratılmıyor ve tek kare bile oynamıyordu. Bu yüzden ekranın
     * gerçekten değiştiği (kare akışı) ölçülüyor.
     */
    private fun assertPlayerVisible(step: String) {
        assertTrue(
            "[$step] 'Oynat' butonuna basıldı ama önizleme ekranı kaybolmadı — video başlamıyor",
            device.wait(Until.gone(By.desc(playDesc)), TIMEOUT)
        )
        assertTrue(
            "[$step] Oynatıcı (VideoView) ekranda oluşmadı",
            device.wait(Until.hasObject(By.clazz("android.widget.VideoView")), TIMEOUT)
        )
        assertTrue(
            "[$step] Oynatıcı ekranda var ama kareler ilerlemiyor — video oynamıyor",
            waitForMovingFrames()
        )
    }

    /** Ardışık ekran görüntülerini karşılaştırarak oynatmanın ilerlediğini ölçer. */
    private fun waitForMovingFrames(): Boolean {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        var previous: Bitmap? = automation.takeScreenshot()
        repeat(6) {
            SystemClock.sleep(700)
            val current = automation.takeScreenshot() ?: return@repeat
            val before = previous
            if (before != null && framesDiffer(before, current)) {
                before.recycle(); current.recycle()
                return true
            }
            before?.recycle()
            previous = current
        }
        previous?.recycle()
        return false
    }

    /** Ekranın orta bölgesinden seyrek örnekleme ile iki karenin farklı olup olmadığına bakar. */
    private fun framesDiffer(a: Bitmap, b: Bitmap): Boolean {
        if (a.width != b.width || a.height != b.height) return true
        var diff = 0
        val stepX = (a.width / 24).coerceAtLeast(1)
        val stepY = (a.height / 48).coerceAtLeast(1)
        var y = a.height / 4
        while (y < a.height * 3 / 4) {
            var x = 0
            while (x < a.width) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) {
                    diff++
                    if (diff > 20) return true
                }
                x += stepX
            }
            y += stepY
        }
        return false
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
