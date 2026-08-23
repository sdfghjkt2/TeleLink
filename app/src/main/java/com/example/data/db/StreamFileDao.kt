package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.FileCategory
import com.example.data.model.StreamFileItem
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamFileDao {
    @Query("SELECT * FROM stream_files ORDER BY createdAt DESC")
    fun getAllFiles(): Flow<List<StreamFileItem>>

    @Query("SELECT * FROM stream_files WHERE category = :category ORDER BY createdAt DESC")
    fun getFilesByCategory(category: FileCategory): Flow<List<StreamFileItem>>

    @Query("SELECT * FROM stream_files WHERE id = :id LIMIT 1")
    suspend fun getFileById(id: String): StreamFileItem?

    @Query("SELECT * FROM stream_files WHERE telegramFileId = :fileId LIMIT 1")
    suspend fun getFileByTelegramId(fileId: String): StreamFileItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: StreamFileItem)

    @Update
    suspend fun updateFile(file: StreamFileItem)

    @Query("UPDATE stream_files SET downloadCount = downloadCount + 1, lastAccessedAt = :time WHERE id = :id")
    suspend fun incrementDownloadCount(id: String, time: Long = System.currentTimeMillis())

    @Query("DELETE FROM stream_files WHERE id = :id")
    suspend fun deleteFileById(id: String)

    @Query("DELETE FROM stream_files")
    suspend fun clearAllFiles()

    @Query("SELECT COUNT(*) FROM stream_files")
    fun getFileCount(): Flow<Int>
}
