package com.example.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bot.BotPollingStatus
import com.example.bot.TelegramBotService
import com.example.data.db.AppDatabase
import com.example.data.model.AppReleaseInfo
import com.example.data.model.BotConfig
import com.example.data.model.FileCategory
import com.example.data.model.LogLevel
import com.example.data.model.ServerLog
import com.example.data.model.ServerStats
import com.example.data.model.StreamFileItem
import com.example.data.model.UpdateStatus
import com.example.data.repository.StreamRepository
import com.example.server.TeleStreamHttpServer
import com.example.util.GitHubUpdateManager
import com.example.util.NetworkUtils
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TeleStreamViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = StreamRepository(application, db.streamFileDao(), db.serverLogDao())

    val botService = TelegramBotService(repository)
    val httpServer = TeleStreamHttpServer(repository)
    val updateManager = GitHubUpdateManager(application)

    val serverStats: StateFlow<ServerStats> = httpServer.serverStats
    val botStatus: StateFlow<BotPollingStatus> = botService.botStatus
    val botConfig: StateFlow<BotConfig> = repository.botConfig
    val updateStatus: StateFlow<UpdateStatus> = updateManager.updateStatus
    val appVersionName: String = updateManager.currentVersionName
    val appVersionCode: Int = updateManager.currentVersionCode
    val recentLogs: StateFlow<List<ServerLog>> = repository.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<FileCategory?>(null)
    val selectedCategory: StateFlow<FileCategory?> = _selectedCategory.asStateFlow()

    private val _rawFiles = repository.allFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredFiles: StateFlow<List<StreamFileItem>> = combine(
        _rawFiles,
        _searchQuery,
        _selectedCategory
    ) { files, query, category ->
        files.filter { file ->
            val matchesQuery = query.isBlank() ||
                    file.fileName.contains(query, ignoreCase = true) ||
                    file.mimeType.contains(query, ignoreCase = true)
            val matchesCategory = category == null || file.category == category
            matchesQuery && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _previewFile = MutableStateFlow<StreamFileItem?>(null)
    val previewFile: StateFlow<StreamFileItem?> = _previewFile.asStateFlow()

    private val _qrFile = MutableStateFlow<StreamFileItem?>(null)
    val qrFile: StateFlow<StreamFileItem?> = _qrFile.asStateFlow()

    private val _isTestingBot = MutableStateFlow(false)
    val isTestingBot: StateFlow<Boolean> = _isTestingBot.asStateFlow()

    private val _botTestMessage = MutableStateFlow<String?>(null)
    val botTestMessage: StateFlow<String?> = _botTestMessage.asStateFlow()

    init {
        // Seed some sample streams if first launch
        viewModelScope.launch {
            seedSampleData()
        }

        // Auto-check for updates on launch if enabled
        viewModelScope.launch {
            if (botConfig.value.autoCheckUpdates) {
                val repoUrl = botConfig.value.githubRepoUrl.ifBlank { "https://github.com/sdfghjkt2/TeleLink" }
                updateManager.checkForUpdates(repoUrl)
            }
        }
    }

    private suspend fun seedSampleData() {
        val current = _rawFiles.value
        if (current.isEmpty()) {
            val sampleVideo = StreamFileItem(
                id = "sample_video_hd",
                telegramFileId = "BAACAgIAAxkBAAI...",
                fileName = "Cosmic_Nebula_Trailer_4K.mp4",
                fileSize = 48500000L, // 48.5 MB
                mimeType = "video/mp4",
                category = FileCategory.VIDEO,
                uploaderName = "TeleStream Showcase",
                createdAt = System.currentTimeMillis() - 3600000,
                downloadCount = 14
            )
            val sampleAudio = StreamFileItem(
                id = "sample_audio_lossless",
                telegramFileId = "CQACAgIAAxkBAAM...",
                fileName = "Synthwave_Night_Drive.mp3",
                fileSize = 8400000L, // 8.4 MB
                mimeType = "audio/mpeg",
                category = FileCategory.AUDIO,
                uploaderName = "DJ Beats",
                createdAt = System.currentTimeMillis() - 7200000,
                downloadCount = 28
            )
            val sampleDoc = StreamFileItem(
                id = "sample_doc_manual",
                telegramFileId = "BQACAgIAAxkBAAE...",
                fileName = "TeleStream_User_Guide.pdf",
                fileSize = 3200000L, // 3.2 MB
                mimeType = "application/pdf",
                category = FileCategory.DOCUMENT,
                uploaderName = "Admin",
                createdAt = System.currentTimeMillis() - 10800000,
                downloadCount = 5
            )

            repository.insertFile(sampleVideo)
            repository.insertFile(sampleAudio)
            repository.insertFile(sampleDoc)

            repository.log("SYSTEM", "INIT", "TeleStream Database ready with sample streams", LogLevel.INFO)
        }
    }

    fun toggleServer() {
        viewModelScope.launch {
            val stats = serverStats.value
            val context = getApplication<Application>()
            if (stats.isRunning) {
                httpServer.stop()
                botService.stopPolling()
                Toast.makeText(context, "TeleStream Server stopped", Toast.LENGTH_SHORT).show()
            } else {
                val ip = NetworkUtils.getDeviceIpAddress(context)
                val networkType = NetworkUtils.getNetworkTypeName(context)
                val port = botConfig.value.serverPort
                httpServer.start(port, ip, networkType)

                if (botConfig.value.botToken.isNotBlank()) {
                    botService.startPolling(ip, port)
                }
                Toast.makeText(context, "Server active on http://$ip:$port", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: FileCategory?) {
        _selectedCategory.value = category
    }

    fun setPreviewFile(file: StreamFileItem?) {
        _previewFile.value = file
    }

    fun setQrFile(file: StreamFileItem?) {
        _qrFile.value = file
    }

    fun saveBotConfig(config: BotConfig) {
        repository.saveBotConfig(config)
        val context = getApplication<Application>()
        Toast.makeText(context, "Settings updated", Toast.LENGTH_SHORT).show()

        // If server is currently running and bot token changed, re-poll
        if (serverStats.value.isRunning && config.botToken.isNotBlank()) {
            val ip = serverStats.value.ipAddress
            val port = serverStats.value.port
            botService.startPolling(ip, port)
        }
    }

    fun testBotToken(token: String) {
        if (token.isBlank()) {
            _botTestMessage.value = "⚠️ Please enter a valid Bot Token first."
            return
        }

        viewModelScope.launch {
            _isTestingBot.value = true
            _botTestMessage.value = "Connecting to Telegram API..."

            val res = botService.verifyBotToken(token)
            _isTestingBot.value = false
            if (res.isSuccess) {
                val info = res.getOrNull()!!
                _botTestMessage.value = "✅ Connected as @${info.username} (${info.firstName})"
                saveBotConfig(botConfig.value.copy(botToken = token, botUsername = info.username, botName = info.firstName))
            } else {
                val err = res.exceptionOrNull()?.message ?: "Verification failed"
                _botTestMessage.value = "❌ Error: $err"
            }
        }
    }

    fun clearBotTestMessage() {
        _botTestMessage.value = null
    }

    fun createManualStream(
        fileName: String,
        telegramFileId: String,
        fileSize: Long,
        mimeType: String,
        isLocalFile: Boolean = false,
        localPath: String? = null
    ) {
        viewModelScope.launch {
            botService.createManualStream(
                fileName = fileName.ifBlank { "stream_file_${System.currentTimeMillis()}" },
                telegramFileId = telegramFileId,
                fileSize = if (fileSize > 0) fileSize else 1048576L,
                mimeType = mimeType.ifBlank { "application/octet-stream" },
                isLocalFile = isLocalFile,
                localPath = localPath
            )
            Toast.makeText(getApplication(), "Stream link created!", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteFile(id: String) {
        viewModelScope.launch {
            repository.deleteFile(id)
            Toast.makeText(getApplication(), "File removed from streams", Toast.LENGTH_SHORT).show()
        }
    }

    fun clearAllFiles() {
        viewModelScope.launch {
            repository.clearAllFiles()
            Toast.makeText(getApplication(), "All files cleared", Toast.LENGTH_SHORT).show()
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun copyAllLogs() {
        val currentLogs = recentLogs.value
        if (currentLogs.isEmpty()) {
            Toast.makeText(getApplication(), "No logs to copy", Toast.LENGTH_SHORT).show()
            return
        }
        val formattedLogs = currentLogs.joinToString("\n") { log ->
            "[${NetworkUtils.formatDate(log.timestamp)}] [${log.level.name}] [${log.method}] ${log.pathOrAction} - ${log.message}"
        }
        copyToClipboard(formattedLogs, "Server Logs")
    }

    fun copyToClipboard(text: String, label: String = "Link") {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(getApplication(), "$label copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    fun getDownloadUrl(fileId: String): String {
        val config = botConfig.value
        val stats = serverStats.value
        val base = if (config.customDomain.isNotBlank()) config.customDomain else "http://${stats.ipAddress}:${stats.port}"
        return "$base/download/$fileId"
    }

    fun getStreamUrl(fileId: String): String {
        val config = botConfig.value
        val stats = serverStats.value
        val base = if (config.customDomain.isNotBlank()) config.customDomain else "http://${stats.ipAddress}:${stats.port}"
        return "$base/stream/$fileId"
    }

    fun getPlayerUrl(fileId: String): String {
        val config = botConfig.value
        val stats = serverStats.value
        val base = if (config.customDomain.isNotBlank()) config.customDomain else "http://${stats.ipAddress}:${stats.port}"
        return "$base/player/$fileId"
    }

    fun getWebPortalUrl(): String {
        val config = botConfig.value
        val stats = serverStats.value
        val base = if (config.customDomain.isNotBlank()) config.customDomain else "http://${stats.ipAddress}:${stats.port}"
        return "$base/"
    }

    fun checkForUpdates(customUrl: String? = null, showToast: Boolean = false) {
        viewModelScope.launch {
            val url = customUrl ?: botConfig.value.githubRepoUrl.ifBlank { "https://github.com/sdfghjkt2/TeleLink" }
            val res = updateManager.checkForUpdates(url)
            if (showToast) {
                val context = getApplication<Application>()
                if (res.isSuccess) {
                    val info = res.getOrNull()
                    if (info == null || !info.isNewer) {
                        Toast.makeText(context, "App is up to date (v$appVersionName)", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "New update found: ${info.tagName}!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val err = res.exceptionOrNull()?.message ?: "Check failed"
                    Toast.makeText(context, "Update check: $err", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun downloadAndInstallUpdate(release: AppReleaseInfo) {
        viewModelScope.launch {
            val res = updateManager.downloadUpdate(release)
            if (res.isSuccess) {
                val file = res.getOrNull()
                if (file != null) {
                    updateManager.installUpdate(file)
                }
            } else {
                val err = res.exceptionOrNull()?.message ?: "Download failed"
                Toast.makeText(getApplication(), "Update error: $err", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun installDownloadedUpdate(file: File) {
        updateManager.installUpdate(file)
    }

    fun dismissUpdateStatus() {
        updateManager.resetStatus()
    }
}
