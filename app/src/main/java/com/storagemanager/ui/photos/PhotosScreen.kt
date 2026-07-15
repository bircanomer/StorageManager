package com.storagemanager.ui.photos

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.storagemanager.domain.model.JunkPhoto
import com.storagemanager.domain.model.JunkType

// ── Renk Paleti ──────────────────────────────────────────────────────────────
private val DarkBackground = Color(0xFF0D0D1A)
private val DarkSurface = Color(0xFF1A1A2E)
private val DarkSurfaceVariant = Color(0xFF252540)
private val PrimaryColor = Color(0xFF6C63FF)
private val SecondaryColor = Color(0xFF00BCD4)
private val OnSurfaceColor = Color(0xFFC8C8D8)
private val ErrorColor = Color(0xFFFF6B6B)
private val SuccessColor = Color(0xFF4CAF50)

// ── Yardımcı Fonksiyon ──────────────────────────────────────────────────────
private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    navController: NavController,
    viewModel: PhotosViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    val tabs = JunkType.entries
    val selectedTabIndex = tabs.indexOf(state.selectedTab)

    val currentPhotos = when (state.selectedTab) {
        JunkType.BLURRY -> state.blurryPhotos
        JunkType.DUPLICATE -> state.duplicatePhotos
        JunkType.SCREENSHOT -> state.screenshots
        JunkType.OLD -> state.oldPhotos
    }

    val selectedInCurrentTab = currentPhotos.count { it.id in state.selectedIds }
    val totalSizeCurrentTab = currentPhotos.sumOf { it.size }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Fotoğraf Analizi",
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
                actions = {
                    if (state.selectedIds.isNotEmpty()) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Sil",
                                tint = ErrorColor
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = state.selectedIds.isNotEmpty(),
                enter = scaleIn() + fadeIn(),
                exit = fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    onClick = { showDeleteDialog = true },
                    containerColor = ErrorColor,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("${state.selectedIds.size} Fotoğraf Sil")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Sekme Satırı ─────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = DarkSurface,
                contentColor = PrimaryColor,
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            height = 3.dp,
                            color = PrimaryColor
                        )
                    }
                },
                divider = {}
            ) {
                tabs.forEachIndexed { index, junkType ->
                    val count = when (junkType) {
                        JunkType.BLURRY -> state.blurryPhotos.size
                        JunkType.DUPLICATE -> state.duplicatePhotos.size
                        JunkType.SCREENSHOT -> state.screenshots.size
                        JunkType.OLD -> state.oldPhotos.size
                    }
                    val tabLabel = when (junkType) {
                        JunkType.BLURRY -> "Bulanık"
                        JunkType.DUPLICATE -> "Duplike"
                        JunkType.SCREENSHOT -> "Ekran Gör."
                        JunkType.OLD -> "Eski"
                    }

                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { viewModel.setTab(junkType) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    tabLabel,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (count > 0) {
                                    Spacer(Modifier.width(4.dp))
                                    Badge(
                                        containerColor = if (selectedTabIndex == index) PrimaryColor else DarkSurfaceVariant,
                                        contentColor = Color.White
                                    ) {
                                        Text("$count", fontSize = 10.sp)
                                    }
                                }
                            }
                        },
                        selectedContentColor = PrimaryColor,
                        unselectedContentColor = OnSurfaceColor.copy(alpha = 0.6f)
                    )
                }
            }

            // ── Özet Çubuğu ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                PrimaryColor.copy(alpha = 0.1f),
                                SecondaryColor.copy(alpha = 0.1f)
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${currentPhotos.size} fotoğraf • ${formatFileSize(totalSizeCurrentTab)}",
                    color = OnSurfaceColor,
                    fontSize = 13.sp
                )
                Row {
                    TextButton(onClick = { viewModel.selectAll() }) {
                        Icon(
                            Icons.Default.SelectAll,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = PrimaryColor
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Tümünü Seç", color = PrimaryColor, fontSize = 12.sp)
                    }
                    if (state.selectedIds.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                Icons.Outlined.Deselect,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = OnSurfaceColor
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Temizle", color = OnSurfaceColor, fontSize = 12.sp)
                        }
                    }
                }
            }

            // ── İçerik ──────────────────────────────────────────────────
            when {
                state.isLoading -> {
                    // Shimmer / Skeleton yükleme durumu
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(8) {
                            Card(
                                modifier = Modifier
                                    .aspectRatio(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = DarkSurfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    DarkSurfaceVariant.copy(alpha = 0.3f),
                                                    DarkSurfaceVariant.copy(alpha = 0.6f),
                                                    DarkSurfaceVariant.copy(alpha = 0.3f)
                                                )
                                            )
                                        )
                                )
                            }
                        }
                    }
                }

                currentPhotos.isEmpty() -> {
                    // Boş durum
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = OnSurfaceColor.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Bu kategoride fotoğraf bulunamadı",
                                color = OnSurfaceColor.copy(alpha = 0.5f),
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Tüm fotoğraflarınız temiz görünüyor! 🎉",
                                color = SuccessColor.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = currentPhotos,
                            key = { it.id }
                        ) { photo ->
                            PhotoGridItem(
                                photo = photo,
                                isSelected = photo.id in state.selectedIds,
                                showBlurScore = state.selectedTab == JunkType.BLURRY,
                                onClick = { viewModel.toggleSelection(photo.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Silme Onay Dialogu ───────────────────────────────────────────────────
    if (showDeleteDialog) {
        val selectedPhotos = listOf(
            state.blurryPhotos,
            state.duplicatePhotos,
            state.screenshots,
            state.oldPhotos
        ).flatten().filter { it.id in state.selectedIds }
        val totalSize = selectedPhotos.sumOf { it.size }

        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = DarkSurface,
            titleContentColor = Color.White,
            textContentColor = OnSurfaceColor,
            title = {
                Text("Fotoğrafları Sil", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "${state.selectedIds.size} fotoğraf silinecek ve ${formatFileSize(totalSize)} alan boşaltılacak.\n\nBu işlem geri alınamaz.",
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelected()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Sil", color = ErrorColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("İptal", color = OnSurfaceColor)
                }
            }
        )
    }
}

@Composable
private fun PhotoGridItem(
    photo: JunkPhoto,
    isSelected: Boolean,
    showBlurScore: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) PrimaryColor else Color.Transparent,
        animationSpec = tween(200),
        label = "borderAnim"
    )

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Fotoğraf küçük resmi
            AsyncImage(
                model = Uri.parse(photo.uri),
                contentDescription = photo.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Seçim göstergesi – sağ üst
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (isSelected) "Seçili" else "Seçilmedi",
                    tint = if (isSelected) PrimaryColor else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                )
            }

            // Alt gradyan bant
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Dosya boyutu – sol alt
                    Text(
                        formatFileSize(photo.size),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Bulanıklık skoru – sağ alt (sadece bulanık sekmede)
                    if (showBlurScore) {
                        Text(
                            "%.0f".format(photo.score),
                            color = SecondaryColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(
                                    color = DarkBackground.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
