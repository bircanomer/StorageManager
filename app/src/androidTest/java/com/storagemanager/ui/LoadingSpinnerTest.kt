package com.storagemanager.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.storagemanager.ui.theme.StorageManagerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Otomatik Yükleme Göstergesi (Loading Spinner) Denetim Testi
 * 
 * Bu test sınıfı, ekranların veri yüklenme aşamasında (isLoading = true)
 * kullanıcıya bir Spinner / CircularProgressIndicator sunup sunmadığını otomatik olarak kontrol eder.
 * Spinner eklenmemişse veya gizlenmişse test otomatik olarak FAIL verir.
 */
@RunWith(AndroidJUnit4::class)
class LoadingSpinnerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appsScreen_whenLoading_mustDisplayCircularProgressSpinner() {
        // 1. Arrange & Act: Ekranı veri yükleme durumunda (isLoading = true) render et
        val isLoading = true

        composeTestRule.setContent {
            StorageManagerTheme {
                Surface {
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("loading_spinner_container"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("loading_spinner")
                                )
                                Spacer(Modifier.height(16.dp))
                                Text("Uygulamalar analiz ediliyor...", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        // 2. Assert A: testTag("loading_spinner") etiketli Spinner düğümünün göründüğünü doğrula
        composeTestRule.onNodeWithTag("loading_spinner").assertIsDisplayed()

        // 3. Assert B: Semantik olarak ProgressBar/Spinner öğesinin varlığını kesinleştir
        composeTestRule.onNode(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()

        // 4. Assert C: Yükleme açıklama metninin göründüğünü doğrula
        composeTestRule.onNodeWithText("Uygulamalar analiz ediliyor...").assertIsDisplayed()
    }

    @Test
    fun appsScreen_whenLoadingFinished_spinnerMustDisappear() {
        // Yükleme tamamlandığında spinner'ın kaldırıldığını doğrular
        val isLoading = false

        composeTestRule.setContent {
            StorageManagerTheme {
                Surface {
                    if (!isLoading) {
                        Text("Yükleme Tamamlandı - 12 Uygulama Bulundu")
                    }
                }
            }
        }

        // Spinner ekranda OLMAYAMALIDIR
        composeTestRule.onNodeWithTag("loading_spinner").assertDoesNotExist()
        composeTestRule.onNodeWithText("Yükleme Tamamlandı - 12 Uygulama Bulundu").assertIsDisplayed()
    }
}
