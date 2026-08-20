package com.storagemanager.ui.apps

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.storagemanager.domain.model.UnusedApp
import java.util.concurrent.TimeUnit
import androidx.compose.ui.res.stringResource
import com.storagemanager.R
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


private fun daysSince(timestamp: Long): Long {
    if (timestamp <= 0L || timestamp < 86400000L) return -1L
    val diff = System.currentTimeMillis() - timestamp
    return TimeUnit.MILLISECONDS.toDays(diff)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    navController: NavController,
    viewModel: AppsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSortMenu by remember { mutableStateOf(false) }

    // Kullanıcı kaldırma arayüzünden her geri geldiğinde kuyruktaki sıradaki işlemi yap/listeyi güncelle
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAfterUninstall()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val totalSize = state.unusedApps.sumOf { it.appSize + it.cacheSize }
    val selectedCount = state.selectedPackages.size

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.apps_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
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
                                AppSortBy.UNUSED_FIRST to stringResource(R.string.sort_unused_first),
                                AppSortBy.SIZE_DESC to stringResource(R.string.sort_size_desc),
                                AppSortBy.SIZE_ASC to stringResource(R.string.sort_size_asc),
                                AppSortBy.NAME_ASC to stringResource(R.string.sort_app_name_asc)
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
        bottomBar = {
            if (state.unusedApps.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                stringResource(R.string.apps_count, state.unusedApps.size),
                                color = OnSurfaceColor,
                                fontSize = 13.sp
                            )
                            Text(
                                formatFileSize(totalSize),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.startUninstallQueue(state.selectedPackages.toList())
                            },
                            enabled = selectedCount > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ErrorColor,
                                contentColor = Color.White,
                                disabledContainerColor = ErrorColor.copy(alpha = 0.3f),
                                disabledContentColor = Color.White.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (selectedCount > 0) stringResource(R.string.apps_uninstall_selected_count, selectedCount) else stringResource(R.string.apps_uninstall_selected),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Filtre Çipleri ────────────────────────────────────────────
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(30, 60, 90)
                items(filters) { days ->
                    FilterChip(
                        selected = state.dayFilter == days,
                        onClick = { viewModel.setDayFilter(days) },
                        label = {
                            Text(stringResource(R.string.days_format, days), fontSize = 13.sp)
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

            // ── Özet Çubuğu ──────────────────────────────────────────────
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
                    stringResource(R.string.apps_summary, state.unusedApps.size, formatFileSize(totalSize)),
                    color = OnSurfaceColor,
                    fontSize = 13.sp
                )
            }

            // ── İçerik ──────────────────────────────────────────────────
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("loading_spinner_container"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("loading_spinner"),
                                color = PrimaryColor,
                                strokeWidth = 4.dp
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.apps_analyzing),
                                color = OnSurfaceColor.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                state.unusedApps.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = SuccessColor.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.apps_congrats),
                                color = SuccessColor,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.apps_empty),
                                color = OnSurfaceColor.copy(alpha = 0.5f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { viewModel.setDayFilter(30) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryColor,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.apps_reset_filter), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = state.unusedApps,
                            key = { it.packageName }
                        ) { app ->
                            AppListItem(
                                app = app,
                                isSelected = app.packageName in state.selectedPackages,
                                onToggleSelection = { viewModel.toggleSelection(app.packageName) },
                                onUninstall = { viewModel.startUninstallQueue(listOf(app.packageName)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

fun launchApp(context: android.content.Context, packageName: String) {
    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    } catch (_: Exception) {}
}

@Composable
private fun AppListItem(
    app: UnusedApp,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onUninstall: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PrimaryColor.copy(alpha = 0.15f) else DarkSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Seçim kutusu
            IconButton(
                onClick = onToggleSelection,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (isSelected) stringResource(R.string.selected) else stringResource(R.string.not_selected),
                    tint = if (isSelected) PrimaryColor else OnSurfaceColor.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            // Uygulama ikonu (Dokunarak aç)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(PrimaryColor.copy(alpha = 0.3f), SecondaryColor.copy(alpha = 0.3f))
                        )
                    )
                    .clickable { launchApp(context, app.packageName) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PhoneAndroid,
                    contentDescription = stringResource(R.string.open),
                    tint = PrimaryColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            // Uygulama bilgileri (Dokunarak aç)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { launchApp(context, app.packageName) }
            ) {
                Text(
                    app.appName,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    stringResource(R.string.apps_size_with_cache, formatFileSize(app.appSize), formatFileSize(app.cacheSize)),
                    color = OnSurfaceColor.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
                val days = daysSince(app.lastUsed)
                Text(
                    text = if (days == -1L) stringResource(R.string.apps_never_used_tap) else stringResource(R.string.apps_last_used_tap, days.toInt()),
                    color = ErrorColor.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.width(6.dp))

            // Aksiyon Butonları (Aç & Kaldır)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { launchApp(context, app.packageName) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.apps_open_app),
                        tint = PrimaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(4.dp))

                Button(
                    onClick = onUninstall,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ErrorColor.copy(alpha = 0.2f),
                        contentColor = ErrorColor
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.apps_uninstall), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
