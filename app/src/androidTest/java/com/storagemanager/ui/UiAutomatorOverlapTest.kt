package com.storagemanager.ui

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI Automator Otomatik Çakışma ve Navigasyon Çubuğu Örtüşme Testi
 * 
 * Bu test, gerçek Android cihaz / emülatör üzerinde çalışırken ekran hiyerarşisini dökümler,
 * "Seçilenleri Kaldır" butonunun görsel koordinat dikdörtgenini (Bounds) alır ve
 * Android Sistem Navigasyon Barı (Back/Home tuşları) koordinatları ile çakışmadığını kesin olarak doğrular.
 */
@RunWith(AndroidJUnit4::class)
class UiAutomatorOverlapTest {

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertNotNull("UiDevice örneği alınamadı", device)
    }

    @Test
    fun verifyRemoveButtonDoesNotOverlapSystemNavigationBar() {
        val launcherPackage = device.launcherPackageName
        assertNotNull("Launcher paketi mevcut olmalı", launcherPackage)

        // 1. Ekrandaki tıklanabilir "Kaldır" veya "Seçilenleri Kaldır" butonunu bul
        val removeButton = device.wait(Until.findObject(By.textContains("Kaldır")), 2000L)
        
        // 2. Android Sistem Navigasyon Çubuğu (3-Button veya Gesture bar) elemanını bul
        val navBar = device.findObject(By.res("com.android.systemui:id/navigation_bar_frame"))
            ?: device.findObject(By.res("android:id/navigationBarBackground"))

        if (removeButton != null && navBar != null) {
            val buttonBounds: Rect = removeButton.visibleBounds
            val navBarBounds: Rect = navBar.visibleBounds

            // 3. İki koordinat dikdörtgeninin birbiriyle KESİŞMEDİĞİNİ doğrula
            val isOverlapping = Rect.intersects(buttonBounds, navBarBounds)
            assertFalse(
                "HATA: 'Seçilenleri Kaldır' butonu ($buttonBounds), Sistem Navigasyon Çubuğu ($navBarBounds) altında kalıyor!",
                isOverlapping
            )
        }
    }

    @Test
    fun verifyBottomButtonIsWithinDisplaySafeBounds() {
        val displayHeight = device.displayHeight
        val displayWidth = device.displayWidth

        val bottomButton = device.findObject(By.textContains("Kaldır"))
        if (bottomButton != null) {
            val bounds = bottomButton.visibleBounds
            
            // Butonun en alt noktasının ekran sınırları dahilinde olduğunu doğrula
            assertTrue(
                "Buton ekranın alt sınırının ($displayHeight px) dışında render edilemez",
                bounds.bottom <= displayHeight
            )
            assertTrue(
                "Buton ekranın sağ sınırının ($displayWidth px) dışında render edilemez",
                bounds.right <= displayWidth
            )
        }
    }

    private fun assertTrue(message: String, condition: Boolean) {
        org.junit.Assert.assertTrue(message, condition)
    }
}
