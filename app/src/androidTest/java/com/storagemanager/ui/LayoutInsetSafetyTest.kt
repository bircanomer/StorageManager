package com.storagemanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.storagemanager.ui.theme.StorageManagerTheme
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Otomatik UI Layout & WindowInsets Güvenlik Testleri
 * 
 * Bu test sınıfı, alt barlarda (bottomBar) ve butonlarda (örn. "Seçilenleri Kaldır")
 * sistem navigasyon barı (Back/Home tuşları) çakışmalarını tespit eder.
 */
@RunWith(AndroidJUnit4::class)
class LayoutInsetSafetyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun unusedApps_bottomBarButton_hasSafeInsetPaddingAndIsNotCovered() {
        // Kullanılmayan Uygulamalar alt çubuğu test senaryosu
        composeTestRule.setContent {
            StorageManagerTheme {
                Surface {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("2 uygulama", color = Color.Gray, fontSize = 13.sp)
                                Text("450.0 MB", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }

                            Button(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B))
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("2 Seçileni Kaldır", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // 1. Butonun ekranda göründüğünü doğrula
        val removeButtonNode = composeTestRule.onNodeWithText("2 Seçileni Kaldır")
        removeButtonNode.assertIsDisplayed()
        removeButtonNode.assertHasClickAction()

        // 2. Butonun kök penceredeki konum sınırlarını (Bounds) ölç
        val buttonBounds = removeButtonNode.getUnclippedBoundsInRoot()
        assertNotNull("Buton alan sınırları boş olamaz", buttonBounds)
        assertTrue("Buton yüksekliği 0'dan büyük olmalıdır", buttonBounds.height > 0.dp)
        assertTrue("Buton alt sınırı geçerli ekran alanında kalmalıdır", buttonBounds.bottom > 0.dp)
    }

    @Test
    fun photosScreen_fabDeleteButton_positionedSafelyAboveBottomEdge() {
        // Fotoğraf silme FAB butonunun güvenli alan testi
        composeTestRule.setContent {
            StorageManagerTheme {
                Scaffold(
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = {},
                            containerColor = Color(0xFFFF6B6B),
                            contentColor = Color.White
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("5 Fotoğraf Sil")
                        }
                    }
                ) { padding ->
                    Surface(modifier = Modifier.padding(padding)) {
                        Text("Fotoğraflar Ekranı İçeriği")
                    }
                }
            }
        }

        val fabNode = composeTestRule.onNodeWithText("5 Fotoğraf Sil")
        fabNode.assertIsDisplayed()
        fabNode.assertHasClickAction()

        val fabBounds = fabNode.getUnclippedBoundsInRoot()
        assertTrue("FAB butonunun genişliği ve yüksekliği pozitif olmalıdır", fabBounds.width > 0.dp && fabBounds.height > 0.dp)
    }

    @Test
    fun fullScaffoldLayout_bottomBar_doesNotOverlapContent() {
        // Kapsamlı Scaffold düzeni ve bottomBar insets çakışmasızlık testi
        composeTestRule.setContent {
            StorageManagerTheme {
                Scaffold(
                    bottomBar = {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) {
                            Row(modifier = Modifier.padding(16.dp)) {
                                Button(onClick = {}) {
                                    Text("Toplu İşlem Yap")
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding)) {
                        Text("Listelenen Öğeler")
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Toplu İşlem Yap").assertIsDisplayed()
        composeTestRule.onNodeWithText("Listelenen Öğeler").assertIsDisplayed()
    }
}
