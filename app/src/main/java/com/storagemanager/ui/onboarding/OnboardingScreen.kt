package com.storagemanager.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.storagemanager.ui.components.GradientButton
import com.storagemanager.ui.permissions.PermissionUtils
import com.storagemanager.ui.theme.*
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.storagemanager.R

@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
    viewModel: OnboardingViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    var visible by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // İzinler artık uygulama açılışında değil, kullanıcı "Başlayalım" dediğinde isteniyor.
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.markCompleted(onGetStarted)
    }

    LaunchedEffect(Unit) {
        delay(150)
        visible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Background,
                        Surface.copy(alpha = 0.5f),
                        Background
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Hero Icon ───────────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(800)) +
                        slideInVertically(
                            initialOffsetY = { -60 },
                            animationSpec = tween(800)
                        )
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Primary, Secondary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = OnPrimary,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // ── Title ───────────────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(800, delayMillis = 200)) +
                        slideInVertically(
                            initialOffsetY = { 40 },
                            animationSpec = tween(800, delayMillis = 200)
                        )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.onboarding_title),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = OnBackground,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.onboarding_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // ── Feature List ────────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(800, delayMillis = 400)) +
                        slideInVertically(
                            initialOffsetY = { 50 },
                            animationSpec = tween(800, delayMillis = 400)
                        )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FeatureRow(
                        icon = Icons.Outlined.PhotoLibrary,
                        text = stringResource(R.string.onboarding_feature_photos),
                        iconTint = PhotoColor
                    )
                    FeatureRow(
                        icon = Icons.Outlined.Widgets,
                        text = stringResource(R.string.onboarding_feature_apps),
                        iconTint = AppColor
                    )
                    FeatureRow(
                        icon = Icons.Outlined.Cached,
                        text = stringResource(R.string.onboarding_feature_cache),
                        iconTint = CacheColor
                    )
                    FeatureRow(
                        icon = Icons.Outlined.FolderOpen,
                        text = stringResource(R.string.onboarding_feature_files),
                        iconTint = VideoColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // ── İzin bilgisi + CTA ──────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(600, delayMillis = 700)) +
                        slideInVertically(
                            initialOffsetY = { 30 },
                            animationSpec = tween(600, delayMillis = 700)
                        )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.onboarding_privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    GradientButton(
                        text = stringResource(R.string.get_started),
                        onClick = {
                            val missing = PermissionUtils.missingRuntimePermissions(context)
                            if (missing.isEmpty()) {
                                viewModel.markCompleted(onGetStarted)
                            } else {
                                permissionLauncher.launch(missing)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Özel erişim izinleri isteğe bağlıdır — Ayarlar ekranından da verilebilir.
                    Text(
                        text = stringResource(R.string.onboarding_permissions_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    text: String,
    iconTint: androidx.compose.ui.graphics.Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = OnBackground.copy(alpha = 0.9f)
        )
    }
}
