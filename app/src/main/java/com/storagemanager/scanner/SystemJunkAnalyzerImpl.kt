package com.storagemanager.scanner

import android.app.Application
import android.os.Environment
import com.storagemanager.domain.model.SystemJunk
import com.storagemanager.domain.model.SystemJunkType
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class SystemJunkAnalyzerImpl @Inject constructor(
    private val application: Application
) : SystemJunkAnalyzer {

    private companion object {
        const val TAG = "SystemJunkAnalyzer"
        const val MAX_DEPTH = 4

        /** Sonuç listesini ve tarama süresini sınırlamak için üst sınır. */
        const val MAX_RESULTS = 2000
    }

    private val junkExtensions = setOf("log", "tmp", "temp", "bak", "old", "dmp")
    private val junkFilenames = setOf(".ds_store", "thumbs.db", "desktop.ini", ".nomedia_bak")

    override suspend fun findSystemJunk(): List<SystemJunk> = withContext(Dispatchers.IO) {
        try {
            val externalStorage = Environment.getExternalStorageDirectory()
                ?: return@withContext emptyList()
            val junkList = mutableListOf<SystemJunk>()

            scanDirectory(externalStorage, junkList, currentDepth = 0, maxDepth = MAX_DEPTH)

            junkList.sortByDescending { it.size }
            junkList
        } catch (e: Exception) {
            Log.e(TAG, "Sistem çöpü taraması başarısız", e)
            emptyList()
        }
    }

    private suspend fun scanDirectory(dir: File, list: MutableList<SystemJunk>, currentDepth: Int, maxDepth: Int) {
        if (currentDepth > maxDepth || list.size >= MAX_RESULTS || !dir.isDirectory) return
        coroutineContext.ensureActive()

        // Skip sensitive system dirs
        if (dir.name.equals("Android", ignoreCase = true) && currentDepth == 1) {
            // Only scan Android/data or Android/media safely, avoid full system root
            return
        }

        val children = dir.listFiles()

        // 1. Check if empty folder
        if (children != null && children.isEmpty() && currentDepth > 0) {
            list.add(
                SystemJunk(
                    path = dir.absolutePath,
                    name = dir.name,
                    size = 0L,
                    type = SystemJunkType.EMPTY_FOLDER
                )
            )
            return
        }

        if (children == null) return

        for (file in children) {
            if (list.size >= MAX_RESULTS) return
            if (file.isFile) {
                val ext = file.extension.lowercase()
                val filename = file.name.lowercase()

                if (junkFilenames.contains(filename) || junkExtensions.contains(ext)) {
                    val type = when {
                        ext == "log" -> SystemJunkType.LOG_FILE
                        ext in listOf("tmp", "temp", "bak") -> SystemJunkType.TEMP_FILE
                        else -> SystemJunkType.CACHE_FILE
                    }
                    list.add(
                        SystemJunk(
                            path = file.absolutePath,
                            name = file.name,
                            size = file.length(),
                            type = type
                        )
                    )
                }
            } else if (file.isDirectory) {
                scanDirectory(file, list, currentDepth + 1, maxDepth)
            }
        }
    }
}
