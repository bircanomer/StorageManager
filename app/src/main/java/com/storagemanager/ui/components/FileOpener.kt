package com.storagemanager.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/**
 * Dosyayı sistemdeki uygun uygulamayla açar.
 *
 * MIME türü verilmezse dosya uzantısından çıkarılır; uzantı tanınmazsa ya da
 * o türü açan uygulama yoksa "* / *" ile ikinci bir deneme yapılır (dosya
 * yöneticileri genelde bunu karşılar).
 *
 * @return Bir uygulama açıldıysa true; dosya yoksa veya açacak uygulama
 *         bulunamadıysa false. Çağıran taraf false durumunda kullanıcıya
 *         bilgi verebilir.
 */
fun openFileSafely(context: Context, filePath: String, mimeType: String? = null): Boolean {
    val file = File(filePath)
    if (!file.exists() || file.isDirectory) return false

    val uri: Uri = try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (_: Exception) {
        return false
    }

    val resolvedType = mimeType ?: run {
        val extension = MimeTypeMap.getFileExtensionFromUrl(file.absolutePath.replace(" ", "%20"))
        extension?.let {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.lowercase(Locale.ROOT))
        } ?: "*/*"
    }

    val candidateTypes = if (resolvedType == "*/*") listOf("*/*") else listOf(resolvedType, "*/*")
    for (type in candidateTypes) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, type)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            return true
        } catch (_: Exception) {
            // Bu türü açan uygulama yok — sıradaki türle dene.
        }
    }
    return false
}
