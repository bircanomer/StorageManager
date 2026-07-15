package com.storagemanager.ui.files

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.storagemanager.domain.model.LargeFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Renk Paleti ──────────────────────────────────────────────────────────────
private val DarkBackground = Color(0xFF0D0D1A)
private val DarkSurface = Color(0xFF1A1A2E)
private val DarkSurfaceVariant = Color(0xFF252540)
private val PrimaryColor = Color(0xFF6C63FF)
private val SecondaryColor = Color(0xFF00BCD4)
private val OnSurfaceColor = Color(0xFFC8C8D8)
private val ErrorColor = Color(0xFFFF6B6B)
private val SuccessColor = Color(0xFF4CAF50)

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale("tr"))
    return sdf.format(Date(timestamp))
}

private fun getFileIcon(mimeType: String?): ImageVector {
    return when {
        mimeType == null -> Icons.AutoMirrored.Filled.InsertDriveFile
        mimeType.startsWith("image/") -> Icons.Default.Image
        mimeType.startsWith("video/") -> Icons.Default.VideoFile
        mimeType.startsWith("audio/") -> Icons.Default.AudioFile
        mimeType.contains("pdf") || mimeType.contains("document") || mimeType.contains("text") -> Icons.Default.Description
        mimeType.contains("zip") || mimeType.contains("rar") || mimeType.contains("tar") || mimeType.contains("archive") -> Icons.Default.Archive
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

private fun getFileIconColor(mimeType: String?): Color {
    return when {
        mimeType == null -> OnSurfaceColor
        mimeType.startsWith("image/") -> Color(0xFF4CAF50)
        mimeType.startsWith("video/") -> Color(0xFFFF7043)
        mimeType.startsWith("audio/") -> Color(0xFFAB47BC)
        mimeType.contains("pdf") || mimeType.contains("document") -> Color(0xFF42A5F5)
        mimeType.contains("zip") || mimeType.contains("archive") -> Color(0xFFFFCA28)
        else -> OnSurfaceColor
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    navController: NavController,
    viewModel: FilesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val selectedCount = state.selectedPaths.size
    val selectedTotalSize = state.largeFiles
        .filter { it.path in state.selectedPaths }
        .sumOf { it.size }
    val allTotalSize = state.largeFiles.sumOf { it.size }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Büyük Dosyalar",
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
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sırala",
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            val sortOptions = listOf(
                                SortBy.SIZE_DESC to "Boyut (Büyük → Küçük)",
                                SortBy.SIZE_ASC to "Boyut (Küçük → Büyük)",
                                SortBy.DATE_DESC to "Tarih (Yeni → Eski)",
                                SortBy.DATE_ASC to "Tarih (Eski → Yeni)",
                                SortBy.NAME_ASC to "İsim (A → Z)"
                            )
                            sortOptions.forEach { (sortBy, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            label,
                                            color = if (state.sortBy == sortBy) PrimaryColor else OnSurfaceColor,
                                            fontWeight = if (state.sortBy == sortBy) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.setSortBy(sortBy)
                                        showSortMenu = false
                                    }
                                )

                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = selectedCount > 0,
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
                    Text("$selectedCount Dosya Sil (${formatFileSize(selectedTotalSize)})")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Boyut Filtre Çipleri ─────────────────────────────────────
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sizeFilters = listOf(10, 50, 100, 500)
                items(sizeFilters) { mb ->
                    FilterChip(
                        selected = state.minSizeMB == mb,
                        onClick = { viewModel.setMinSize(mb) },
                        label = {
                            Text("${mb} MB+", fontSize = 13.sp)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryColor,
                            selectedLabelColor = Color.White,
                            containerColor = DarkSurfaceVariant,
                            labelColor = OnSurfaceColor
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            // ── Özet ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                PrimaryColor.copy(alpha = 0.08f),
                                SecondaryColor.copy(alpha = 0.08f)
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${state.largeFiles.size} dosya • ${formatFileSize(allTotalSize)} toplam",
                    color = OnSurfaceColor,
                    fontSize = 13.sp
                )
            }

            // ── İçerik ──────────────────────────────────────────────────
            when {
                state.isLoading -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(8) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = DarkSurfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
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

                state.largeFiles.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = OnSurfaceColor.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Büyük dosya bulunamadı",
                                color = OnSurfaceColor.copy(alpha = 0.5f),
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${state.minSizeMB} MB üzerinde dosya yok 👍",
                                color = SuccessColor.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = if (selectedCount > 0) 80.dp else 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            items = state.largeFiles,
                            key = { _, file -> file.path }
                        ) { index, file ->
                            FileListItem(
                                file = file,
                                isSelected = file.path in state.selectedPaths,
                                isAlternate = index % 2 == 1,
                                onClick = { viewModel.toggleSelection(file.path) }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Silme Onay Dialogu ───────────────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = DarkSurface,
            titleContentColor = Color.White,
            textContentColor = OnSurfaceColor,
            title = {
                Text("Dosyaları Sil", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "$selectedCount dosya silinecek ve ${formatFileSize(selectedTotalSize)} alan boşaltılacak.\n\nBu işlem geri alınamaz!",
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
private fun FileListItem(
    file: LargeFile,
    isSelected: Boolean,
    isAlternate: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        isSelected -> PrimaryColor.copy(alpha = 0.15f)
        isAlternate -> DarkSurfaceVariant.copy(alpha = 0.5f)
        else -> DarkSurface
    }

    val iconColor = getFileIconColor(file.mimeType)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Seçim göstergesi
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (isSelected) PrimaryColor else OnSurfaceColor.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp)
            )

            Spacer(Modifier.width(12.dp))

            // Dosya tipi ikonu
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        iconColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    getFileIcon(file.mimeType),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            // Dosya bilgileri
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    formatDate(file.lastModified),
                    color = OnSurfaceColor.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.width(8.dp))

            // Dosya boyutu (belirgin)
            Text(
                formatFileSize(file.size),
                color = SecondaryColor,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}
