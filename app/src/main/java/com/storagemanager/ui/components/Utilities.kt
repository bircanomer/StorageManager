package com.storagemanager.ui.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Byte değerini okunabilir dosya boyutuna çevirir (1 ondalık).
 * Örnek: 1_536_000 → "1.5 MB"
 */
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${value.toLong()} ${units[unitIndex]}"
    } else {
        "%.1f %s".format(value, units[unitIndex])
    }
}

/**
 * Timestamp'i Türkçe göreli zaman ifadesine çevirir.
 * Örnek: "3 gün önce", "1 hafta önce", "az önce"
 */
fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    if (diff < 0) return "az önce"

    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    return when {
        minutes < 1 -> "az önce"
        minutes < 60 -> "$minutes dakika önce"
        hours < 24 -> "$hours saat önce"
        days < 2 -> "dün"
        days < 7 -> "$days gün önce"
        days < 30 -> "${days / 7} hafta önce"
        days < 365 -> "${days / 30} ay önce"
        else -> "${days / 365} yıl önce"
    }
}

/**
 * Timestamp'i "14 Tem 2026" formatında tarihe çevirir.
 */
fun formatDate(timestamp: Long): String {
    val locale = Locale("tr", "TR")
    val sdf = SimpleDateFormat("dd MMM yyyy", locale)
    return sdf.format(Date(timestamp))
}
