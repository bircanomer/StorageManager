package com.storagemanager.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.storagemanager.domain.model.ScanCategory
import com.storagemanager.domain.model.ScanState
import com.storagemanager.ui.components.AnimatedCounter
import com.storagemanager.ui.components.CategoryCard
import com.storagemanager.ui.components.GradientButton
import com.storagemanager.ui.components.StorageDonutChart
import com.storagemanager.ui.components.StorageAccessBanner
import com.storagemanager.ui.components.formatFileSize
import com.storagemanager.ui.navigation.Screen
import com.storagemanager.ui.pro.ProBadgeButton
import com.storagemanager.ui.pro.ProViewModel
import com.storagemanager.ui.components.NativeAdCard
import com.storagemanager.ui.theme.*
import androidx.compose.ui.res.stringResource
import com.storagemanager.R

@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel(),
    proViewModel: ProViewModel = hiltViewModel()
) {
    val storageInfo by viewModel.storageInfo.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val selectedCategories by viewModel.selectedCategories.collectAsStateWithLifecycle()
    val isPro by proViewModel.isPro.collectAsStateWithLifecycle()
    var showScanScopeDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Background,
        topBar = {
            DashboardTopBar(
                isPro = isPro,
                onProClick = { navController.navigate(Screen.Paywall.route) },
                onTrashClick = { navController.navigate(Screen.Trash.route) },
                onTreemapClick = { navController.navigate(Screen.Treemap.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── 0. Eksik erişim uyarısı ────────────────────
            item {
                StorageAccessBanner(modifier = Modifier.padding(top = 8.dp))
            }

            // ── 1. Donut Chart ──────────────────────────────
            item {
                StorageDonutChart(
                    storageInfo = storageInfo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            // ── 2. Scan Button / Progress ───────────────────
            item {
                when (val state = scanState) {
                    is ScanState.Idle -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            GradientButton(
                                text = stringResource(R.string.scan_button),
                                onClick = { viewModel.startScan() }
                            )
                            OutlinedButton(
                                onClick = { showScanScopeDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.scan_customize, selectedCategories.size), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    is ScanState.Scanning -> {
                        ScanningProgress(
                            progress = state.progress,
                            currentTask = state.currentTask
                        )
                    }

                    is ScanState.Completed -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    GradientButton(
                                        text = stringResource(R.string.rescan),
                                        onClick = { viewModel.startScan() }
                                    )
                                }
                                IconButton(
                                    onClick = { showScanScopeDialog = true },
                                    modifier = Modifier
                                        .size(54.dp)
                                        .background(SurfaceVariant, shape = RoundedCornerShape(16.dp))
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.scan_scope_icon), tint = Primary)
                                }
                            }
                            CleanableCard(totalCleanableSize = state.result.totalCleanableSize)
                        }
                    }

                    is ScanState.Error -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.error_with_message, state.message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Error
                            )
                            GradientButton(
                                text = stringResource(R.string.retry),
                                onClick = { viewModel.startScan() }
                            )
                        }
                    }
                }
            }

            // ── 3. Category Cards (2-column grid) ───────────
            item {
                Text(
                    text = stringResource(R.string.dashboard_categories),
                    style = MaterialTheme.typography.titleLarge,
                    color = OnBackground,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            item {
                // Tarama sürerken de biten kategorilerin sonuçları kartlara yansır
                val scanResult = (scanState as? ScanState.Completed)?.result
                    ?: (scanState as? ScanState.Scanning)?.partial

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Row 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CategoryCard(
                            title = stringResource(R.string.category_photos),
                            subtitle = stringResource(R.string.card_photos_subtitle),
                            icon = Icons.Filled.Photo,
                            itemCount = scanResult?.junkPhotos?.size ?: 0,
                            totalSize = scanResult?.junkPhotos?.sumOf { it.size } ?: 0L,
                            gradientColors = listOf(
                                PhotoColor,
                                PhotoColor.copy(alpha = 0.6f)
                            ),
                            onClick = { navController.navigate(Screen.Photos.route) },
                            modifier = Modifier.weight(1f)
                        )

                        CategoryCard(
                            title = stringResource(R.string.category_apps),
                            subtitle = stringResource(R.string.card_apps_subtitle),
                            icon = Icons.Filled.Apps,
                            itemCount = scanResult?.unusedApps?.size ?: 0,
                            totalSize = scanResult?.unusedApps?.sumOf { it.appSize + it.cacheSize }
                                ?: 0L,
                            gradientColors = listOf(
                                AppColor,
                                AppColor.copy(alpha = 0.6f)
                            ),
                            onClick = { navController.navigate(Screen.Apps.route) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CategoryCard(
                            title = stringResource(R.string.category_cache),
                            subtitle = stringResource(R.string.card_cache_subtitle),
                            icon = Icons.Filled.Cached,
                            itemCount = scanResult?.cacheInfos?.size ?: 0,
                            totalSize = scanResult?.cacheInfos?.sumOf { it.cacheSize } ?: 0L,
                            gradientColors = listOf(
                                CacheColor,
                                CacheColor.copy(alpha = 0.6f)
                            ),
                            onClick = { navController.navigate(Screen.Cache.route) },
                            modifier = Modifier.weight(1f)
                        )

                        CategoryCard(
                            title = stringResource(R.string.category_large_files),
                            subtitle = stringResource(R.string.card_large_files_subtitle),
                            icon = Icons.Filled.FolderOpen,
                            itemCount = scanResult?.largeFiles?.size ?: 0,
                            totalSize = scanResult?.largeFiles?.sumOf { it.size } ?: 0L,
                            gradientColors = listOf(
                                VideoColor,
                                VideoColor.copy(alpha = 0.6f)
                            ),
                            onClick = { navController.navigate(Screen.Files.route) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 3
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CategoryCard(
                            title = stringResource(R.string.downloads_tab),
                            subtitle = stringResource(R.string.card_downloads_subtitle),
                            icon = Icons.Filled.Download,
                            itemCount = (scanResult?.downloadJunks?.size ?: 0) + (scanResult?.duplicateDocuments?.size ?: 0),
                            totalSize = (scanResult?.downloadJunks?.sumOf { it.size } ?: 0L) + (scanResult?.duplicateDocuments?.sumOf { it.totalSize } ?: 0L),
                            gradientColors = listOf(
                                Secondary,
                                Secondary.copy(alpha = 0.6f)
                            ),
                            onClick = { navController.navigate(Screen.Downloads.route) },
                            modifier = Modifier.weight(1f)
                        )

                        CategoryCard(
                            title = stringResource(R.string.category_system_junk),
                            subtitle = stringResource(R.string.card_system_junk_subtitle),
                            icon = Icons.Filled.CleaningServices,
                            itemCount = scanResult?.systemJunks?.size ?: 0,
                            totalSize = scanResult?.systemJunks?.sumOf { it.size } ?: 0L,
                            gradientColors = listOf(
                                PrimaryVariant,
                                PrimaryVariant.copy(alpha = 0.6f)
                            ),
                            onClick = { navController.navigate(Screen.SystemJunk.route) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── Native reklam (yalnızca ücretsiz sürüm) ─────
            // Kart listeden sonra gelir; tarama akışını kesmez ve reklam
            // yüklenmezse hiçbir boşluk bırakmaz.
            if (!isPro) {
                item {
                    NativeAdCard(adsManager = proViewModel.ads())
                }
            }

            // Bottom spacing for navigation bar
            item {
                Spacer(modifier = Modifier.height(24.dp).navigationBarsPadding())
            }
        }
    }

    if (showScanScopeDialog) {
        ScanScopeDialog(
            selectedCategories = selectedCategories,
            onToggleCategory = { viewModel.toggleCategory(it) },
            onSelectAll = { viewModel.selectAllCategories() },
            onStartScan = { viewModel.startScan(selectedCategories) },
            onDismiss = { showScanScopeDialog = false }
        )
    }
}

// ── Top Bar ─────────────────────────────────────────────────────
@Composable
private fun DashboardTopBar(
    isPro: Boolean,
    onProClick: () -> Unit,
    onTrashClick: () -> Unit,
    onTreemapClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.dashboard_title),
                style = MaterialTheme.typography.labelMedium,
                color = OnSurface.copy(alpha = 0.5f)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProBadgeButton(isPro = isPro, onClick = onProClick)
            IconButton(onClick = onTrashClick) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.trash_title),
                    tint = OnSurface
                )
            }
            IconButton(onClick = onTreemapClick) {
                Icon(
                    imageVector = Icons.Filled.PieChart,
                    contentDescription = stringResource(R.string.treemap_title),
                    tint = Primary
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = OnSurface
                )
            }
        }
    }
}

