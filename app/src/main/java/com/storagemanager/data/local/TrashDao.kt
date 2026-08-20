package com.storagemanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashDao {

    @Query("SELECT * FROM trash_items ORDER BY deletedTimestamp DESC")
    fun getAllTrashItemsFlow(): Flow<List<TrashItemEntity>>

    @Query("SELECT * FROM trash_items ORDER BY deletedTimestamp DESC")
    suspend fun getAllTrashItems(): List<TrashItemEntity>

    @Query("SELECT * FROM trash_items WHERE id = :id LIMIT 1")
    suspend fun getTrashItemById(id: Long): TrashItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrashItem(item: TrashItemEntity): Long

    @Query("DELETE FROM trash_items WHERE id = :id")
    suspend fun deleteTrashItem(id: Long)

    @Query("DELETE FROM trash_items")
    suspend fun clearTrash()

    @Query("SELECT * FROM trash_items WHERE deletedTimestamp < :cutoffTime")
    suspend fun getItemsOlderThan(cutoffTime: Long): List<TrashItemEntity>
}
