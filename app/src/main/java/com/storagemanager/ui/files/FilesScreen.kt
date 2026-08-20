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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.storagemanager.ui.components.MediaItemPreview
import com.storagemanager.ui.components.MediaPreviewDialog
import androidx.compose.ui.res.stringResource
import com.storagemanager.R
import com.storagemanager.ui.pro.ProViewModel
import com.storagemanager.ui.components.formatFileSize
import com.storagemanager.ui.components.StorageAccessBanner
import com.storagemanager.ui.components.openFileSafely

// ── Renk Paleti ──────────────────────────────────────────────────────────────
private val DarkBackground = Color(0xFF0D0D1A)
private val DarkSurface = Color(0xFF1A1A2E)
private val DarkSurfaceVariant = Color(0xFF252540)
private val PrimaryColor = Color(0xFF6C63FF)
private val SecondaryColor = Color(0xFF00BCD4)
private val OnSurfaceColor = Color(0xFFC8C8D8)
private val ErrorColor = Color(0xFFFF6B6B)
private val SuccessColor = Color(0xFF4CAF50)


private fun formatDate(context: android.content.Context, timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        context.getString(R.string.unknown_date)
    }
}

private fun getFileIcon(mimeType: String?): ImageVector {
    return when {
        mimeType == null -> Icons.AutoMirrored.Filled.Article
        mimeType.startsWith("image/") -> Icons.Default.Image
        mimeType.startsWith("video/") -> Icons.Default.VideoFile
        mimeType.startsWith("audio/") -> Icons.Default.AudioFile
        mimeType.contains("pdf") || mimeType.contains("document") || mimeType.contains("text") -> Icons.Default.Description
        mimeType.contains("zip") || mimeType.contains("rar") || mimeType.contains("tar") || mimeType.contains("archive") -> Icons.Default.Archive
        else -> Icons.AutoMirrored.Filled.Article
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

private fun isFileInCategory(file: LargeFile, category: FileCategoryFilter): Boolean {
    val mime = file.mimeType?.lowercase(Locale.ROOT) ?: ""
    val name = file.name.lowercase(Locale.ROOT)
    return when (category) {
        FileCategoryFilter.ALL -> true
        FileCategoryFilter.VIDEO -> mime.startsWith("video/") ||
                name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") ||
                name.endsWith(".mov") || name.endsWith(".3gp") || name.endsWith(".webm")
        FileCategoryFilter.IMAGE -> mime.startsWith("image/") ||
                name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                name.endsWith(".webp") || name.endsWith(".heic") || name.endsWith(".gif")
        FileCategoryFilter.AUDIO -> mime.startsWith("audio/") ||
                name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg") ||
                name.endsWith(".m4a") || name.endsWith(".flac") || name.endsWith(".aac")
        FileCategoryFilter.DOCS -> mime.contains("pdf") || mime.contains("document") || mime.contains("text") ||
                name.endsWith(".pdf") || name.endsWith(".doc") || name.endsWith(".docx") ||
                name.endsWith(".txt") || name.endsWith(".xls") || name.endsWith(".xlsx") ||
                name.endsWith(".ppt") || name.endsWith(".pptx")
        FileCategoryFilter.OTHER -> !isFileInCategory(file, FileCategoryFilter.VIDEO) &&
                !isFileInCategory(file, FileCategoryFilter.IMAGE) &&
                !isFileInCategory(file, FileCategoryFilter.AUDIO) &&
                !isFileInCategory(file, FileCategoryFilter.DOCS)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    navController: NavController,
    viewModel: FilesViewModel = hiltViewModel(),
    proViewModel: ProViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showProLimitDialog by remember { mutableStateOf(false) }
    val isPro by proViewModel.isPro.collectAsStateWithLifecycle()
    var showSortMenu by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }

    val filteredFiles = remember(state.largeFiles, state.selectedCategory, state.minSizeMB) {
        val minBytes = state.minSizeMB.toLong() * 1024L * 1024L
        state.largeFiles.filter { isFileInCategory(it, state.selectedCategory) && it.size >= minBytes }
    }
    val mediaFiles = remember(filteredFiles) {
        filteredFiles.filter { file ->
            val nameLower = file.name.lowercase(Locale.ROOT)
            val mimeLower = file.mimeType?.lowercase(Locale.ROOT) ?: ""
            mimeLower.startsWith("image/") || mimeLower.startsWith("video/") ||
                    nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") ||
                    nameLower.endsWith(".webp") || nameLower.endsWith(".heic") || nameLower.endsWith(".mp4") ||
                    nameLower.endsWith(".mkv") || nameLower.endsWith(".avi") || nameLower.endsWith(".mov")
        }
    }
    val selectedCount = state.selectedPaths.size
    val selectedTotalSize = state.largeFiles
        .filter { it.path in state.selectedPaths }
        .sumOf { it.size }
    val filteredTotalSize = filteredFiles.sumOf { it.size }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.files_title),
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
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.sort),
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            val sortOptions = listOf(
                                SortBy.SIZE_DESC to stringResource(R.string.sort_size_desc),
                                SortBy.SIZE_ASC to stringResource(R.string.sort_size_asc),
                                SortBy.DATE_DESC to stringResource(R.string.sort_date_desc),
                                SortBy.DATE_ASC to stringResource(R.string.sort_date_asc),
                                SortBy.NAME_ASC to stringResource(R.string.sort_name_asc)
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
                    onClick = {
                        if (!isPro && selectedCount > ProViewModel.FREE_BULK_LIMIT) {
                            showProLimitDialog = true
                        } else {
                            showDeleteDialog = true
                        }
                    },
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = ErrorColor,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.files_delete_count, selectedCount, formatFileSize(selectedTotalSize)))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Kategori Filtre Çipleri ─────────────────────────────────
            LazyRow(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FileCategoryFilter.entries.toTypedArray()) { cat ->
                    FilterChip(
                        selected = state.selectedCategory == cat,
                        onClick = { viewModel.setCategoryFilter(cat) },
                        label = {
                            Text(stringResource(cat.labelRes), fontSize = 13.sp)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SecondaryColor,
                            selectedLabelColor = Color.White,
                            containerColor = DarkSurfaceVariant,
                            labelColor = OnSurfaceColor
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            // ── Boyut Filtre Çipleri ─────────────────────────────────────
            LazyRow(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sizeFilters = listOf(10, 50, 100, 500)
                items(sizeFilters) { mb ->
                    FilterChip(
                        selected = state.minSizeMB == mb,
                        onClick = { viewModel.setMinSize(mb) },
                        label = {
                            Text(stringResource(R.string.files_min_size_chip, mb), fontSize = 13.sp)
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.files_summary_total, filteredFiles.size, formatFileSize(filteredTotalSize)),
                    color = OnSurfaceColor,
                    fontSize = 13.sp
                )
            }

            StorageAccessBanner(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

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

                filteredFiles.isEmpty() -> {
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
                                if (state.largeFiles.isEmpty()) stringResource(R.string.files_empty) else stringResource(R.string.files_empty_category),
                                color = OnSurfaceColor.copy(alpha = 0.5f),
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (state.largeFiles.isEmpty()) stringResource(R.string.files_empty_hint, state.minSizeMB) else stringResource(R.string.files_empty_category_hint, state.minSizeMB, stringResource(state.selectedCategory.labelRes)),
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
                            items = filteredFiles,
                            key = { _, file -> file.path }
                        ) { index, file ->
                            val nameLower = file.name.lowercase(Locale.ROOT)
                            val mimeLower = file.mimeType?.lowercase(Locale.ROOT) ?: ""
                            val isMedia = mimeLower.startsWith("image/") || mimeLower.startsWith("video/") ||
                                    nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") ||
                                    nameLower.endsWith(".webp") || nameLower.endsWith(".heic") || nameLower.endsWith(".mp4") ||
                                    nameLower.endsWith(".mkv") || nameLower.endsWith(".avi") || nameLower.endsWith(".mov")

                            FileListItem(
                                file = file,
                                isSelected = file.path in state.selectedPaths,
                                isAlternate = index % 2 == 1,
                                isMedia = isMedia,
                                onClick = {
                                    if (isMedia) {
                                        val mediaIndex = mediaFiles.indexOf(file)
                                        if (mediaIndex != -1) {
                                            previewIndex = mediaIndex
                                        }
                                    } else {
                                        openFileSafely(context, file.path, file.mimeType)
                                    }
                                },
                                onToggleSelect = { viewModel.toggleSelection(file.path) },
                                onPreview = if (isMedia) {
                                    {
                                        val mediaIndex = mediaFiles.indexOf(file)
                                        if (mediaIndex != -1) {
                                            previewIndex = mediaIndex
                                        }
                                    }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Silme Onay Dialogu ───────────────────────────────────────────────────
    if (showProLimitDialog) {
        com.storagemanager.ui.pro.ProLimitDialog(
            limit = ProViewModel.FREE_BULK_LIMIT,
            onUnlock = {
                showProLimitDialog = false
                navController.navigate(com.storagemanager.ui.navigation.Screen.Paywall.route)
            },
            onDismiss = { showProLimitDialog = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = DarkSurface,
            titleContentColor = Color.White,
            textContentColor = OnSurfaceColor,
            title = {
                Text(stringResource(R.string.files_delete_title), fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    stringResource(R.string.files_delete_message, selectedCount, formatFileSize(selectedTotalSize)),
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
                    Text(stringResource(R.string.delete), color = ErrorColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel), color = OnSurfaceColor)
                }
            }
        )
    }

    // ── Silme İlerleme Dialogu ───────────────────────────────────────────────
    if (state.isDeleting) {
        AlertDialog(
            onDismissRequest = { /* Silme sırasında kapatılamaz */ },
            containerColor = DarkSurface,
            title = {
                Text(
                    stringResource(R.string.files_deleting_short),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = PrimaryColor,
                        strokeWidth = 3.dp
                    )
                    Text(
                        stringResource(R.string.files_deleting),
                        color = OnSurfaceColor,
                        fontSize = 14.sp
                    )
                }
            },
            confirmButton = {}
        )
    }

    // ── Medya Önizleme Dialogu ────────────────────────────────────────────────
    previewIndex?.let { index ->
        if (index in mediaFiles.indices) {
            val file = mediaFiles[index]
            val nameLower = file.name.lowercase(Locale.ROOT)
            val mimeLower = file.mimeType?.lowercase(Locale.ROOT) ?: ""
            val isVideo = mimeLower.startsWith("video/") ||
                    nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") || nameLower.endsWith(".avi") ||
                    nameLower.endsWith(".mov") || nameLower.endsWith(".3gp") || nameLower.endsWith(".webm")

            MediaPreviewDialog(
                item = MediaItemPreview(
                    uriOrPath = file.path,
                    title = file.name,
                    subtitle = stringResource(R.string.meta_separator, formatFileSize(file.size), formatDate(LocalContext.current, file.lastModified)),
                    isVideo = isVideo,
                    isSelected = file.path in state.selectedPaths,
                    onToggleSelect = { viewModel.toggleSelection(file.path) },
                    onDelete = {
                        val pathToDelete = file.path
                        previewIndex = null
                        viewModel.deleteSingleFile(pathToDelete)
                    }
                ),
                onDismiss = { previewIndex = null },
                onPrevious = if (index > 0) { { previewIndex = index - 1 } } else null,
                onNext = if (index < mediaFiles.size - 1) { { previewIndex = index + 1 } } else null
            )
        }
    }
}

@Composable
private fun FileListItem(
    file: LargeFile,
    isSelected: Boolean,
    isAlternate: Boolean,
    isMedia: Boolean,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onPreview: (() -> Unit)? = null
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
            Box(
                modifier = Modifier.clickable { onToggleSelect() }
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = null,
                    tint = if (isSelected) PrimaryColor else OnSurfaceColor.copy(alpha = 0.4f),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            // Dosya tipi ikonu veya Medya Önizleme Küçük Resmi (Thumbnail)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconColor.copy(alpha = 0.15f))
                    .then(if (onPreview != null) Modifier.clickable { onPreview() } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                if (isMedia) {
                    AsyncImage(
                        model = java.io.File(file.path),
                        contentDescription = file.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    val mimeLower = file.mimeType?.lowercase(Locale.ROOT) ?: ""
                    val nameLower = file.name.lowercase(Locale.ROOT)
                    val isVideo = mimeLower.startsWith("video/") ||
                            nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") || nameLower.endsWith(".avi") ||
                            nameLower.endsWith(".mov") || nameLower.endsWith(".3gp") || nameLower.endsWith(".webm")

                    if (isVideo) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.play),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                } else {
                    Icon(
                        getFileIcon(file.mimeType),
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
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
                Spacer(Modifier.height(2.dp))
                Text(
                    file.path,
                    color = OnSurfaceColor.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    formatDate(LocalContext.current, file.lastModified),
                    color = OnSurfaceColor.copy(alpha = 0.35f),
                    fontSize = 10.sp
                )
            }

            Spacer(Modifier.width(8.dp))

            // Dosya boyutu (belirgin)
            Text(
                formatFileSize(file.size),
                color = SecondaryColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}
