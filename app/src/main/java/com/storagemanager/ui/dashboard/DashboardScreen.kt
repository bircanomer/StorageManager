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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.storagemanager.domain.model.ScanState
import com.storagemanager.ui.components.AnimatedCounter
import com.storagemanager.ui.components.CategoryCard
import com.storagemanager.ui.components.GradientButton
import com.storagemanager.ui.components.StorageDonutChart
import com.storagemanager.ui.components.formatFileSize
import com.storagemanager.ui.navigation.Screen
import com.storagemanager.ui.theme.*

@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val storageInfo by viewModel.storageInfo.collectAsState()
    val scanState by viewModel.scanState.collectAsState()

    Scaffold(
        containerColor = Background,
        topBar = {
            DashboardTopBar(
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
                        GradientButton(
                            text = "Akıllı Tarama Başlat",
                            onClick = { viewModel.startScan() }
                        )
                    }

                    is ScanState.Scanning -> {
                        ScanningProgress(
                            progress = state.progress,
                            currentTask = state.currentTask
                        )
                    }

                    is ScanState.Completed -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            GradientButton(
                                text = "Tekrar Tara",
                                onClick = { viewModel.startScan() }
                            )
                            CleanableCard(totalCleanableSize = state.result.totalCleanableSize)
                        }
                    }

                    is ScanState.Error -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Hata: ${state.message}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Error
                            )
                            GradientButton(
                                text = "Tekrar Dene",
                                onClick = { viewModel.startScan() }
                            )
                        }
                    }
                }
            }

            // ── 3. Category Cards (2-column grid) ───────────
            item {
                Text(
                    text = "Kategoriler",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnBackground,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            item {
                val scanResult = (scanState as? ScanState.Completed)?.result

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Row 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CategoryCard(
                            title = "Fotoğraflar",
                            subtitle = "Bulanık & duplike",
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
                            title = "Uygulamalar",
                            subtitle = "Kullanılmayan",
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
                            title = "Önbellek",
                            subtitle = "Güvenle temizle",
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
                            title = "Büyük Dosyalar",
                            subtitle = "50 MB üzeri",
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
                }
            }

            // Bottom spacing for navigation bar
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ── Top Bar ─────────────────────────────────────────────────────
@Composable
private fun DashboardTopBar(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "StorageManager",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = OnBackground
            )
            Text(
                text = "Akıllı Depolama Yöneticisi",
                style = MaterialTheme.typography.labelMedium,
                color = OnSurface.copy(alpha = 0.5f)
            )
        }
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Ayarlar",
                tint = OnSurface
            )
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
            text = "Taranıyor…",
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
            text = "%${(progress * 100).toInt()}",
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
                text = "Temizlenebilir",
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
