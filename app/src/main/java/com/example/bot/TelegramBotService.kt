package com.example.bot

import com.example.data.model.BotConfig
import com.example.data.model.FileCategory
import com.example.data.model.LogLevel
import com.example.data.model.StreamFileItem
import com.example.data.repository.StreamRepository
import com.example.util.NetworkUtils
import com.example.util.TelegramDns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class BotUserInfo(
    val id: Long = 0,
    val isBot: Boolean = false,
    val firstName: String = "",
    val username: String = ""
)

sealed class BotPollingStatus {
    object Idle : BotPollingStatus()
    object Connecting : BotPollingStatus()
    data class Active(val botUsername: String) : BotPollingStatus()
    data class Error(val message: String) : BotPollingStatus()
}

class TelegramBotService(
    private val repository: StreamRepository,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(TelegramDns)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    private val botScope = CoroutineScope(Dispatchers.IO + Job())
    private var pollingJob: Job? = null
    private var lastUpdateId = 0L

    private val _botStatus = MutableStateFlow<BotPollingStatus>(BotPollingStatus.Idle)
    val botStatus: StateFlow<BotPollingStatus> = _botStatus.asStateFlow()

    suspend fun verifyBotToken(token: String): Result<BotUserInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot$token/getMe"
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()

                val bodyStr = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val json = JSONObject(bodyStr)

                if (json.optBoolean("ok")) {
                    val result = json.getJSONObject("result")
                    val info = BotUserInfo(
                        id = result.optLong("id"),
                        isBot = result.optBoolean("is_bot"),
                        firstName = result.optString("first_name"),
                        username = result.optString("username")
                    )
                    Result.success(info)
                } else {
                    val desc = json.optString("description", "Invalid Bot Token")
                    Result.failure(Exception(desc))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun startPolling(hostIp: String, port: Int) {
        pollingJob?.cancel()
        val config = repository.botConfig.value
        if (config.botToken.isBlank()) {
            _botStatus.value = BotPollingStatus.Error("Bot Token is not configured.")
            return
        }

        _botStatus.value = BotPollingStatus.Connecting

        pollingJob = botScope.launch {
            // First verify
            val verifyRes = verifyBotToken(config.botToken)
            if (verifyRes.isFailure) {
                val errMsg = verifyRes.exceptionOrNull()?.message ?: "Failed to connect to Telegram API"
                _botStatus.value = BotPollingStatus.Error(errMsg)
                repository.log("BOT", "CONNECT_ERROR", errMsg, LogLevel.ERROR)
                return@launch
            }

            val botInfo = verifyRes.getOrNull()!!
            _botStatus.value = BotPollingStatus.Active(botInfo.username)
            repository.saveBotConfig(config.copy(botUsername = botInfo.username, botName = botInfo.firstName))
            repository.log("BOT", "ONLINE", "Bot @${botInfo.username} polling started", LogLevel.SUCCESS)

            while (isActive) {
                try {
                    pollUpdates(config.botToken, hostIp, port)
                } catch (e: Exception) {
                    if (isActive) {
                        repository.log("BOT", "POLL_ERROR", "Error during poll: ${e.message}", LogLevel.WARN)
                        delay(5000)
                    }
                }
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _botStatus.value = BotPollingStatus.Idle
        botScope.launch {
            repository.log("BOT", "OFFLINE", "Bot polling stopped", LogLevel.INFO)
        }
    }

    private suspend fun pollUpdates(token: String, hostIp: String, port: Int) {
        val url = "https://api.telegram.org/bot$token/getUpdates?offset=${lastUpdateId + 1}&timeout=25"
        val request = Request.Builder().url(url).build()

        val response = withContext(Dispatchers.IO) { okHttpClient.newCall(request).execute() }
        if (!response.isSuccessful) {
            response.close()
            delay(3000)
            return
        }

        val bodyStr = response.body?.string() ?: return
        val json = JSONObject(bodyStr)
        if (!json.optBoolean("ok")) return

        val results = json.optJSONArray("result") ?: return
        for (i in 0 until results.length()) {
            val update = results.getJSONObject(i)
            val updateId = update.optLong("update_id")
            if (updateId > lastUpdateId) {
                lastUpdateId = updateId
            }
            handleUpdate(update, token, hostIp, port)
        }
    }

    private suspend fun handleUpdate(update: JSONObject, token: String, hostIp: String, port: Int) {
        val message = update.optJSONObject("message") ?: update.optJSONObject("channel_post") ?: return
        val chatId = message.optJSONObject("chat")?.optLong("id") ?: return
        val messageId = message.optLong("message_id")
        val fromUser = message.optJSONObject("from")
        val senderName = fromUser?.optString("first_name", "Telegram User") ?: "User"
        val text = message.optString("text", "").trim()

        val config = repository.botConfig.value

        // Check text commands
        if (text.startsWith("/start")) {
            val welcomeText = """
                ⚡ <b>Welcome to TeleStream File Bot!</b>
                
                Send me any <b>Video, Audio, Document, or File</b>, and I will generate:
                • 📥 <b>Instant Browser Download Link</b>
                • 🎬 <b>Online Web Stream Player</b> (Full video seeking supported!)
                • 🌐 <b>Local Network Sharing Hub</b>
                
                <i>No file size limits • Hosted by TeleStream Android Server</i>
            """.trimIndent()

            sendTelegramMessage(token, chatId, welcomeText, createKeyboard(hostIp, port, null))
            repository.log("BOT", "/start", "Replied to user $senderName (Chat ID: $chatId)", LogLevel.INFO)
            return
        }

        if (text.startsWith("/help")) {
            val helpText = """
                ℹ️ <b>TeleStream Bot Help</b>
                
                <b>How to use:</b>
                1. Forward or send any file (up to 2GB) to this bot.
                2. Receive high-speed direct download and stream links.
                3. Open in your web browser, VLC, or Kodi!
                
                <b>Commands:</b>
                /start - Welcome menu
                /help - Usage instructions
                /stats - View server status & stats
            """.trimIndent()
            sendTelegramMessage(token, chatId, helpText)
            return
        }

        if (text.startsWith("/stats")) {
            val stats = repository.allFiles
            val statsText = "📊 <b>TeleStream Server Stats</b>\n• Server: <code>http://$hostIp:$port</code>\n• Status: Online ⚡"
            sendTelegramMessage(token, chatId, statsText)
            return
        }

        // Check Media / Document / File attachments
        val document = message.optJSONObject("document")
        val video = message.optJSONObject("video")
        val audio = message.optJSONObject("audio")
        val voice = message.optJSONObject("voice")
        val videoNote = message.optJSONObject("video_note")
        val photoArray = message.optJSONArray("photo")
        val animation = message.optJSONObject("animation")
        val sticker = message.optJSONObject("sticker")

        var tgFileId = ""
        var tgFileUniqueId = ""
        var fileName = ""
        var fileSize = 0L
        var mimeType = "application/octet-stream"

        when {
            document != null -> {
                tgFileId = document.optString("file_id")
                tgFileUniqueId = document.optString("file_unique_id")
                val origName = document.optString("file_name", "")
                mimeType = document.optString("mime_type", "application/octet-stream")
                fileName = if (origName.isNotBlank()) origName else "file_${tgFileUniqueId.ifEmpty { System.currentTimeMillis().toString() }}"
                fileSize = document.optLong("file_size")
            }
            video != null -> {
                tgFileId = video.optString("file_id")
                tgFileUniqueId = video.optString("file_unique_id")
                val origName = video.optString("file_name", "")
                mimeType = video.optString("mime_type", "video/mp4")
                fileName = if (origName.isNotBlank()) origName else "video_${tgFileUniqueId.ifEmpty { System.currentTimeMillis().toString() }}.mp4"
                fileSize = video.optLong("file_size")
            }
            audio != null -> {
                tgFileId = audio.optString("file_id")
                tgFileUniqueId = audio.optString("file_unique_id")
                val origName = audio.optString("file_name", "")
                val performer = audio.optString("performer", "")
                val title = audio.optString("title", "")
                mimeType = audio.optString("mime_type", "audio/mpeg")
                fileName = when {
                    origName.isNotBlank() -> origName
                    title.isNotBlank() -> if (performer.isNotBlank()) "$performer - $title.mp3" else "$title.mp3"
                    else -> "audio_${tgFileUniqueId.ifEmpty { System.currentTimeMillis().toString() }}.mp3"
                }
                fileSize = audio.optLong("file_size")
            }
            voice != null -> {
                tgFileId = voice.optString("file_id")
                tgFileUniqueId = voice.optString("file_unique_id")
                fileName = "voice_${tgFileUniqueId.ifEmpty { System.currentTimeMillis().toString() }}.ogg"
                fileSize = voice.optLong("file_size")
                mimeType = voice.optString("mime_type", "audio/ogg")
            }
            videoNote != null -> {
                tgFileId = videoNote.optString("file_id")
                tgFileUniqueId = videoNote.optString("file_unique_id")
                fileName = "round_video_${tgFileUniqueId.ifEmpty { System.currentTimeMillis().toString() }}.mp4"
                fileSize = videoNote.optLong("file_size")
                mimeType = "video/mp4"
            }
            animation != null -> {
                tgFileId = animation.optString("file_id")
                tgFileUniqueId = animation.optString("file_unique_id")
                val origName = animation.optString("file_name", "")
                fileName = if (origName.isNotBlank()) origName else "animation_${tgFileUniqueId.ifEmpty { System.currentTimeMillis().toString() }}.mp4"
                fileSize = animation.optLong("file_size")
                mimeType = animation.optString("mime_type", "video/mp4")
            }
            photoArray != null && photoArray.length() > 0 -> {
                val largestPhoto = photoArray.getJSONObject(photoArray.length() - 1)
                tgFileId = largestPhoto.optString("file_id")
                tgFileUniqueId = largestPhoto.optString("file_unique_id")
                fileName = "photo_${tgFileUniqueId.ifEmpty { System.currentTimeMillis().toString() }}.jpg"
                fileSize = largestPhoto.optLong("file_size")
                mimeType = "image/jpeg"
            }
            sticker != null -> {
                tgFileId = sticker.optString("file_id")
                tgFileUniqueId = sticker.optString("file_unique_id")
                val isAnimated = sticker.optBoolean("is_animated")
                val isVideo = sticker.optBoolean("is_video")
                val ext = when {
                    isAnimated -> "tgs"
                    isVideo -> "webm"
                    else -> "webp"
                }
                fileName = "sticker_${tgFileUniqueId.ifEmpty { System.currentTimeMillis().toString() }}.$ext"
                fileSize = sticker.optLong("file_size")
                mimeType = when {
                    isAnimated -> "application/x-tgsticker"
                    isVideo -> "video/webm"
                    else -> "image/webp"
                }
            }
        }

        if (tgFileId.isNotEmpty()) {
            val fileToken = "ts_" + UUID.randomUUID().toString().replace("-", "").take(10)
            val category = NetworkUtils.detectCategory(fileName, mimeType)

            // Resolve direct Telegram Cloud CDN path for instant worldwide reachability
            val customApiUrl = config.customBotApiUrl
            val baseApi = if (customApiUrl.isNotBlank()) customApiUrl.trimEnd('/') else "https://api.telegram.org"
            val tgFilePath = fetchTelegramFilePath(token, tgFileId, baseApi)
            
            val streamItem = StreamFileItem(
                id = fileToken,
                telegramFileId = tgFileId,
                telegramFileUniqueId = tgFileUniqueId,
                telegramFilePath = tgFilePath,
                fileName = fileName,
                fileSize = fileSize,
                mimeType = mimeType,
                category = category,
                telegramChatId = chatId,
                telegramMessageId = messageId,
                uploaderName = senderName,
                createdAt = System.currentTimeMillis()
            )

            repository.insertFile(streamItem)

            val globalCdnUrl = if (tgFilePath != null) {
                "$baseApi/file/bot$token/$tgFilePath"
            } else null

            val baseHost = if (config.customDomain.isNotBlank()) {
                config.customDomain.trimEnd('/')
            } else {
                "http://$hostIp:$port"
            }
            val downloadUrl = "$baseHost/download/$fileToken"
            val playerUrl = "$baseHost/player/$fileToken"

            val formattedSize = if (fileSize > 0) NetworkUtils.formatBytes(fileSize) else "Streamable"

            val categoryEmoji = when (category) {
                FileCategory.VIDEO -> "🎬"
                FileCategory.AUDIO -> "🎵"
                FileCategory.IMAGE -> "🖼️"
                FileCategory.ARCHIVE -> "📦"
                FileCategory.DOCUMENT -> "📄"
                FileCategory.OTHER -> "📁"
            }

            val replyText = buildString {
                appendLine("⚡ <b>File Converted to Browser Download Link!</b> $categoryEmoji")
                appendLine()
                appendLine("📄 <b>Name:</b> <code>$fileName</code>")
                appendLine("📦 <b>Size:</b> <b>$formattedSize</b>")
                appendLine("🏷️ <b>Type:</b> <code>$mimeType</code>")
                appendLine()

                if (globalCdnUrl != null) {
                    appendLine("🚀 <b>Global High-Speed Download (Anywhere/Any Device):</b>")
                    appendLine("<code>$globalCdnUrl</code>")
                    appendLine()
                }

                appendLine("📥 <b>Local TeleStream Server Link:</b>")
                appendLine("<code>$downloadUrl</code>")
                appendLine()
                appendLine("🎬 <b>Online Web Stream Player:</b>")
                appendLine("<code>$playerUrl</code>")
                appendLine()

                if (fileSize > 20 * 1024 * 1024 && globalCdnUrl == null) {
                    appendLine("⚠️ <b>Note on Telegram 20MB limit:</b> Telegram restricts direct Bot API downloads to 20MB on the public cloud. For files up to 2GB, you can connect a local Bot API server in app settings.")
                    appendLine()
                }

                append("<i>💡 Works directly with Chrome, Brave, Safari, IDM, 1DM, ADM, curl & VLC!</i>")
            }

            val keyboard = createKeyboard(hostIp, port, fileToken, globalCdnUrl)
            sendTelegramMessage(token, chatId, replyText, keyboard)

            repository.log(
                method = "BOT",
                pathOrAction = "CONVERT_LINK",
                message = "Converted '$fileName' ($formattedSize) to download link for $senderName",
                level = LogLevel.SUCCESS
            )
        }
    }

    private suspend fun fetchTelegramFilePath(botToken: String, fileId: String, baseApi: String = "https://api.telegram.org"): String? {
        val cleanToken = botToken.trim()
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseApi/bot$cleanToken/getFile?file_id=$fileId"
                val req = Request.Builder().url(url).build()
                okHttpClient.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string() ?: return@withContext null
                    val json = JSONObject(bodyStr)
                    if (json.optBoolean("ok")) {
                        return@withContext json.getJSONObject("result").optString("file_path")
                    } else {
                        val desc = json.optString("description", "Unknown error")
                        repository.log(
                            method = "BOT",
                            pathOrAction = "GET_FILE",
                            message = "Telegram getFile API: $desc",
                            level = LogLevel.WARN
                        )
                    }
                }
            } catch (e: Exception) {
                repository.log(
                    method = "BOT",
                    pathOrAction = "GET_FILE",
                    message = "Telegram getFile connection error: ${e.message}",
                    level = LogLevel.WARN
                )
            }
            null
        }
    }

    private fun createKeyboard(hostIp: String, port: Int, fileToken: String?, globalCdnUrl: String? = null): JSONObject {
        val config = repository.botConfig.value
        val baseHost = if (config.customDomain.isNotBlank()) config.customDomain.trimEnd('/') else "http://$hostIp:$port"

        val inlineKeyboard = JSONArray()

        if (globalCdnUrl != null) {
            val globalRow = JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "🚀 Global Fast Download (CDN)")
                    put("url", globalCdnUrl)
                })
            }
            inlineKeyboard.put(globalRow)
        }

        if (fileToken != null) {
            val row1 = JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "📥 Local Server Download")
                    put("url", "$baseHost/download/$fileToken")
                })
                put(JSONObject().apply {
                    put("text", "🎬 Web Stream Player")
                    put("url", "$baseHost/player/$fileToken")
                })
            }
            inlineKeyboard.put(row1)
        }

        val row2 = JSONArray().apply {
            put(JSONObject().apply {
                put("text", "🌐 Open TeleStream Portal")
                put("url", "$baseHost/")
            })
        }
        inlineKeyboard.put(row2)

        return JSONObject().apply {
            put("inline_keyboard", inlineKeyboard)
        }
    }

    suspend fun sendTelegramMessage(
        token: String,
        chatId: Long,
        text: String,
        replyMarkup: JSONObject? = null
    ) {
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot$token/sendMessage"
                val jsonPayload = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", text)
                    put("parse_mode", "HTML")
                    if (replyMarkup != null) {
                        put("reply_markup", replyMarkup)
                    }
                }

                val body = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder().url(url).post(body).build()
                okHttpClient.newCall(request).execute().close()
            } catch (e: Exception) {
                repository.log("BOT", "SEND_MSG_FAIL", "Failed to send message: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    suspend fun createManualStream(
        fileName: String,
        telegramFileId: String,
        fileSize: Long,
        mimeType: String,
        isLocalFile: Boolean = false,
        localPath: String? = null
    ): StreamFileItem {
        val fileToken = "ts_" + UUID.randomUUID().toString().replace("-", "").take(10)
        val category = NetworkUtils.detectCategory(fileName, mimeType)

        val streamItem = StreamFileItem(
            id = fileToken,
            telegramFileId = telegramFileId,
            fileName = fileName,
            fileSize = fileSize,
            mimeType = mimeType,
            category = category,
            uploaderName = if (isLocalFile) "Device Admin" else "Manual Import",
            createdAt = System.currentTimeMillis(),
            isLocalFile = isLocalFile,
            localFilePath = localPath
        )

        repository.insertFile(streamItem)
        repository.log(
            method = "MANUAL",
            pathOrAction = "CREATE",
            message = "Created stream for '$fileName' (${NetworkUtils.formatBytes(fileSize)})",
            level = LogLevel.SUCCESS
        )

        return streamItem
    }
}
