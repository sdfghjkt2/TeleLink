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
import com.example.data.db.ApiRequestLogEntity
import com.example.data.db.AppDatabase
import com.example.data.db.BotEntity
import com.example.data.db.SandboxMessageEntity
import com.example.data.db.ServerConfigEntity
import com.example.data.model.AppReleaseInfo
import com.example.data.model.BotConfig
import com.example.data.model.FileCategory
import com.example.data.model.LogLevel
import com.example.data.model.MtprotoDcInfo
import com.example.data.model.ServerLog
import com.example.data.model.ServerMode
import com.example.data.model.ServerRuntimeStats
import com.example.data.model.ServerStats
import com.example.data.model.StreamFileItem
import com.example.data.model.UpdateStatus
import com.example.data.repository.ServerRepository
import com.example.data.repository.StreamRepository
import com.example.server.TeleStreamHttpServer
import com.example.server.TeleStreamService
import com.example.server.TelegramBotServer
import com.example.util.GitHubUpdateManager
import com.example.util.NetworkUtils
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class TeleStreamViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = StreamRepository(application, db.streamFileDao(), db.serverLogDao())
    val mtprotoRepository = ServerRepository(db)

    val botService = TelegramBotService(repository)
    val httpServer = TeleStreamHttpServer(repository)
    val mtprotoServer = TelegramBotServer(mtprotoRepository, viewModelScope)
    val updateManager = GitHubUpdateManager(application)

    val serverStats: StateFlow<ServerStats> = httpServer.serverStats
    val mtprotoStats: StateFlow<ServerRuntimeStats> = mtprotoServer.stats
    val botStatus: StateFlow<BotPollingStatus> = botService.botStatus
    val botConfig: StateFlow<BotConfig> = repository.botConfig

    val mtprotoConfig: StateFlow<ServerConfigEntity> = mtprotoRepository.configFlow
        .filterNotNull()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ServerConfigEntity()
        )

    val mtprotoBots: StateFlow<List<BotEntity>> = mtprotoRepository.botsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val mtprotoLogs: StateFlow<List<ApiRequestLogEntity>> = mtprotoRepository.logsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _mtprotoSimulatorChatId = MutableStateFlow(999999L)
    val mtprotoSimulatorChatId: StateFlow<Long> = _mtprotoSimulatorChatId.asStateFlow()

    private val _mtprotoSimulatorMessages = MutableStateFlow<List<SandboxMessageEntity>>(emptyList())
    val mtprotoSimulatorMessages: StateFlow<List<SandboxMessageEntity>> = _mtprotoSimulatorMessages.asStateFlow()

    private val _mtprotoSimulatorText = MutableStateFlow("")
    val mtprotoSimulatorText: StateFlow<String> = _mtprotoSimulatorText.asStateFlow()

    // API Console State
    private val _apiTesterMethod = MutableStateFlow("getMe")
    val apiTesterMethod: StateFlow<String> = _apiTesterMethod.asStateFlow()

    private val _apiTesterToken = MutableStateFlow("")
    val apiTesterToken: StateFlow<String> = _apiTesterToken.asStateFlow()

    private val _apiTesterBody = MutableStateFlow("{}")
    val apiTesterBody: StateFlow<String> = _apiTesterBody.asStateFlow()

    private val _apiTesterResponseBody = MutableStateFlow<String?>(null)
    val apiTesterResponseBody: StateFlow<String?> = _apiTesterResponseBody.asStateFlow()

    private val _isApiTesterLoading = MutableStateFlow(false)
    val isApiTesterLoading: StateFlow<Boolean> = _isApiTesterLoading.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

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

        // Initialize MTProto defaults and observe sandbox
        viewModelScope.launch {
            mtprotoRepository.initializeDefaultsIfNeeded()

            mtprotoRepository.botsFlow.collectLatest { bots ->
                if (bots.isNotEmpty()) {
                    val defaultBot = bots.firstOrNull { it.isDefault } ?: bots.first()
                    if (_apiTesterToken.value.isBlank()) {
                        _apiTesterToken.value = defaultBot.token
                    }
                    observeMtprotoSimulatorChat(defaultBot.token, _mtprotoSimulatorChatId.value)
                }
            }
        }

        // Auto-start MTProto server if configured
        viewModelScope.launch {
            val config = mtprotoRepository.getConfig()
            if (config.autoStartOnLaunch) {
                mtprotoServer.startServer(config, NetworkUtils.getLocalIpAddress())
            }
        }

        // Auto-check for updates on launch if enabled
        viewModelScope.launch {
            if (botConfig.value.autoCheckUpdates) {
                val repoUrl = botConfig.value.githubRepoUrl.ifBlank { "https://github.com/sdfghjkt2/TeleLink" }
                updateManager.checkForUpdates(repoUrl)
            }
        }
    }

    // --- MTProto Server Actions ---

    fun toggleMtprotoServer() {
        if (mtprotoStats.value.isRunning) {
            stopMtprotoServer()
        } else {
            startMtprotoServer()
        }
    }

    fun startMtprotoServer() {
        val config = mtprotoConfig.value
        val ip = NetworkUtils.getLocalIpAddress()
        mtprotoServer.startServer(config, ip)
        Toast.makeText(getApplication(), "MTProto Bot Server started on port ${config.port}", Toast.LENGTH_SHORT).show()
    }

    fun stopMtprotoServer() {
        mtprotoServer.stopServer()
        Toast.makeText(getApplication(), "MTProto Server stopped", Toast.LENGTH_SHORT).show()
    }

    fun restartMtprotoServer() {
        mtprotoServer.stopServer()
        viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            val config = mtprotoConfig.value
            val ip = NetworkUtils.getLocalIpAddress()
            mtprotoServer.startServer(config, ip)
            Toast.makeText(getApplication(), "MTProto Server restarted on port ${config.port}", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateMtprotoConfig(updated: ServerConfigEntity) {
        viewModelScope.launch {
            mtprotoRepository.saveConfig(updated)
            Toast.makeText(getApplication(), "MTProto config saved", Toast.LENGTH_SHORT).show()
            if (mtprotoStats.value.isRunning) {
                restartMtprotoServer()
            }
        }
    }

    fun updateMtprotoServerMode(mode: ServerMode) {
        viewModelScope.launch {
            val updated = mtprotoConfig.value.copy(mode = mode.name)
            mtprotoRepository.saveConfig(updated)
            Toast.makeText(getApplication(), "Mode set to ${mode.name}", Toast.LENGTH_SHORT).show()
            if (mtprotoStats.value.isRunning) {
                restartMtprotoServer()
            }
        }
    }

    fun selectMtprotoDc(dc: MtprotoDcInfo) {
        viewModelScope.launch {
            val updated = mtprotoConfig.value.copy(
                selectedDcId = dc.id,
                isTestDc = dc.isTest
            )
            mtprotoRepository.saveConfig(updated)
            Toast.makeText(getApplication(), "MTProto DC set to DC${dc.id} (${dc.location})", Toast.LENGTH_SHORT).show()
            if (mtprotoStats.value.isRunning) {
                restartMtprotoServer()
            }
        }
    }

    private fun observeMtprotoSimulatorChat(token: String, chatId: Long) {
        viewModelScope.launch {
            mtprotoRepository.getChatMessagesFlow(token, chatId).collectLatest { msgs ->
                _mtprotoSimulatorMessages.value = msgs
            }
        }
    }

    fun setMtprotoSimulatorText(text: String) {
        _mtprotoSimulatorText.value = text
    }

    fun sendMtprotoSimulatorMessage() {
        val text = _mtprotoSimulatorText.value.trim()
        if (text.isBlank()) return
        val defaultBot = mtprotoBots.value.firstOrNull { it.isDefault } ?: mtprotoBots.value.firstOrNull()
        if (defaultBot == null) {
            Toast.makeText(getApplication(), "No active bot configured in sandbox", Toast.LENGTH_SHORT).show()
            return
        }

        _mtprotoSimulatorText.value = ""
        mtprotoServer.dispatchSimulatedUpdate(
            bot = defaultBot,
            chatId = _mtprotoSimulatorChatId.value,
            userText = text,
            senderName = "Developer",
            onWebhookDelivery = { success, _ ->
                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(getApplication(), if (success) "Webhook Delivered" else "Webhook Delivery Failed", Toast.LENGTH_SHORT).show()
                }
            }
        )

        if (mtprotoConfig.value.mode == ServerMode.LOCAL_SANDBOX.name && defaultBot.webhookUrl.isBlank()) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(350)
                val replyText = when {
                    text == "/start" -> "👋 Welcome to <b>${defaultBot.firstName}</b>!\n\nRunning in local MTProto Sandbox mode on Android.\n\nCommands:\n/ping - Check latency\n/help - Bot info\n/info - Server stats"
                    text == "/ping" -> "🏓 <b>Pong!</b>\nMTProto DC: ${mtprotoConfig.value.selectedDcId} (${if (mtprotoConfig.value.isTestDc) "Test" else "Prod"})\nPort: ${mtprotoConfig.value.port}"
                    text == "/info" -> "ℹ️ <b>MTProto Server</b>\nIP: ${mtprotoStats.value.localIp}\nUptime: ${mtprotoStats.value.uptimeSeconds}s\nRequests: ${mtprotoStats.value.totalRequests}"
                    text == "/help" -> "🛠 <b>Sandbox Help</b>\n• Point your local code to <code>http://${NetworkUtils.getLocalIpAddress()}:${mtprotoConfig.value.port}/bot${defaultBot.token}/</code>"
                    text.startsWith("/echo ") -> text.removePrefix("/echo ")
                    else -> "🤖 Echo: $text"
                }

                mtprotoRepository.insertSandboxMessage(
                    SandboxMessageEntity(
                        botToken = defaultBot.token,
                        chatId = _mtprotoSimulatorChatId.value,
                        messageId = System.currentTimeMillis() % 1000000,
                        isFromBot = true,
                        senderName = defaultBot.firstName,
                        text = replyText,
                        mediaType = "text"
                    )
                )
            }
        }
    }

    fun clearMtprotoSimulatorChat() {
        val defaultBot = mtprotoBots.value.firstOrNull { it.isDefault } ?: mtprotoBots.value.firstOrNull()
        if (defaultBot != null) {
            viewModelScope.launch {
                mtprotoRepository.clearChatMessages(defaultBot.token, _mtprotoSimulatorChatId.value)
                Toast.makeText(getApplication(), "Chat history cleared", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun clearMtprotoLogs() {
        viewModelScope.launch {
            mtprotoRepository.clearLogs()
            Toast.makeText(getApplication(), "MTProto logs cleared", Toast.LENGTH_SHORT).show()
        }
    }

    fun setApiTesterMethod(method: String) {
        _apiTesterMethod.value = method
        _apiTesterBody.value = when (method) {
            "getMe" -> "{}"
            "getUpdates" -> """{"offset": 0, "limit": 10, "timeout": 5}"""
            "sendMessage" -> """{"chat_id": 999999, "text": "Hello from MTProto Bot Server! 🚀"}"""
            "sendChatAction" -> """{"chat_id": 999999, "action": "typing"}"""
            "sendPhoto" -> """{"chat_id": 999999, "photo": "https://picsum.photos/400/300", "caption": "Attachment Demo"}"""
            "setWebhook" -> """{"url": "https://example.com/webhook", "secret_token": "secret_123"}"""
            "getWebhookInfo" -> "{}"
            "deleteWebhook" -> "{}"
            "setMyCommands" -> """{"commands": [{"command": "start", "description": "Start bot"}, {"command": "help", "description": "Help"}]}"""
            "getMyCommands" -> "{}"
            else -> "{}"
        }
    }

    fun setApiTesterToken(token: String) {
        _apiTesterToken.value = token
    }

    fun setApiTesterBody(body: String) {
        _apiTesterBody.value = body
    }

    fun executeApiTest() {
        val token = _apiTesterToken.value.trim()
        if (token.isBlank()) {
            Toast.makeText(getApplication(), "Please specify a Bot Token", Toast.LENGTH_SHORT).show()
            return
        }

        val port = mtprotoConfig.value.port
        val method = _apiTesterMethod.value
        val url = "http://127.0.0.1:$port/bot$token/$method"

        _isApiTesterLoading.value = true
        _apiTesterResponseBody.value = "Executing HTTP POST $url..."

        viewModelScope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val body = _apiTesterBody.value.toRequestBody(mediaType)
                val req = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                val res = okHttpClient.newCall(req).execute()
                val resBody = res.body?.string() ?: "{}"
                val elapsed = System.currentTimeMillis() - start

                val formatted = try {
                    JSONObject(resBody).toString(2)
                } catch (_: Exception) {
                    resBody
                }

                _apiTesterResponseBody.value = "// HTTP ${res.code} (${elapsed}ms)\n$formatted"
            } catch (e: Exception) {
                _apiTesterResponseBody.value = "// Error: ${e.message}\nEnsure the server is running on port $port"
            } finally {
                _isApiTesterLoading.value = false
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
                TeleStreamService.stop(context)
                httpServer.stop()
                botService.stopPolling()
                Toast.makeText(context, "TeleStream Server stopped", Toast.LENGTH_SHORT).show()
            } else {
                val ip = NetworkUtils.getDeviceIpAddress(context)
                val networkType = NetworkUtils.getNetworkTypeName(context)
                val port = botConfig.value.serverPort
                TeleStreamService.start(context)
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
