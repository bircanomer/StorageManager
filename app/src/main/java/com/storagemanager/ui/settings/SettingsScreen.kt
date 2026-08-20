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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Preview
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import com.storagemanager.ml.SharpnessProbe
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.storagemanager.BuildConfig
import com.storagemanager.R
import com.storagemanager.ui.navigation.Screen
import com.storagemanager.ui.permissions.PermissionUtils
import com.storagemanager.ui.pro.ProViewModel

// ── Renk Paleti ──────────────────────────────────────────────────────────────
private val DarkBackground = Color(0xFF0D0D1A)
private val DarkSurface = Color(0xFF1A1A2E)
private val DarkSurfaceVariant = Color(0xFF252540)
private val PrimaryColor = Color(0xFF6C63FF)
private val SecondaryColor = Color(0xFF00BCD4)
private val OnSurfaceColor = Color(0xFFC8C8D8)
private val ErrorColor = Color(0xFFFF6B6B)
private val SuccessColor = Color(0xFF4CAF50)
private val GoldColor = Color(0xFFFFC107)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
    proViewModel: ProViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isPro by proViewModel.isPro.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // İzin durumları Ayarlar'dan dönüşte yeniden okunmalı
    var usageStatsGranted by remember { mutableStateOf(PermissionUtils.hasUsageStatsPermission(context)) }
    var allFilesGranted by remember { mutableStateOf(PermissionUtils.hasAllFilesAccess()) }
    var languageDialogVisible by remember { mutableStateOf(false) }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                usageStatsGranted = PermissionUtils.hasUsageStatsPermission(context)
                allFilesGranted = PermissionUtils.hasAllFilesAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (languageDialogVisible) {
        LanguagePickerDialog(onDismiss = { languageDialogVisible = false })
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
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
            // PRO
            // ═══════════════════════════════════════════════════════════════
            item {
                SectionHeader(
                    icon = Icons.Default.WorkspacePremium,
                    title = stringResource(R.string.section_pro)
                )
            }

            item {
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(Screen.Paywall.route) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = if (isPro) GoldColor else PrimaryColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(if (isPro) R.string.pro_status_active else R.string.pro_status_free),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                stringResource(if (isPro) R.string.pro_manage else R.string.pro_upgrade_hint),
                                color = OnSurfaceColor.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = OnSurfaceColor.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // DİL
            // ═══════════════════════════════════════════════════════════════
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.section_language)
                )
            }

            item {
                val current = AppLanguage.current()
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { languageDialogVisible = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            tint = PrimaryColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.language_title),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                current?.nativeName ?: stringResource(R.string.language_system_default),
                                color = SecondaryColor,
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = OnSurfaceColor.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // TARAMA AYARLARI
            // ═══════════════════════════════════════════════════════════════
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader(
                    icon = Icons.Default.Tune,
                    title = stringResource(R.string.section_scan_settings)
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
                                    stringResource(R.string.blur_threshold),
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    stringResource(
                                        R.string.blur_threshold_value,
                                        state.minPhotoBlurThreshold.toInt()
                                    ),
                                    color = SecondaryColor,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = state.minPhotoBlurThreshold,
                            onValueChange = { viewModel.updateBlurThreshold(it) },
                            // Aralık gerçek çözünürlükte ölçülen netlik dağılımına göre:
                            // p2 30, p5 54, p10 97, medyan 745 (195 fotoğrafl��k örneklem).
                            valueRange = SharpnessProbe.MIN_THRESHOLD..SharpnessProbe.MAX_THRESHOLD,
                            steps = 23,
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
                            Text("20", color = OnSurfaceColor.copy(alpha = 0.4f), fontSize = 11.sp)
                            Text("500", color = OnSurfaceColor.copy(alpha = 0.4f), fontSize = 11.sp)
                        }

                        // Eşiği körlemesine ayarlamak yerine örneklem üzerinde hemen dene
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { viewModel.runBlurPreview() }) {
                            Icon(
                                Icons.Default.Preview,
                                contentDescription = null,
                                tint = SecondaryColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.blur_preview_action),
                                color = SecondaryColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // ── Eski Fotoğraf Süresi ─────────────────────────────────────
            item {
                val options = listOf(90, 180, 365)
                DropdownSettingItem(
                    icon = Icons.Default.CalendarMonth,
                    title = stringResource(R.string.old_photo_duration),
                    currentValue = stringResource(R.string.days_format, state.oldPhotoDays),
                    options = options,
                    labels = options.map { stringResource(R.string.days_format, it) },
                    onSelect = { viewModel.updateOldPhotoDays(it) }
                )
            }

            // ── Kullanılmayan Uygulama Süresi ────────────────────────────
            item {
                val options = listOf(15, 30, 60, 90)
                DropdownSettingItem(
                    icon = Icons.Default.PhoneAndroid,
                    title = stringResource(R.string.unused_app_duration),
                    currentValue = stringResource(R.string.days_format, state.unusedAppDays),
                    options = options,
                    labels = options.map { stringResource(R.string.days_format, it) },
                    onSelect = { viewModel.updateUnusedAppDays(it) }
                )
            }

            // ── Minimum Dosya Boyutu ─────────────────────────────────────
            item {
                val options = listOf(10, 50, 100, 500)
                DropdownSettingItem(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.min_file_size),
                    currentValue = stringResource(R.string.mb_format, state.minLargeFileSizeMB),
                    options = options,
                    labels = options.map { stringResource(R.string.mb_format, it) },
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
                    title = stringResource(R.string.section_general)
                )
            }

            // ── AI İçerik Sınıflandırması ────────────────────────────────
            item {
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (state.aiClassificationEnabled) SuccessColor else OnSurfaceColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.ai_classification),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                stringResource(
                                    if (state.aiClassificationEnabled) R.string.ai_classification_on
                                    else R.string.ai_classification_off
                                ),
                                color = if (state.aiClassificationEnabled) SuccessColor else OnSurfaceColor.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = state.aiClassificationEnabled,
                            onCheckedChange = { viewModel.toggleAiClassification() },
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

            // ── Otomatik Tarama (Pro) ────────────────────────────────────
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.auto_scan),
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                if (!isPro) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.pro_badge),
                                        color = GoldColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .background(
                                                GoldColor.copy(alpha = 0.15f),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                stringResource(
                                    if (state.autoScanEnabled) R.string.auto_scan_on
                                    else R.string.auto_scan_off
                                ),
                                color = if (state.autoScanEnabled) SuccessColor else OnSurfaceColor.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = state.autoScanEnabled,
                            onCheckedChange = {
                                if (isPro) {
                                    viewModel.toggleAutoScan()
                                } else {
                                    navController.navigate(Screen.Paywall.route)
                                }
                            },
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
            // GELİŞTİRİCİ — yalnızca debug derlemesinde derlenir
            // ═══════════════════════════════════════════════════════════════
            if (BuildConfig.DEBUG) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader(
                        icon = Icons.Default.BugReport,
                        title = stringResource(R.string.section_developer)
                    )
                }

                item {
                    SettingsCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = if (state.debugProEnabled) GoldColor else OnSurfaceColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.debug_pro),
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    stringResource(R.string.debug_pro_desc),
                                    color = OnSurfaceColor.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = state.debugProEnabled,
                                onCheckedChange = { viewModel.toggleDebugPro() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = GoldColor,
                                    uncheckedThumbColor = OnSurfaceColor,
                                    uncheckedTrackColor = DarkSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // İZİNLER
            // ═══════════════════════════════════════════════════════════════
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.section_permissions)
                )
            }

            // Özel erişim izinleri artık uygulama açılışında zorla istenmiyor;
            // kullanıcı buradan bilinçli olarak veriyor.
            item {
                PermissionRow(
                    title = stringResource(R.string.permission_usage_stats),
                    description = stringResource(R.string.permission_usage_stats_desc),
                    granted = usageStatsGranted,
                    onClick = { PermissionUtils.openUsageAccessSettings(context) }
                )
            }

            item {
                PermissionRow(
                    title = stringResource(R.string.permission_all_files),
                    description = stringResource(R.string.permission_all_files_desc),
                    granted = allFilesGranted,
                    onClick = { PermissionUtils.openAllFilesAccessSettings(context) }
                )
            }

            // ═══════════════════════════════════════════════════════════════
            // HAKKINDA
            // ═══════════════════════════════════════════════════════════════
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.section_about)
                )
            }

            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        AboutRow(
                            label = stringResource(R.string.about_app),
                            value = stringResource(R.string.app_name)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = DarkSurfaceVariant, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                        AboutRow(label = stringResource(R.string.about_version), value = "1.0.0")
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = DarkSurfaceVariant, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                        AboutRow(
                            label = stringResource(R.string.about_developer),
                            value = "StorageManager Team"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = DarkSurfaceVariant, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))
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
                                stringResource(R.string.about_tagline),
                                color = OnSurfaceColor.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    BlurPreviewDialog(
        state = state.blurPreview,
        onDismiss = { viewModel.dismissBlurPreview() }
    )
}

