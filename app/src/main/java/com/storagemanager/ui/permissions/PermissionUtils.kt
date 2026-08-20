package com.storagemanager.ui.permissions

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * İzin durumu sorgulama ve ilgili ayar ekranlarını açma yardımcıları.
 *
 * Özel erişim izinleri (kullanım istatistikleri, tüm dosyalara erişim) artık uygulama
 * açılışında otomatik olarak tetiklenmiyor; kullanıcı bunları onboarding veya Ayarlar
 * ekranından bilinçli olarak veriyor.
 */
object PermissionUtils {

    private const val TAG = "PermissionUtils"

    /** Tarama için gereken çalışma zamanı izinleri. */
    fun runtimePermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun missingRuntimePermissions(context: Context): Array<String> =
        runtimePermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

    fun hasMediaPermission(context: Context): Boolean {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, required) == PackageManager.PERMISSION_GRANTED
    }

    /** Uygulama kullanım istatistikleri izni (kullanılmayan uygulama tespiti için). */
    @Suppress("DEPRECATION")
    fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.w(TAG, "Kullanım istatistiği izni sorgulanamadı", e)
            false
        }
    }

    /** Tüm dosyalara erişim izni (büyük dosya ve sistem çöpü taraması için). */
    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    /** Kalıcı olarak reddedilen izinler yalnızca uygulama ayarlarından verilebilir. */
    fun openAppSettings(context: Context) {
        launch(
            context,
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        )
    }

    fun openUsageAccessSettings(context: Context) {
        launch(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    fun openAllFilesAccessSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val specific = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        if (!launch(context, specific)) {
            launch(context, Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    private fun launch(context: Context, intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Ayar ekranı açılamadı: ${intent.action}", e)
        false
    }
}
