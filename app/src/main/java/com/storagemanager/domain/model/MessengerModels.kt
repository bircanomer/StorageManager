package com.storagemanager.domain.model

data class MessengerJunk(
    val id: Long,
    val path: String,
    val name: String,
    val size: Long,
    val dateModified: Long,
    val mimeType: String,
    val appName: String, // "WhatsApp" or "Telegram"
    val category: String // "Images", "Videos", "Audio", "Documents"
)
