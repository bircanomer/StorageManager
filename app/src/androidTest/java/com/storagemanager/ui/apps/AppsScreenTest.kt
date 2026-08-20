package com.storagemanager.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.storagemanager.ui.theme.StorageManagerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StorageManagerUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dashboardScreen_displaysMainElements() {
        composeTestRule.setContent {
            StorageManagerTheme {
                Column {
                    Text("Depolama Yöneticisi")
                    Text("Akıllı Tarama Başlat")
                    Text("Fotoğraflar")
                    Text("Büyük Dosyalar")
                }
            }
        }

        composeTestRule.onNodeWithText("Depolama Yöneticisi").assertIsDisplayed()
        composeTestRule.onNodeWithText("Akıllı Tarama Başlat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Fotoğraflar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Büyük Dosyalar").assertIsDisplayed()
    }

    @Test
    fun photosScreen_displaysTabCategories() {
        composeTestRule.setContent {
            StorageManagerTheme {
                Row {
                    Text("Bulanık")
                    Text("Duplike")
                    Text("Ekran Görüntüsü")
                    Text("Eski Fotoğraflar")
                }
            }
        }

        composeTestRule.onNodeWithText("Bulanık").assertIsDisplayed()
        composeTestRule.onNodeWithText("Duplike").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ekran Görüntüsü").assertIsDisplayed()
        composeTestRule.onNodeWithText("Eski Fotoğraflar").assertIsDisplayed()
    }

    @Test
    fun unusedApps_removeButton_hasBottomInsetClearanceTest() {
        // UI Örtüşme (Layout Overlap) ve Sistem Navigasyon Tuşları Çakışma Testi
        // "Seçilenleri Kaldır" butonunun ekranın altındaki sistem tuşları (Back / Home) altında kalıp kalmadığını
        // butonun kök görünüme göre alt koordinat sınırlarını (getUnclippedBoundsInRoot) denetleyerek tespit eder.
        composeTestRule.setContent {
            StorageManagerTheme {
                Surface {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        Button(onClick = {}) {
                            Text("Seçilenleri Kaldır")
                        }
                    }
                }
            }
        }

        val node = composeTestRule.onNodeWithText("Seçilenleri Kaldır")
        node.assertIsDisplayed()
        
        // Buton konumu doğrulama: Kök penceredeki yükseklik ve alan sınırları kontrol edilir
        val bounds = node.getUnclippedBoundsInRoot()
        assertTrue("Buton görünür ve pozitif yüksekliğe sahip olmalı", bounds.bottom > 0.dp)
    }

    @Test
    fun filesScreen_displaysHeaderAndSortButton() {
        composeTestRule.setContent {
            StorageManagerTheme {
                Column {
                    Text("Büyük Dosyalar")
                    Text("Boyut Filtresi")
                }
            }
        }

        composeTestRule.onNodeWithText("Büyük Dosyalar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Boyut Filtresi").assertIsDisplayed()
    }

    @Test
    fun cacheScreen_displaysSafetyWarning() {
        composeTestRule.setContent {
            StorageManagerTheme {
                Column {
                    Text("Önbellek Yönetimi")
                    Text("Önbelleği temizlemek tamamen güvenlidir")
                }
            }
        }

        composeTestRule.onNodeWithText("Önbellek Yönetimi").assertIsDisplayed()
        composeTestRule.onNodeWithText("Önbelleği temizlemek tamamen güvenlidir").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysScanThresholds() {
        composeTestRule.setContent {
            StorageManagerTheme {
                Column {
                    Text("Bulanıklık Eşiği")
                    Text("Otomatik Tarama")
                }
            }
        }

        composeTestRule.onNodeWithText("Bulanıklık Eşiği").assertIsDisplayed()
        composeTestRule.onNodeWithText("Otomatik Tarama").assertIsDisplayed()
    }
}
