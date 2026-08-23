package com.example.bot

import com.example.data.model.BotConfig
import com.example.data.model.FileCategory
import com.example.data.model.LogLevel
import com.example.data.model.StreamFileItem
import com.example.data.repository.StreamRepository
import com.example.util.NetworkUtils
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
        .connectTimeout(35, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
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

        // Check Media / Document
        val document = message.optJSONObject("document")
        val video = message.optJSONObject("video")
        val audio = message.optJSONObject("audio")
        val voice = message.optJSONObject("voice")
        val photoArray = message.optJSONArray("photo")
        val animation = message.optJSONObject("animation")

        var tgFileId = ""
        var tgFileUniqueId = ""
        var fileName = "file_${System.currentTimeMillis()}"
        var fileSize = 0L
        var mimeType = "application/octet-stream"

        when {
            video != null -> {
                tgFileId = video.optString("file_id")
                tgFileUniqueId = video.optString("file_unique_id")
                fileName = video.optString("file_name", "video_${System.currentTimeMillis()}.mp4")
                fileSize = video.optLong("file_size")
                mimeType = video.optString("mime_type", "video/mp4")
            }
            audio != null -> {
                tgFileId = audio.optString("file_id")
                tgFileUniqueId = audio.optString("file_unique_id")
                fileName = audio.optString("file_name", "audio_${System.currentTimeMillis()}.mp3")
                fileSize = audio.optLong("file_size")
                mimeType = audio.optString("mime_type", "audio/mpeg")
            }
            voice != null -> {
                tgFileId = voice.optString("file_id")
                tgFileUniqueId = voice.optString("file_unique_id")
                fileName = "voice_${System.currentTimeMillis()}.ogg"
                fileSize = voice.optLong("file_size")
                mimeType = voice.optString("mime_type", "audio/ogg")
            }
            document != null -> {
                tgFileId = document.optString("file_id")
                tgFileUniqueId = document.optString("file_unique_id")
                fileName = document.optString("file_name", "doc_${System.currentTimeMillis()}")
                fileSize = document.optLong("file_size")
                mimeType = document.optString("mime_type", "application/octet-stream")
            }
            animation != null -> {
                tgFileId = animation.optString("file_id")
                tgFileUniqueId = animation.optString("file_unique_id")
                fileName = animation.optString("file_name", "animation_${System.currentTimeMillis()}.mp4")
                fileSize = animation.optLong("file_size")
                mimeType = animation.optString("mime_type", "video/mp4")
            }
            photoArray != null && photoArray.length() > 0 -> {
                val largestPhoto = photoArray.getJSONObject(photoArray.length() - 1)
                tgFileId = largestPhoto.optString("file_id")
                tgFileUniqueId = largestPhoto.optString("file_unique_id")
                fileName = "photo_${System.currentTimeMillis()}.jpg"
                fileSize = largestPhoto.optLong("file_size")
                mimeType = "image/jpeg"
            }
        }

        if (tgFileId.isNotEmpty()) {
            val fileToken = "ts_" + UUID.randomUUID().toString().replace("-", "").take(10)
            val category = NetworkUtils.detectCategory(fileName, mimeType)

            val streamItem = StreamFileItem(
                id = fileToken,
                telegramFileId = tgFileId,
                telegramFileUniqueId = tgFileUniqueId,
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

            val baseHost = if (config.customDomain.isNotBlank()) config.customDomain else "http://$hostIp:$port"
            val downloadUrl = "$baseHost/download/$fileToken"
            val streamUrl = "$baseHost/stream/$fileToken"
            val playerUrl = "$baseHost/player/$fileToken"

            val formattedSize = NetworkUtils.formatBytes(fileSize)

            val replyText = """
                🎉 <b>File Ready to Stream & Download!</b>
                
                📄 <b>Name:</b> <code>$fileName</code>
                📦 <b>Size:</b> $formattedSize
                🏷️ <b>Type:</b> <code>$mimeType</code>
                
                ⚡ <b>Direct Download Link:</b>
                <code>$downloadUrl</code>
                
                🎬 <b>Online Web Player:</b>
                <code>$playerUrl</code>
            """.trimIndent()

            val keyboard = createKeyboard(hostIp, port, fileToken)
            sendTelegramMessage(token, chatId, replyText, keyboard)

            repository.log(
                method = "BOT",
                pathOrAction = "CONVERT",
                message = "Converted '$fileName' ($formattedSize) for $senderName",
                level = LogLevel.SUCCESS
            )
        }
    }

    private fun createKeyboard(hostIp: String, port: Int, fileToken: String?): JSONObject {
        val config = repository.botConfig.value
        val baseHost = if (config.customDomain.isNotBlank()) config.customDomain else "http://$hostIp:$port"

        val inlineKeyboard = JSONArray()

        if (fileToken != null) {
            val row1 = JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "📥 Direct Download")
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
