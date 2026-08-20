package com.storagemanager.scanner

import android.app.Application
import android.os.Environment
import com.storagemanager.domain.model.StorageTreeNode
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class TreemapAnalyzerImpl @Inject constructor(
    private val application: Application
) : TreemapAnalyzer {

    private companion object {
        const val TAG = "TreemapAnalyzer"

        /** Sığ boyut hesabında gezilecek en fazla dosya. */
        const val MAX_SHALLOW_FILES = 20_000
    }

    override suspend fun generateTreemap(maxDepth: Int): StorageTreeNode = withContext(Dispatchers.IO) {
        try {
            val rootDir = Environment.getExternalStorageDirectory() ?: File("/")
            buildNode(rootDir, currentDepth = 0, maxDepth = maxDepth)
        } catch (e: Exception) {
            Log.e(TAG, "Treemap oluşturulamadı", e)
            StorageTreeNode(path = "/", name = "Dahili Depolama", size = 0L, isDirectory = true)
        }
    }

    private suspend fun buildNode(file: File, currentDepth: Int, maxDepth: Int): StorageTreeNode {
        coroutineContext.ensureActive()

        if (!file.exists()) {
            return StorageTreeNode(path = file.absolutePath, name = file.name, size = 0L, isDirectory = false)
        }

        if (file.isFile) {
            return StorageTreeNode(
                path = file.absolutePath,
                name = file.name,
                size = file.length(),
                isDirectory = false
            )
        }

        val childrenNodes = mutableListOf<StorageTreeNode>()
        var totalFolderSize = 0L

        val subFiles = file.listFiles()
        if (subFiles != null && currentDepth < maxDepth) {
            for (subFile in subFiles) {
                try {
                    val childNode = buildNode(subFile, currentDepth + 1, maxDepth)
                    if (childNode.size > 0) {
                        childrenNodes.add(childNode)
                        totalFolderSize += childNode.size
                    }
                } catch (_: Exception) {
                    // Ignore unreadable files/folders
                }
            }
        } else if (subFiles != null) {
            // Fast shallow calculate
            totalFolderSize = calculateFolderSizeShallow(file)
        }

        val sortedChildren = childrenNodes.sortedByDescending { it.size }
        val childrenWithPercentage = sortedChildren.map { child ->
            val percentage = if (totalFolderSize > 0) child.size.toFloat() / totalFolderSize else 0f
            child.copy(percentageOfParent = percentage)
        }

        return StorageTreeNode(
            path = file.absolutePath,
            name = if (file.name.isEmpty()) "Dahili Depolama" else file.name,
            size = totalFolderSize,
            isDirectory = true,
            children = childrenWithPercentage,
            percentageOfParent = 1.0f
        )
    }

    private suspend fun calculateFolderSizeShallow(dir: File): Long {
        var size = 0L
        var visited = 0
        try {
            for (f in dir.walkTopDown().maxDepth(3)) {
                if (++visited % 512 == 0) coroutineContext.ensureActive()
                if (visited > MAX_SHALLOW_FILES) break
                if (f.isFile) size += f.length()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Klasör boyutu kısmi hesaplandı: ${dir.path}", e)
        }
        return size
    }
}