/**
 * Netlik eşiğinin rastgele bir örneklemde neyi işaretlediğini gösterir.
 *
 * Amacı tek: eşiği ayarlarken sonucu görmek için dakikalarca süren tam taramayı
 * beklemek zorunda kalmamak. Gösterilen fotoğraflar üretimdeki aynı iki aşamalı
 * yoldan geçer, en bulanıktan başlayarak sıralanır.
 */
@Composable
private fun BlurPreviewDialog(
    state: BlurPreviewState,
    onDismiss: () -> Unit
) {
    if (state is BlurPreviewState.Idle) return

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = Color.White,
        textContentColor = OnSurfaceColor,
        title = { Text(stringResource(R.string.blur_preview_title)) },
        text = {
            when (state) {
                is BlurPreviewState.Idle -> Unit
                is BlurPreviewState.Running -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = PrimaryColor
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.blur_preview_running), fontSize = 13.sp)
                    }
                }
                is BlurPreviewState.Ready -> {
                    Column {
                        Text(
                            stringResource(
                                R.string.blur_preview_summary,
                                state.flagged.size,
                                state.sampleSize,
                                state.threshold.toInt()
                            ),
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        if (state.flagged.isEmpty()) {
                            Text(
                                stringResource(R.string.blur_preview_empty),
                                color = OnSurfaceColor.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.heightIn(max = 340.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(state.flagged, key = { it.id }) { photo ->
                                    Column {
                                        AsyncImage(
                                            model = photo.uri,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                        // Skor gösteriliyor ki eşiği nereye çekeceğin somutlaşsın
                                        Text(
                                            photo.score.toInt().toString(),
                                            color = OnSurfaceColor.copy(alpha = 0.6f),
                                            fontSize = 10.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close), color = PrimaryColor)
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// Yardımcı Composable'lar
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Dil seçim diyaloğu.
 *
 * Dil adları kasıtlı olarak kendi dillerinde yazılır; yanlış dile düşen kullanıcı
 * listede kendi dilini yine de bulabilmelidir.
 */
@Composable
private fun LanguagePickerDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text(
                stringResource(R.string.language_title),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            val current = AppLanguage.current()
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                item {
                    LanguageRow(
                        label = stringResource(R.string.language_system_default),
                        selected = current == null,
                        onClick = {
                            AppLanguage.apply(null)
                            onDismiss()
                        }
                    )
                }
                items(AppLanguage.entries.size) { index ->
                    val language = AppLanguage.entries[index]
                    LanguageRow(
                        label = language.nativeName,
                        selected = current == language,
                        onClick = {
                            AppLanguage.apply(language)
                            onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = OnSurfaceColor)
            }
        }
    )
}

@Composable
private fun LanguageRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (selected) PrimaryColor else OnSurfaceColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = PrimaryColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

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
            letterSpacing = 0.5.sp,
            modifier = Modifier.weight(1f)
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
                    Icons.Default.KeyboardArrowDown,
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
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp
        )
    }
}

/**
 * Özel erişim izni satırı.
 */
@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (granted) SuccessColor else ErrorColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    text = if (granted) stringResource(R.string.permission_granted) else description,
                    color = if (granted) SuccessColor else OnSurfaceColor.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = OnSurfaceColor.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
