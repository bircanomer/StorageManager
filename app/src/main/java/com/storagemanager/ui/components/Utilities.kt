package com.storagemanager.ui.components

import android.content.Context
import androidx.core.text.BidiFormatter
import com.storagemanager.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Sayı + birim gibi soldan sağa yazılan parçaları çevreleyen metnin yönünden yalıtır.
 *
 * Arapça gibi sağdan sola dillerde "15,1 GB" yalıtılmazsa çift yönlü metin algoritması
 * parçayı ters çevirip ekranda "GB 15,1" olarak gösterir. [BidiFormatter] görünmez
 * yalıtım karakterleri ekleyerek bunu engeller.
 */
fun bidiIsolate(text: String): String =
    BidiFormatter.getInstance().unicodeWrap(text)

/**
 * Byte değerini okunabilir dosya boyutuna çevirir (1 ondalık).
 * Örnek: 1_536_000 → "1,5 MB"
 *
 * Sonuç RTL dillerde de doğru sırada görünmesi için yalıtılır.
 */
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return bidiIsolate("0 B")
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val formatted = if (unitIndex == 0) {
        "${value.toLong()} ${units[unitIndex]}"
    } else {
        String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
    }
    return bidiIsolate(formatted)
}

/**
 * Timestamp'i kullanıcının dilinde göreli zaman ifadesine çevirir.
 * Örnek: "3 days ago", "1 week ago", "just now"
 */
fun formatRelativeTime(context: Context, timestamp: Long): String {
    if (timestamp <= 0L || timestamp < 86400000L) return context.getString(R.string.time_never_used)

    val now = System.currentTimeMillis()
    val diff = now - timestamp

    if (diff < 0) return context.getString(R.string.time_just_now)

    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    return when {
        minutes < 1 -> context.getString(R.string.time_just_now)
        minutes < 60 -> context.getString(R.string.time_minutes_ago, minutes.toInt())
        hours < 24 -> context.getString(R.string.time_hours_ago, hours.toInt())
        days < 2 -> context.getString(R.string.time_yesterday)
        days < 7 -> context.getString(R.string.time_days_ago, days.toInt())
        days < 30 -> context.getString(R.string.time_weeks_ago, (days / 7).toInt())
        days < 365 -> context.getString(R.string.time_months_ago, (days / 30).toInt())
        else -> context.getString(R.string.time_years_ago, (days / 365).toInt())
    }
}

/**
 * Timestamp'i kullanıcının yereline göre tarihe çevirir (ör. "14 Jul 2026" / "14 Tem 2026").
 */
fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
