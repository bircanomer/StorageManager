package com.storagemanager.ui.components

import java.text.Collator
import java.util.Locale

/**
 * Kullanıcının diline göre ada göre sıralama karşılaştırıcısı.
 *
 * `lowercase()` ile sıralama yanlıştır: Türkçe'de "ı/i", Almanca'da "ä", İsveççe'de "å"
 * gibi harfler ASCII sırasına göre yanlış yere düşer. [Collator] her dilde o dilin
 * alfabetik sırasını uygular ve büyük/küçük harf farkını yok sayar.
 */
fun localeCollator(locale: Locale = Locale.getDefault()): Comparator<String> =
    Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
        .let { collator -> Comparator { a, b -> collator.compare(a, b) } }
