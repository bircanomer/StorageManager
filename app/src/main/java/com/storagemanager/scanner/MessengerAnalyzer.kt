package com.storagemanager.scanner

import com.storagemanager.domain.model.MessengerJunk

/**
 * Messenger (WhatsApp/Telegram) medya dosyalarını analiz eden arayüz.
 */
interface MessengerAnalyzer {
    suspend fun scanMessengerJunk(): List<MessengerJunk>
}
