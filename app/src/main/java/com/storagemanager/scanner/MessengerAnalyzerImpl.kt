package com.storagemanager.scanner

import android.app.Application
import android.os.Environment
import android.webkit.MimeTypeMap
import com.storagemanager.domain.model.MessengerJunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MessengerAnalyzer implementasyonu.
 * WhatsApp ve Telegram medya klasörlerini tarayarak gereksiz dosyaları tespit eder.
 */
@Singleton
class MessengerAnalyzerImpl @Inject constructor(
    private val application: Application
) : MessengerAnalyzer {

    private val idCounter = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())

    override suspend fun scanMessengerJunk(): List<MessengerJunk> = withContext(Dispatchers.IO) {
        val junkList = mutableListOf<MessengerJunk>()
        val externalStorage = Environment.getExternalStorageDirectory() ?: return@withContext emptyList()

        // 1. WhatsApp taraması
        val whatsappBase = File(externalStorage, "Android/media/com.whatsapp/WhatsApp/Media")
        if (whatsappBase.exists() && whatsappBase.isDirectory) {
            val subfolders = listOf(
                "WhatsApp Images" to "Images",
                "WhatsApp Video" to "Videos",
                "WhatsApp Audio" to "Audio",
                "WhatsApp Documents" to "Documents",
                "WhatsApp Voice Notes" to "Audio"
            )
            for ((folderName, category) in subfolders) {
                val folder = File(whatsappBase, folderName)
                scanFolder(folder, "WhatsApp", category, junkList)
            }
        }

        // 2. Telegram taraması
        val telegramPaths = listOf(
            File(externalStorage, "Android/media/org.telegram.messenger/Telegram"),
            File(externalStorage, "Telegram")
        )
        for (telegramBase in telegramPaths) {
            if (telegramBase.exists() && telegramBase.isDirectory) {
                val subfolders = listOf(
                    "Telegram Images" to "Images",
                    "Telegram Video" to "Videos",
                    "Telegram Audio" to "Audio",
                    "Telegram Documents" to "Documents"
                )
                for ((folderName, category) in subfolders) {
                    val folder = File(telegramBase, folderName)
                    scanFolder(folder, "Telegram", category, junkList)
                }
            }
        }

        junkList
    }

    private fun scanFolder(folder: File, appName: String, category: String, list: MutableList<MessengerJunk>) {
        if (!folder.exists() || !folder.isDirectory) return
        val files = folder.listFiles() ?: return
        for (file in files) {
            if (file.isFile && file.length() > 0) {
                val mimeType = getMimeType(file)
                list.add(
                    MessengerJunk(
                        id = idCounter.getAndIncrement(),
                        path = file.absolutePath,
                        name = file.name,
                        size = file.length(),
                        dateModified = file.lastModified() / 1000,
                        mimeType = mimeType,
                        appName = appName,
                        category = category
                    )
                )
            } else if (file.isDirectory) {
                scanFolder(file, appName, category, list)
            }
        }
    }

    private fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }
}
