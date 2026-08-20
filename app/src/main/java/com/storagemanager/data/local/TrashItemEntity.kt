package com.storagemanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trash_items")
data class TrashItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalPath: String,
    val trashPath: String,
    val fileName: String,
    val fileSize: Long,
    val deletedTimestamp: Long,
    val mimeType: String? = null,
    val itemType: String
)
