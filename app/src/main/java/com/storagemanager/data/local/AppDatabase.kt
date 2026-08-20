package com.storagemanager.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedStorageInfo::class,
        CachedScanResults::class,
        CachedJunkPhoto::class,
        TrashItemEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanResultDao(): ScanResultDao
    abstract fun trashDao(): TrashDao
}
