package com.storagemanager.ui.treemap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.storagemanager.domain.model.StorageTreeNode
import com.storagemanager.ui.components.formatFileSize
import com.storagemanager.ui.components.openFileSafely
import com.storagemanager.ui.components.StorageAccessBanner
import kotlinx.coroutines.launch
import com.storagemanager.ui.theme.*
import androidx.compose.ui.res.stringResource
import com.storagemanager.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageTreemapScreen(
    navController: NavController,
    viewModel: StorageTreemapViewModel = hiltViewModel(),
    proViewModel: com.storagemanager.ui.pro.ProViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isPro by proViewModel.isPro.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val openFailedMessage = stringResource(R.string.file_open_failed)

    // Klasörler haritada bir alt seviyeye iner, dosyalar sistem uygulamasında açılır.
    val onNodeClick: (StorageTreeNode) -> Unit = { node ->
        if (node.isDirectory) {
            viewModel.selectNode(node)
        } else if (!openFileSafely(context, node.path)) {
            scope.launch { snackbarHostState.showSnackbar(openFailedMessage) }
        }
    }

    Scaffold(
        containerColor = Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.treemap_title), color = OnBackground, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!viewModel.navigateUp()) {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = OnBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadTreemap() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.rescan), tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { innerPadding ->
        if (!isPro) {
            com.storagemanager.ui.pro.ProLockedScreen(
                featureText = stringResource(R.string.pro_locked_treemap),
                onUnlock = { navController.navigate(com.storagemanager.ui.navigation.Screen.Paywall.route) },
                modifier = Modifier.padding(innerPadding)
            )
            return@Scaffold
        }

        when (val uiState = state) {
            is TreemapState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.treemap_calculating), color = OnSurface.copy(alpha = 0.7f))
                    }
                }
            }

            is TreemapState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.error_with_message, uiState.message), color = Error)
                }
            }

            is TreemapState.Success -> {
                val currentNode = uiState.currentNode

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        StorageAccessBanner()
                    }

                    // Current Directory Info Card
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
                                    Text(currentNode.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = OnBackground)
                                    Text(currentNode.path, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(alpha = 0.5f))
                                }
                                Text(formatFileSize(currentNode.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
                            }
                        }
                    }

                    // Visual Treemap Chart
                    item {
                        Text(stringResource(R.string.treemap_blocks), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OnBackground)
                        Spacer(modifier = Modifier.height(8.dp))
                        StorageTreemapChart(
                            node = currentNode,
                            onNodeClick = onNodeClick
                        )
                    }

                    // Sub-folder list
                    item {
                        Text(stringResource(R.string.treemap_folder_details), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OnBackground)
                    }

                    items(currentNode.children) { child ->
                        Card(
                            onClick = { onNodeClick(child) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = if (child.isDirectory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                        contentDescription = null,
                                        tint = if (child.isDirectory) Primary else Secondary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(child.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = OnBackground)
                                        val percent = (child.percentageOfParent * 100).toInt()
                                        Text(
                                            text = if (child.isDirectory) {
                                                stringResource(R.string.percent_format, percent)
                                            } else {
                                                stringResource(R.string.treemap_file_meta, percent)
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OnSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                                Text(formatFileSize(child.size), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