// ── Scanning Progress ───────────────────────────────────────────
@Composable
private fun ScanningProgress(
    progress: Float,
    currentTask: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(20.dp)
    ) {
        Text(
            text = stringResource(R.string.scanning),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = OnBackground,
            modifier = Modifier.alpha(pulseAlpha)
        )

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = Primary,
            trackColor = SurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = currentTask,
            style = MaterialTheme.typography.labelMedium,
            color = OnSurface.copy(alpha = 0.6f)
        )

        Text(
            text = stringResource(R.string.percent_format, (progress * 100).toInt()),
            style = MaterialTheme.typography.labelSmall,
            color = PrimaryVariant
        )
    }
}

// ── Cleanable Card ──────────────────────────────────────────────
@Composable
private fun CleanableCard(totalCleanableSize: Long) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(600)) +
                slideInVertically(
                    initialOffsetY = { it / 3 },
                    animationSpec = tween(600)
                )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.cleanable_space),
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            AnimatedCounter(
                targetValue = totalCleanableSize,
                formatAsSize = true,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Success
                )
            )
        }
    }
}

// ── Tarama Kapsamı Diyaloğu ────────────────────────────────────
@Composable
private fun ScanScopeDialog(
    selectedCategories: Set<ScanCategory>,
    onToggleCategory: (ScanCategory) -> Unit,
    onSelectAll: () -> Unit,
    onStartScan: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        titleContentColor = OnBackground,
        textContentColor = OnSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.scan_scope_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                TextButton(onClick = onSelectAll) {
                    Text(stringResource(R.string.select_all), color = Primary, fontSize = 12.sp)
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.scan_scope_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(2.dp))

                ScanCategory.entries.forEach { category ->
                    val isChecked = category in selectedCategories
                    Card(
                        onClick = { onToggleCategory(category) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isChecked) Primary.copy(alpha = 0.15f) else SurfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { onToggleCategory(category) },
                                colors = CheckboxDefaults.colors(checkedColor = Primary)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(category.emoji, fontSize = 18.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(category.labelRes),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = OnBackground
                                )
                                Text(
                                    stringResource(category.descriptionRes),
                                    fontSize = 10.sp,
                                    color = OnSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            GradientButton(
                text = stringResource(R.string.scan_selected_categories, selectedCategories.size),
                onClick = {
                    onStartScan()
                    onDismiss()
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = OnSurface.copy(alpha = 0.7f))
            }
        }
    )
}
