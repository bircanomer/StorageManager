package com.storagemanager.ui.components

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.storagemanager.R
import com.storagemanager.ui.permissions.PermissionUtils
import com.storagemanager.ui.theme.OnSurface
import com.storagemanager.ui.theme.OnBackground
import com.storagemanager.ui.theme.Primary
import com.storagemanager.ui.theme.Surface
import com.storagemanager.ui.theme.Warning

/**
 * Tarama için gereken erişim eksikse kullanıcıyı uyaran ve izni oradan istemeyi
 * sağlayan kart.
 *
 * Tarama ekranları izin yokken boş sonuç gösteriyordu; kullanıcı "hiçbir şey
 * bulunamadı" ile "izin verilmedi" arasındaki farkı göremiyordu. Kart yalnızca
 * eksik izin varken görünür, izinler tamamsa hiçbir şey çizmez.
 *
 * İzinler sistem ayarlarından da değiştirilebildiği için durum her ON_RESUME'da
 * yeniden okunur.
 */
@Composable
fun StorageAccessBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var mediaGranted by remember { mutableStateOf(PermissionUtils.hasMediaPermission(context)) }
    var allFilesGranted by remember { mutableStateOf(PermissionUtils.hasAllFilesAccess()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        mediaGranted = PermissionUtils.hasMediaPermission(context)
        // Sistem diyaloğu hiç görünmediyse (kalıcı ret) kullanıcıyı uygulama
        // ayarlarına yönlendirmekten başka yol yok.
        if (!mediaGranted && !canAskAgain(context)) {
            PermissionUtils.openAppSettings(context)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mediaGranted = PermissionUtils.hasMediaPermission(context)
                allFilesGranted = PermissionUtils.hasAllFilesAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (mediaGranted && allFilesGranted) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!mediaGranted) {
                AccessRow(
                    icon = Icons.Filled.Warning,
                    tint = Warning,
                    title = stringResource(R.string.permission_required),
                    description = stringResource(R.string.storage_permission_rationale),
                    onGrant = {
                        val missing = PermissionUtils.missingRuntimePermissions(context)
                        if (missing.isEmpty()) {
                            mediaGranted = PermissionUtils.hasMediaPermission(context)
                        } else {
                            permissionLauncher.launch(missing)
                        }
                    }
                )
            }

            if (!mediaGranted && !allFilesGranted) {
                HorizontalDivider(color = OnSurface.copy(alpha = 0.12f))
            }

            if (!allFilesGranted) {
                AccessRow(
                    icon = Icons.Filled.Folder,
                    tint = Primary,
                    title = stringResource(R.string.permission_all_files),
                    description = stringResource(R.string.permission_all_files_desc),
                    onGrant = { PermissionUtils.openAllFilesAccessSettings(context) }
                )
            }
        }
    }
}

@Composable
private fun AccessRow(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    description: String,
    onGrant: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnBackground
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface.copy(alpha = 0.7f)
                )
            }
        }
        Button(
            onClick = onGrant,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Text(stringResource(R.string.grant_permission), fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Sistem izin diyaloğu hâlâ gösterilebiliyor mu? Kalıcı olarak reddedilen bir izin
 * için hem izin verilmemiş hem de gerekçe gösterilmemiş olur.
 */
private fun canAskAgain(context: Context): Boolean {
    val activity = context.findActivity() ?: return true
    return PermissionUtils.missingRuntimePermissions(context).any {
        ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
