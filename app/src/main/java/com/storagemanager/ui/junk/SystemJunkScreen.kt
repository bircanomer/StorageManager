package com.storagemanager.ui.junk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.storagemanager.domain.model.SystemJunk
import com.storagemanager.ui.components.GradientButton
import com.storagemanager.ui.components.formatFileSize
import com.storagemanager.ui.theme.*
import androidx.compose.ui.res.stringResource
import com.storagemanager.R
import com.storagemanager.ui.components.StorageAccessBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemJunkScreen(
    navController: NavController,
    viewModel: SystemJunkViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.junk_title), color = OnBackground, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { innerPadding ->
        when (val uiState = state) {
            is SystemJunkState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.junk_scanning), color = OnSurface.copy(alpha = 0.7f))
                    }
                }
            }

            is SystemJunkState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.error_with_message, uiState.message), color = Error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadSystemJunk() },
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }

            is SystemJunkState.Success -> {
                val junks = uiState.junks
                val totalSize = junks.sumOf { it.size }

                if (junks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            StorageAccessBanner(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                            Icon(
                                imageVector = Icons.Filled.CleaningServices,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Success
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.junk_all_clean),
                                style = MaterialTheme.typography.titleMedium,
                                color = OnSurface.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadSystemJunk() },
                                colors = ButtonDefaults.buttonColors(containerColor = Primary)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.rescan))
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Surface),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(stringResource(R.string.junk_cleanable), style = MaterialTheme.typography.labelMedium, color = OnSurface.copy(alpha = 0.6f))
                                            Text(formatFileSize(totalSize), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Primary)
                                        }
                                        Text(stringResource(R.string.item_count, junks.size), style = MaterialTheme.typography.titleMedium, color = OnSurface)
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    GradientButton(
                                        text = stringResource(R.string.junk_clean_all, junks.size),
                                        onClick = { viewModel.cleanAllSystemJunk() }
                                    )
                                }
                            }
                        }

                        items(junks, key = { it.path }) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Surface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.type.emoji, style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = OnBackground)
                                        Text(stringResource(R.string.meta_separator, stringResource(item.type.displayNameRes), item.path), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(alpha = 0.5f), maxLines = 1)
                                    }
                                    if (item.size > 0) {
                                        Text(formatFileSize(item.size), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
