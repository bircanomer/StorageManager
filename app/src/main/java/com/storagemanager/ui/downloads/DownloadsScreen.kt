package com.storagemanager.ui.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.storagemanager.domain.model.DownloadJunk
import com.storagemanager.domain.model.DuplicateDocumentGroup
import com.storagemanager.ui.components.formatFileSize
import com.storagemanager.ui.components.openFileSafely
import com.storagemanager.ui.components.StorageAccessBanner
import com.storagemanager.ui.theme.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.storagemanager.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    navController: NavController,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Duplike Belgeler, 1: İndirilenler
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title), color = OnBackground, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = OnBackground)
                    }
                },
                actions = {
                    if (state is DownloadsState.Success) {
                        val currentSort = (state as DownloadsState.Success).sortBy
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = stringResource(R.string.sort),
                                    tint = OnBackground
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier.background(Surface)
                            ) {
                                val sortOptions = listOf(
                                    DownloadsSortBy.SIZE_DESC to stringResource(R.string.sort_size_desc),
                                    DownloadsSortBy.SIZE_ASC to stringResource(R.string.sort_size_asc),
                                    DownloadsSortBy.DATE_DESC to stringResource(R.string.sort_date_desc),
                                    DownloadsSortBy.DATE_ASC to stringResource(R.string.sort_date_asc),
                                    DownloadsSortBy.NAME_ASC to stringResource(R.string.sort_name_asc)
                                )
                                sortOptions.forEach { (sortBy, label) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                label,
                                                color = if (currentSort == sortBy) Primary else OnSurface,
                                                fontWeight = if (currentSort == sortBy) FontWeight.Bold else FontWeight.Normal
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
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Background,
                contentColor = Primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.downloads_duplicates_tab), fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.downloads_tab), fontWeight = FontWeight.Bold) }
                )
            }

            StorageAccessBanner(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            when (val uiState = state) {
                is DownloadsState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                }

                is DownloadsState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.error_with_message, uiState.message), color = Error)
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadData() },
                                colors = ButtonDefaults.buttonColors(containerColor = Primary)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }

                is DownloadsState.Success -> {
                    if (selectedTab == 0) {
                        DuplicateDocsList(
                            groups = uiState.duplicateDocs,
                            onDeleteFile = { viewModel.moveToTrash(it) },
                            onCleanAllCopies = { viewModel.deleteAllDuplicateCopies() }
                        )
                    } else {
                        DownloadsJunkList(
                            junks = uiState.downloadJunks,
                            onDeleteFile = { viewModel.moveToTrash(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateDocsList(
    groups: List<DuplicateDocumentGroup>,
    onDeleteFile: (String) -> Unit,
    onCleanAllCopies: () -> Unit
) {
    val context = LocalContext.current
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (groups.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.downloads_no_duplicates), color = OnSurface.copy(alpha = 0.6f))
        }
    } else {
        val totalGroups = groups.size
        val totalFiles = groups.sumOf { it.files.size }
        val duplicateCopies = groups.sumOf { it.files.size - 1 }
        val cleanableSize = groups.sumOf { it.files.drop(1).sumOf { f -> f.size } }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.downloads_summary_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.downloads_summary_groups, totalFiles, totalGroups),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnBackground
                        )
                        Text(
                            text = stringResource(R.string.downloads_summary_cleanable, duplicateCopies, formatFileSize(cleanableSize)),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurface.copy(alpha = 0.8f)
                        )
                        if (duplicateCopies > 0) {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { showConfirmDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Error),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.downloads_delete_copies, formatFileSize(cleanableSize)), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            items(groups, key = { it.groupId }) { group ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.FileCopy, contentDescription = null, tint = Primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.downloads_group_title, group.files.size), fontWeight = FontWeight.Bold, color = OnBackground)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        group.files.forEach { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openFileSafely(context, file.path, file.mimeType) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(file.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = OnBackground)
                                    Text(
                                        text = file.path,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OnSurface.copy(alpha = 0.5f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(stringResource(R.string.downloads_item_meta, formatFileSize(file.size)), style = MaterialTheme.typography.labelSmall, color = Primary.copy(alpha = 0.8f))
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { openFileSafely(context, file.path, file.mimeType) }) {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.open), tint = Primary)
                                    }
                                    IconButton(onClick = { onDeleteFile(file.path) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.downloads_move_to_trash), tint = Error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                containerColor = Surface,
                title = { Text(stringResource(R.string.downloads_delete_title), color = OnBackground, fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.downloads_delete_message), color = OnSurface) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onCleanAllCopies()
                            showConfirmDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.delete), color = Error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text(stringResource(R.string.cancel), color = OnSurface.copy(alpha = 0.6f))
                    }
                }
            )
        }
    }
}

@Composable
private fun DownloadsJunkList(
    junks: List<DownloadJunk>,
    onDeleteFile: (String) -> Unit
) {
    val context = LocalContext.current

    if (junks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.downloads_no_junk), color = OnSurface.copy(alpha = 0.6f))
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(junks, key = { it.path }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openFileSafely(context, item.path, null) },
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Download, contentDescription = null, tint = Secondary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = OnBackground)
                                Text(
                                    text = item.path,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnSurface.copy(alpha = 0.5f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(stringResource(R.string.downloads_item_meta_category, item.category, formatFileSize(item.size)), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(alpha = 0.4f))
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { openFileSafely(context, item.path, null) }) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.open), tint = Primary)
                            }
                            IconButton(onClick = { onDeleteFile(item.path) }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.downloads_move_to_trash), tint = Error)
                            }
                        }
                    }
                }
            }
        }
    }
}
