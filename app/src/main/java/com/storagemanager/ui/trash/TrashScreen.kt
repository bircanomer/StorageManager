package com.storagemanager.ui.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.storagemanager.domain.model.TrashItem
import com.storagemanager.ui.components.formatFileSize
import com.storagemanager.ui.theme.*
import androidx.compose.ui.res.stringResource
import com.storagemanager.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    navController: NavController,
    viewModel: TrashViewModel = hiltViewModel()
) {
    val trashItems by viewModel.trashItems.collectAsStateWithLifecycle()
    val totalSize = trashItems.sumOf { it.fileSize }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trash_title), color = OnBackground, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = OnBackground)
                    }
                },
                actions = {
                    if (trashItems.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearAllTrash() }) {
                            Text(stringResource(R.string.trash_empty_action), color = Error, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { innerPadding ->
        if (trashItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = OnSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.trash_is_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurface.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(stringResource(R.string.trash_total), style = MaterialTheme.typography.labelMedium, color = OnSurface.copy(alpha = 0.6f))
                                Text(formatFileSize(totalSize), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Primary)
                            }
                            Text(stringResource(R.string.item_count, trashItems.size), style = MaterialTheme.typography.titleMedium, color = OnSurface)
                        }
                    }
                }

                items(trashItems, key = { it.id }) { item ->
                    TrashItemCard(
                        item = item,
                        onRestore = { viewModel.restoreItem(item.id) },
                        onDeletePermanently = { viewModel.deletePermanently(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashItemCard(
    item: TrashItem,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = OnBackground,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.meta_separator, formatFileSize(item.fileSize), item.originalPath),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurface.copy(alpha = 0.5f),
                    maxLines = 1
                )
            }

            Row {
                IconButton(onClick = onRestore) {
                    Icon(Icons.Filled.Restore, contentDescription = stringResource(R.string.trash_restore), tint = Success)
                }
                IconButton(onClick = onDeletePermanently) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = stringResource(R.string.trash_delete_forever), tint = Error)
                }
            }
        }
    }
}
