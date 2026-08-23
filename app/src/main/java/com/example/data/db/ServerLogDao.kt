package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ServerLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerLogDao {
    @Query("SELECT * FROM server_logs ORDER BY timestamp DESC LIMIT 200")
    fun getRecentLogs(): Flow<List<ServerLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ServerLog)

    @Query("DELETE FROM server_logs")
    suspend fun clearLogs()
}
