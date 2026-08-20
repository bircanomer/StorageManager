package com.storagemanager.ui.photos

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.automirrored.filled.Sort
import com.storagemanager.ui.photos.PhotosSortBy
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import com.storagemanager.ui.components.MediaItemPreview
import com.storagemanager.ui.components.MediaPreviewDialog
import androidx.compose.ui.res.stringResource
import com.storagemanager.R
import com.storagemanager.ui.pro.ProViewModel
import com.storagemanager.ui.components.formatFileSize

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

private fun formatPhotoDate(timestamp: Long): String {
    return try {
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
        sdf.format(java.util.Date(timestamp * 1000L))
    } catch (e: Exception) {
        ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    navController: NavController,
    viewModel: PhotosViewModel = hiltViewModel(),
    proViewModel: ProViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showProLimitDialog by remember { mutableStateOf(false) }
    val isPro by proViewModel.isPro.collectAsStateWithLifecycle()

    // Ücretsiz sürümde tek seferde silinebilecek öge sınırı
    fun requestDelete(count: Int) {
        if (!isPro && count > ProViewModel.FREE_BULK_LIMIT) {
            showProLimitDialog = true
        } else {
            showDeleteDialog = true
        }
    }
    var showSortMenu by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onPhotosDeletedSuccessfully()
        } else {
            viewModel.onPhotosDeleteCancelled()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.deleteIntentSender.collect { intentSender ->
            deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

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

    // Önizleme için fotoğraf → liste indeksi eşlemesi.
    // Eskiden her ızgara hücresinde currentPhotos.indexOf(photo) çağrılıyordu;
    // bu, kaydırma sırasında hücre başına O(N) tarama (toplam O(N²)) demekti.
    val photoIndexById = remember(currentPhotos) {
        currentPhotos.withIndex().associate { (index, photo) -> photo.id to index }
    }

    // Duplike grupları da her yeniden çizimde değil, liste değiştiğinde hesapla
    val duplicateGroups = remember(currentPhotos, state.selectedTab) {
        if (state.selectedTab == JunkType.DUPLICATE) {
            currentPhotos.groupBy { it.groupId ?: it.id.toString() }.values.toList()
        } else {
            emptyList()
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.photos_title),
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
                    if (state.selectedIds.isNotEmpty()) {
                        IconButton(onClick = { requestDelete(state.selectedIds.size) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = ErrorColor
                            )
                        }
                    }
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
                                PhotosSortBy.DATE_DESC to stringResource(R.string.sort_date_desc),
                                PhotosSortBy.DATE_ASC to stringResource(R.string.sort_date_asc),
                                PhotosSortBy.SIZE_DESC to stringResource(R.string.sort_size_desc),
                                PhotosSortBy.SIZE_ASC to stringResource(R.string.sort_size_asc)
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
                    onClick = { requestDelete(state.selectedIds.size) },
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = ErrorColor,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.photos_delete_count, state.selectedIds.size))
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
                    val tabLabel = stringResource(junkType.tabLabelRes)

                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { viewModel.setTab(junkType) },
                        text = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Text(
                                    tabLabel,
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (count > 0) "($count)" else "(0)",
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTabIndex == index) PrimaryColor else OnSurfaceColor.copy(alpha = 0.5f)
                                )
                            }
                        },
                        selectedContentColor = PrimaryColor,
                    )
                }
            }

            // ── Kategori Açıklaması ──────────────────────────────────────
            val description = stringResource(state.selectedTab.descriptionRes)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                    .background(
                        color = DarkSurfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = PrimaryColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = description,
                        color = OnSurfaceColor.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
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
                    stringResource(R.string.photos_summary, currentPhotos.size, formatFileSize(totalSizeCurrentTab)),
                    color = OnSurfaceColor,
                    fontSize = 13.sp
                )
                Row {
                    if (state.selectedTab == JunkType.DUPLICATE) {
                        TextButton(onClick = { viewModel.selectDuplicateCopies() }) {
                            Icon(
                                Icons.Default.SelectAll,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = SecondaryColor
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.photos_select_duplicates), color = SecondaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(onClick = { viewModel.selectAll() }) {
                            Icon(
                                Icons.Default.SelectAll,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = PrimaryColor
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.select_all), color = PrimaryColor, fontSize = 12.sp)
                        }
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
                            Text(stringResource(R.string.clear), color = OnSurfaceColor, fontSize = 12.sp)
                        }
                    }
                }
            }

            // ── İçerik ──────────────────────────────────────────────────
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(54.dp),
                                color = PrimaryColor,
                                strokeWidth = 4.dp
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.photos_analyzing),
                                color = OnSurfaceColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.please_wait),
                                color = OnSurfaceColor.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
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
                                stringResource(R.string.photos_empty_category),
                                color = OnSurfaceColor.copy(alpha = 0.5f),
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.photos_all_clean),
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
                        if (state.selectedTab == JunkType.DUPLICATE) {
                            duplicateGroups.forEachIndexed { groupIndex, groupPhotos ->
                                item(
                                    span = { GridItemSpan(2) },
                                    key = "group_header_$groupIndex"
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(SecondaryColor, CircleShape)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                stringResource(R.string.photos_group_title, groupIndex + 1, groupPhotos.size),
                                                color = SecondaryColor,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        if (groupPhotos.size > 1) {
                                            TextButton(
                                                onClick = { viewModel.selectGroupCopies(groupPhotos) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.SelectAll,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = SecondaryColor
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(stringResource(R.string.photos_select_duplicates), color = SecondaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                items(
                                    items = groupPhotos,
                                    key = { photo -> "${photo.junkType.name}_${photo.id}" }
                                ) { photo ->
                                    PhotoGridItem(
                                        photo = photo,
                                        isSelected = photo.id in state.selectedIds,
                                        showBlurScore = false,
                                        groupLabel = stringResource(R.string.photos_group_short, groupIndex + 1),
                                        onClick = { previewIndex = photoIndexById[photo.id] },
                                        onToggleSelect = { viewModel.toggleSelection(photo.id) }
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = currentPhotos,
                                key = { _, photo -> "${photo.junkType.name}_${photo.id}" }
                            ) { index, photo ->
                                PhotoGridItem(
                                    photo = photo,
                                    isSelected = photo.id in state.selectedIds,
                                    showBlurScore = state.selectedTab == JunkType.BLURRY,
                                    groupLabel = null,
                                    onClick = { previewIndex = index },
                                    onToggleSelect = { viewModel.toggleSelection(photo.id) }
                                )
                            }
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
                Text(stringResource(R.string.photos_delete_title), fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    stringResource(R.string.photos_delete_message, state.selectedIds.size, formatFileSize(totalSize)),
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

    // ── Medya Önizleme Dialogu ────────────────────────────────────────────────
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
                        stringResource(R.string.photos_deleting),
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
        if (index in currentPhotos.indices) {
            val photo = currentPhotos[index]
            MediaPreviewDialog(
                item = MediaItemPreview(
                    uriOrPath = photo.uri,
                    title = photo.name,
                    subtitle = "${formatFileSize(photo.size)}${if (photo.width > 0 && photo.height > 0) " • ${photo.width}x${photo.height}" else ""}",
                    isVideo = false,
                    isSelected = photo.id in state.selectedIds,
                    onToggleSelect = { viewModel.toggleSelection(photo.id) },
                    onDelete = {
                        viewModel.deleteSinglePhoto(photo)
                        previewIndex = null
                    }
                ),
                onDismiss = { previewIndex = null },
                onPrevious = if (index > 0) { { previewIndex = index - 1 } } else null,
                onNext = if (index < currentPhotos.size - 1) { { previewIndex = index + 1 } } else null
            )
        }
    }
}

@Composable
private fun PhotoGridItem(
    photo: JunkPhoto,
    isSelected: Boolean,
    showBlurScore: Boolean,
    groupLabel: String? = null,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit
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
            AsyncImage(
                model = java.io.File(photo.path),
                contentDescription = photo.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Grup Etiketi – sol üst
            if (groupLabel != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(
                            color = SecondaryColor.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        groupLabel,
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Seçim göstergesi – sağ üst
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clickable { onToggleSelect() }
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (isSelected) stringResource(R.string.selected) else stringResource(R.string.not_selected),
                    tint = if (isSelected) PrimaryColor else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(26.dp)
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

                    // Tarih
                    Text(
                        formatPhotoDate(photo.dateModified),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp
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
