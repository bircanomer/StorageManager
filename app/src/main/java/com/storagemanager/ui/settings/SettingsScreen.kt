package com.storagemanager.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

// ── Renk Paleti ──────────────────────────────────────────────────────────────
private val DarkBackground = Color(0xFF0D0D1A)
private val DarkSurface = Color(0xFF1A1A2E)
private val DarkSurfaceVariant = Color(0xFF252540)
private val PrimaryColor = Color(0xFF6C63FF)
private val SecondaryColor = Color(0xFF00BCD4)
private val OnSurfaceColor = Color(0xFFC8C8D8)
private val ErrorColor = Color(0xFFFF6B6B)
private val SuccessColor = Color(0xFF4CAF50)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Ayarlar",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = paddingValues.calculateTopPadding() + 12.dp,
                bottom = paddingValues.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ═══════════════════════════════════════════════════════════════
            // TARAMA AYARLARI
            // ═══════════════════════════════════════════════════════════════
            item {
                SectionHeader(
                    icon = Icons.Default.Tune,
                    title = "Tarama Ayarları"
                )
            }

            // ── Bulanıklık Eşiği ─────────────────────────────────────────
            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.BlurOn,
                                contentDescription = null,
                                tint = PrimaryColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Bulanıklık Eşiği",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "Değer: ${state.minPhotoBlurThreshold.toInt()}",
                                    color = SecondaryColor,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = state.minPhotoBlurThreshold,
                            onValueChange = { viewModel.updateBlurThreshold(it) },
                            valueRange = 50f..200f,
                            steps = 29,
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryColor,
                                activeTrackColor = PrimaryColor,
                                inactiveTrackColor = DarkSurfaceVariant
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("50", color = OnSurfaceColor.copy(alpha = 0.4f), fontSize = 11.sp)
                            Text("200", color = OnSurfaceColor.copy(alpha = 0.4f), fontSize = 11.sp)
                        }
                    }
                }
            }

            // ── Eski Fotoğraf Süresi ─────────────────────────────────────
            item {
                val options = listOf(90, 180, 365)
                val labels = listOf("90 gün", "180 gün", "365 gün")
                DropdownSettingItem(
                    icon = Icons.Default.CalendarMonth,
                    title = "Eski Fotoğraf Süresi",
                    currentValue = "${state.oldPhotoDays} gün",
                    options = options,
                    labels = labels,
                    onSelect = { viewModel.updateOldPhotoDays(it) }
                )
            }

            // ── Kullanılmayan Uygulama Süresi ────────────────────────────
            item {
                val options = listOf(15, 30, 60, 90)
                val labels = listOf("15 gün", "30 gün", "60 gün", "90 gün")
                DropdownSettingItem(
                    icon = Icons.Default.PhoneAndroid,
                    title = "Kullanılmayan Uygulama Süresi",
                    currentValue = "${state.unusedAppDays} gün",
                    options = options,
                    labels = labels,
                    onSelect = { viewModel.updateUnusedAppDays(it) }
                )
            }

            // ── Minimum Dosya Boyutu ─────────────────────────────────────
            item {
                val options = listOf(10, 50, 100, 500)
                val labels = listOf("10 MB", "50 MB", "100 MB", "500 MB")
                DropdownSettingItem(
                    icon = Icons.Default.Storage,
                    title = "Minimum Dosya Boyutu",
                    currentValue = "${state.minLargeFileSizeMB} MB",
                    options = options,
                    labels = labels,
                    onSelect = { viewModel.updateMinFileSize(it) }
                )
            }

            // ═══════════════════════════════════════════════════════════════
            // GENEL
            // ═══════════════════════════════════════════════════════════════
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader(
                    icon = Icons.Default.AutoMode,
                    title = "Genel"
                )
            }

            // ── Otomatik Tarama ──────────────────────────────────────────
            item {
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoMode,
                            contentDescription = null,
                            tint = if (state.autoScanEnabled) SuccessColor else OnSurfaceColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Otomatik Tarama",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                if (state.autoScanEnabled) "Aktif" else "Kapalı",
                                color = if (state.autoScanEnabled) SuccessColor else OnSurfaceColor.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = state.autoScanEnabled,
                            onCheckedChange = { viewModel.toggleAutoScan() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SuccessColor,
                                uncheckedThumbColor = OnSurfaceColor,
                                uncheckedTrackColor = DarkSurfaceVariant
                            )
                        )
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // HAKKINDA
            // ═══════════════════════════════════════════════════════════════
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader(
                    icon = Icons.Default.Info,
                    title = "Hakkında"
                )
            }

            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        AboutRow(label = "Uygulama", value = "StorageManager")
                        HorizontalDivider(
                            color = DarkSurfaceVariant,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        AboutRow(label = "Versiyon", value = "1.0.0")
                        HorizontalDivider(
                            color = DarkSurfaceVariant,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        AboutRow(label = "Geliştirici", value = "StorageManager Team")
                        HorizontalDivider(
                            color = DarkSurfaceVariant,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                tint = OnSurfaceColor.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "AI destekli depolama yönetimi uygulaması",
                                color = OnSurfaceColor.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Yardımcı Composable'lar
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = PrimaryColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            color = PrimaryColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        content()
    }
}

@Composable
private fun DropdownSettingItem(
    icon: ImageVector,
    title: String,
    currentValue: String,
    options: List<Int>,
    labels: List<String>,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    SettingsCard {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = PrimaryColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        currentValue,
                        color = SecondaryColor,
                        fontSize = 12.sp
                    )
                }
                Icon(
                    Icons.Default.PhotoSizeSelectLarge,
                    contentDescription = null,
                    tint = OnSurfaceColor.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(DarkSurfaceVariant)
            ) {
                labels.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                label,
                                color = if (currentValue == label) PrimaryColor else OnSurfaceColor,
                                fontWeight = if (currentValue == label) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSelect(options[index])
                            expanded = false
                        }
                    )
                }
            }

        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = OnSurfaceColor.copy(alpha = 0.6f),
            fontSize = 13.sp
        )
        Text(
            value,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp
        )
    }
}
