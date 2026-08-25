package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerConfigDao {
    @Query("SELECT * FROM server_config WHERE id = 1 LIMIT 1")
    fun getConfigFlow(): Flow<ServerConfigEntity?>

    @Query("SELECT * FROM server_config WHERE id = 1 LIMIT 1")
    suspend fun getConfig(): ServerConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(config: ServerConfigEntity)
}

@Dao
interface BotDao {
    @Query("SELECT * FROM bots ORDER BY isDefault DESC, createdAt DESC")
    fun getAllBotsFlow(): Flow<List<BotEntity>>

    @Query("SELECT * FROM bots ORDER BY isDefault DESC, createdAt DESC")
    suspend fun getAllBots(): List<BotEntity>

    @Query("SELECT * FROM bots WHERE id = :id LIMIT 1")
    suspend fun getBotById(id: Long): BotEntity?

    @Query("SELECT * FROM bots WHERE token = :token LIMIT 1")
    suspend fun getBotByToken(token: String): BotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBot(bot: BotEntity): Long

    @Update
    suspend fun updateBot(bot: BotEntity)

    @Query("DELETE FROM bots WHERE id = :id")
    suspend fun deleteBot(id: Long)

    @Query("UPDATE bots SET isDefault = 0")
    suspend fun clearDefaultBots()

    @Query("UPDATE bots SET isDefault = 1 WHERE id = :id")
    suspend fun setDefaultBot(id: Long)
}

@Dao
interface ApiRequestLogDao {
    @Query("SELECT * FROM api_request_logs ORDER BY timestamp DESC LIMIT 300")
    fun getRecentLogsFlow(): Flow<List<ApiRequestLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ApiRequestLogEntity)

    @Query("DELETE FROM api_request_logs")
    suspend fun clearAllLogs()
}

@Dao
interface SandboxMessageDao {
    @Query("SELECT * FROM sandbox_messages WHERE botToken = :botToken AND chatId = :chatId ORDER BY timestamp ASC")
    fun getChatMessagesFlow(botToken: String, chatId: Long): Flow<List<SandboxMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: SandboxMessageEntity): Long

    @Query("DELETE FROM sandbox_messages WHERE botToken = :botToken AND chatId = :chatId")
    suspend fun clearChat(botToken: String, chatId: Long)
}
