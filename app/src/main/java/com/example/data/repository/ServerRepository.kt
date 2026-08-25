package com.example.data.repository

import com.example.data.db.ApiRequestLogDao
import com.example.data.db.ApiRequestLogEntity
import com.example.data.db.AppDatabase
import com.example.data.db.BotDao
import com.example.data.db.BotEntity
import com.example.data.db.SandboxMessageDao
import com.example.data.db.SandboxMessageEntity
import com.example.data.db.ServerConfigDao
import com.example.data.db.ServerConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ServerRepository(private val db: AppDatabase) {
    private val configDao: ServerConfigDao = db.serverConfigDao()
    private val botDao: BotDao = db.botDao()
    private val logDao: ApiRequestLogDao = db.apiRequestLogDao()
    private val messageDao: SandboxMessageDao = db.sandboxMessageDao()

    val configFlow: Flow<ServerConfigEntity?> = configDao.getConfigFlow()
    val botsFlow: Flow<List<BotEntity>> = botDao.getAllBotsFlow()
    val logsFlow: Flow<List<ApiRequestLogEntity>> = logDao.getRecentLogsFlow()

    suspend fun initializeDefaultsIfNeeded(defaultApiId: String = "35633835", defaultApiHash: String = "a7d8cdf50ddc75cbed4a0f709a2bcd78") = withContext(Dispatchers.IO) {
        val existingConfig = configDao.getConfig()
        if (existingConfig == null) {
            configDao.insertOrUpdate(
                ServerConfigEntity(
                    port = 8081,
                    apiId = defaultApiId,
                    apiHash = defaultApiHash,
                    localModeEnabled = true,
                    maxFileSizeMb = 2000
                )
            )
        }

        val existingBots = botDao.getAllBots()
        if (existingBots.isEmpty()) {
            val sampleBot = BotEntity(
                token = "8341113063:AAF9o_bZc62q1jX-nO871_4Q5cEvsW_sample",
                botId = 8341113063L,
                firstName = "TeleStream MTProto Bot",
                username = "telestream_mtproto_bot",
                canJoinGroups = true,
                isDefault = true
            )
            botDao.insertBot(sampleBot)
        }
    }

    suspend fun getConfig(): ServerConfigEntity = withContext(Dispatchers.IO) {
        configDao.getConfig() ?: ServerConfigEntity()
    }

    suspend fun saveConfig(config: ServerConfigEntity) = withContext(Dispatchers.IO) {
        configDao.insertOrUpdate(config)
    }

    suspend fun insertBot(bot: BotEntity): Long = withContext(Dispatchers.IO) {
        botDao.insertBot(bot)
    }

    suspend fun updateBot(bot: BotEntity) = withContext(Dispatchers.IO) {
        botDao.updateBot(bot)
    }

    suspend fun deleteBot(id: Long) = withContext(Dispatchers.IO) {
        botDao.deleteBot(id)
    }

    suspend fun setDefaultBot(id: Long) = withContext(Dispatchers.IO) {
        botDao.clearDefaultBots()
        botDao.setDefaultBot(id)
    }

    suspend fun getBotById(id: Long): BotEntity? = withContext(Dispatchers.IO) {
        botDao.getBotById(id)
    }

    suspend fun getBotByToken(token: String): BotEntity? = withContext(Dispatchers.IO) {
        botDao.getBotByToken(token)
    }

    suspend fun insertLog(log: ApiRequestLogEntity) = withContext(Dispatchers.IO) {
        logDao.insertLog(log)
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        logDao.clearAllLogs()
    }

    fun getChatMessagesFlow(botToken: String, chatId: Long): Flow<List<SandboxMessageEntity>> {
        return messageDao.getChatMessagesFlow(botToken, chatId)
    }

    suspend fun insertSandboxMessage(message: SandboxMessageEntity): Long = withContext(Dispatchers.IO) {
        messageDao.insertMessage(message)
    }

    suspend fun clearChatMessages(botToken: String, chatId: Long) = withContext(Dispatchers.IO) {
        messageDao.clearChat(botToken, chatId)
    }
}
