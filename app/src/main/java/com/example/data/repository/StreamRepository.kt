package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.db.ServerLogDao
import com.example.data.db.StreamFileDao
import com.example.data.model.BotConfig
import com.example.data.model.FileCategory
import com.example.data.model.LogLevel
import com.example.data.model.ServerLog
import com.example.data.model.StreamFileItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StreamRepository(
    context: Context,
    private val streamFileDao: StreamFileDao,
    private val serverLogDao: ServerLogDao
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("telestream_prefs", Context.MODE_PRIVATE)

    private val _botConfig = MutableStateFlow(loadBotConfig())
    val botConfig: StateFlow<BotConfig> = _botConfig.asStateFlow()

    val allFiles: Flow<List<StreamFileItem>> = streamFileDao.getAllFiles()
    val recentLogs: Flow<List<ServerLog>> = serverLogDao.getRecentLogs()

    private fun loadBotConfig(): BotConfig {
        return BotConfig(
            botToken = prefs.getString("bot_token", "8590847613:AAGo5TCv74JzoQzVcMsZmYjAqwdjf_OEgcA") ?: "8590847613:AAGo5TCv74JzoQzVcMsZmYjAqwdjf_OEgcA",
            botUsername = prefs.getString("bot_username", "TeleStreamBot") ?: "TeleStreamBot",
            botName = prefs.getString("bot_name", "TeleStream Bot") ?: "TeleStream Bot",
            telegramApiId = prefs.getString("telegram_api_id", "35633835") ?: "35633835",
            telegramApiHash = prefs.getString("telegram_api_hash", "a7d8cdf50ddc75cbed4a0f709a2bcd78") ?: "a7d8cdf50ddc75cbed4a0f709a2bcd78",
            isBotActive = prefs.getBoolean("is_bot_active", false),
            serverPort = prefs.getInt("server_port", 8080),
            customDomain = prefs.getString("custom_domain", "") ?: "",
            customBotApiUrl = prefs.getString("custom_bot_api_url", "http://127.0.0.1:8081") ?: "http://127.0.0.1:8081",
            adminUserIds = prefs.getString("admin_user_ids", "") ?: "",
            autoDeleteAfterDays = prefs.getInt("auto_delete_days", 0),
            welcomeMessage = prefs.getString("welcome_message", "👋 Hello! Send me any file, video, or audio to get high-speed browser download links and web stream player!") ?: "",
            isPublicBot = prefs.getBoolean("is_public_bot", true),
            githubRepoUrl = prefs.getString("github_repo_url", "https://github.com/sdfghjkt2/TeleLink") ?: "https://github.com/sdfghjkt2/TeleLink",
            autoCheckUpdates = prefs.getBoolean("auto_check_updates", true)
        )
    }

    fun saveBotConfig(config: BotConfig) {
        prefs.edit().apply {
            putString("bot_token", config.botToken)
            putString("bot_username", config.botUsername)
            putString("bot_name", config.botName)
            putString("telegram_api_id", config.telegramApiId)
            putString("telegram_api_hash", config.telegramApiHash)
            putBoolean("is_bot_active", config.isBotActive)
            putInt("server_port", config.serverPort)
            putString("custom_domain", config.customDomain)
            putString("custom_bot_api_url", config.customBotApiUrl)
            putString("admin_user_ids", config.adminUserIds)
            putInt("auto_delete_days", config.autoDeleteAfterDays)
            putString("welcome_message", config.welcomeMessage)
            putBoolean("is_public_bot", config.isPublicBot)
            putString("github_repo_url", config.githubRepoUrl)
            putBoolean("auto_check_updates", config.autoCheckUpdates)
            apply()
        }
        _botConfig.value = config
    }

    suspend fun insertFile(file: StreamFileItem) = streamFileDao.insertFile(file)

    suspend fun getFileById(id: String): StreamFileItem? = streamFileDao.getFileById(id)

    suspend fun incrementDownloadCount(id: String) = streamFileDao.incrementDownloadCount(id)

    suspend fun deleteFile(id: String) = streamFileDao.deleteFileById(id)

    suspend fun clearAllFiles() = streamFileDao.clearAllFiles()

    suspend fun log(
        method: String,
        pathOrAction: String,
        message: String,
        level: LogLevel = LogLevel.INFO,
        clientIp: String = "",
        statusCode: Int = 200
    ) {
        val logItem = ServerLog(
            timestamp = System.currentTimeMillis(),
            level = level,
            method = method,
            pathOrAction = pathOrAction,
            clientIp = clientIp,
            statusCode = statusCode,
            message = message
        )
        serverLogDao.insertLog(logItem)
    }

    suspend fun clearLogs() = serverLogDao.clearLogs()
}
