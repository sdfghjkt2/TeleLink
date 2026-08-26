package com.example.server

import com.example.data.model.LogLevel
import com.example.data.model.ServerStats
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class TeleStreamHttpServer(
    private val repository: StreamRepository,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(TelegramDns)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var speedMonitorJob: Job? = null
    private val serverScope = CoroutineScope(Dispatchers.IO + Job())

    private val _serverStats = MutableStateFlow(ServerStats())
    val serverStats: StateFlow<ServerStats> = _serverStats.asStateFlow()

    private val activeConnectionsCount = AtomicInteger(0)
    private val totalBytesStreamedCounter = AtomicLong(0)
    private val currentSecondBytes = AtomicLong(0)
    private var startTimeMillis = 0L

    // Cache telegram file paths by file_id: file_id -> file_path on api.telegram.org
    private val telegramFilePathCache = ConcurrentHashMap<String, String>()

    suspend fun start(port: Int, hostIp: String, networkName: String) {
        if (serverSocket != null && !serverSocket!!.isClosed) {
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(java.net.InetSocketAddress(port), 250)
                serverSocket = socket
                startTimeMillis = System.currentTimeMillis()

                _serverStats.value = _serverStats.value.copy(
                    isRunning = true,
                    ipAddress = hostIp,
                    port = port,
                    networkName = networkName
                )

                repository.log(
                    method = "SYSTEM",
                    pathOrAction = "START",
                    message = "TeleStream Server listening on http://$hostIp:$port",
                    level = LogLevel.SUCCESS
                )

                startSpeedMonitor()

                serverJob = serverScope.launch {
                    while (isActive && !socket.isClosed) {
                        try {
                            val clientSocket = socket.accept()
                            serverScope.launch {
                                handleClient(clientSocket)
                            }
                        } catch (e: Exception) {
                            if (e !is SocketException) {
                                repository.log("SYSTEM", "ACCEPT", "Socket error: ${e.message}", LogLevel.ERROR)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                repository.log("SYSTEM", "START_FAIL", "Failed to start server: ${e.message}", LogLevel.ERROR)
                stop()
            }
        }
    }

    private fun startSpeedMonitor() {
        speedMonitorJob?.cancel()
        speedMonitorJob = serverScope.launch {
            while (isActive) {
                delay(1000)
                val bytesThisSec = currentSecondBytes.getAndSet(0)
                val uptimeSec = if (startTimeMillis > 0) (System.currentTimeMillis() - startTimeMillis) / 1000 else 0L

                _serverStats.value = _serverStats.value.copy(
                    activeConnections = activeConnectionsCount.get(),
                    currentSpeedBps = bytesThisSec,
                    totalBytesStreamed = totalBytesStreamedCounter.get(),
                    uptimeSeconds = uptimeSec
                )
            }
        }
    }

    suspend fun stop() {
        withContext(Dispatchers.IO) {
            try {
                speedMonitorJob?.cancel()
                serverJob?.cancel()
                serverSocket?.close()
                serverSocket = null
                _serverStats.value = _serverStats.value.copy(
                    isRunning = false,
                    activeConnections = 0,
                    currentSpeedBps = 0
                )
                repository.log("SYSTEM", "STOP", "TeleStream Server stopped", LogLevel.INFO)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        activeConnectionsCount.incrementAndGet()
        val clientIp = socket.inetAddress?.hostAddress ?: "unknown"

        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 30000
            socket.sendBufferSize = 128 * 1024
            socket.receiveBufferSize = 64 * 1024
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)

            val requestLine = reader.readLine()
            if (requestLine.isNullOrBlank()) {
                socket.close()
                return
            }

            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                socket.close()
                return
            }

            val method = parts[0].uppercase()
            val isHead = method == "HEAD"
            val rawUri = parts[1]
            val path = rawUri.substringBefore("?")

            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) break
                val colonIdx = line!!.indexOf(':')
                if (colonIdx > 0) {
                    val k = line!!.substring(0, colonIdx).trim().lowercase()
                    val v = line!!.substring(colonIdx + 1).trim()
                    headers[k] = v
                }
            }

            when {
                // Web Portal
                path == "/" || path == "/index.html" -> {
                    handleHomePage(output, clientIp)
                }

                // Web Player
                path.startsWith("/player/") -> {
                    val fileId = path.removePrefix("/player/").trim()
                    handlePlayerPage(fileId, output, clientIp)
                }

                // Stream Endpoint (video/audio inline streaming with range request support)
                path.startsWith("/stream/") -> {
                    val fileId = path.removePrefix("/stream/").trim()
                    handleStreamOrDownload(fileId, isDownload = false, headers = headers, isHead = isHead, output = output, clientIp = clientIp)
                }

                // Direct Download Endpoint (attachment header)
                path.startsWith("/download/") -> {
                    val fileId = path.removePrefix("/download/").trim()
                    handleStreamOrDownload(fileId, isDownload = true, headers = headers, isHead = isHead, output = output, clientIp = clientIp)
                }

                // API Status
                path == "/api/status" -> {
                    handleApiStatus(output)
                }

                // API Files
                path == "/api/files" -> {
                    handleApiFiles(output)
                }

                else -> {
                    sendNotFound(output)
                }
            }

        } catch (_: SocketException) {
            // Connection reset / aborted by client, normal for video player seeking
        } catch (e: Exception) {
            // Log error
        } finally {
            activeConnectionsCount.decrementAndGet()
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private suspend fun handleHomePage(output: OutputStream, clientIp: String) {
        val files = repository.allFiles.first()
        val botConfig = repository.botConfig.value
        val html = WebTemplate.renderHomePage(
            files = files,
            serverIp = _serverStats.value.ipAddress,
            port = _serverStats.value.port,
            totalBytesStreamed = totalBytesStreamedCounter.get(),
            botUsername = botConfig.botUsername.ifEmpty { "TeleStreamBot" }
        )

        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray())
        output.write(bytes)
        output.flush()

        repository.log("GET", "/", "Served Web Portal to $clientIp", LogLevel.INFO, clientIp, 200)
    }

    private suspend fun handlePlayerPage(fileId: String, output: OutputStream, clientIp: String) {
        val file = repository.getFileById(fileId)
        if (file == null) {
            sendNotFound(output)
            return
        }

        val html = WebTemplate.renderPlayerPage(
            file = file,
            serverIp = _serverStats.value.ipAddress,
            port = _serverStats.value.port
        )

        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray())
        output.write(bytes)
        output.flush()

        repository.log("GET", "/player/$fileId", "Player loaded for '${file.fileName}'", LogLevel.INFO, clientIp, 200)
    }

    private suspend fun handleStreamOrDownload(
        fileId: String,
        isDownload: Boolean,
        headers: Map<String, String>,
        isHead: Boolean,
        output: OutputStream,
        clientIp: String
    ) {
        val file = repository.getFileById(fileId)
        if (file == null) {
            sendNotFound(output)
            return
        }

        // Increment stats on full download/stream requests
        if (!isHead) {
            repository.incrementDownloadCount(file.id)
        }

        val rangeHeader = headers["range"]
        val botConfig = repository.botConfig.value

        if (file.isLocalFile && file.localFilePath != null) {
            streamLocalFile(file, rangeHeader, isDownload, isHead, output, clientIp)
        } else {
            streamTelegramFile(file, botConfig.botToken, rangeHeader, isDownload, isHead, output, clientIp)
        }
    }

    private suspend fun streamLocalFile(
        file: StreamFileItem,
        rangeHeader: String?,
        isDownload: Boolean,
        isHead: Boolean,
        output: OutputStream,
        clientIp: String
    ) {
        val localFile = File(file.localFilePath ?: "")
        if (!localFile.exists() || !localFile.canRead()) {
            sendNotFound(output)
            return
        }

        val totalLength = localFile.length()
        var startByte = 0L
        var endByte = totalLength - 1
        var isPartial = false

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
            val rangeVal = rangeHeader.removePrefix("bytes=").trim()
            val dashIdx = rangeVal.indexOf('-')
            if (dashIdx != -1) {
                val sStr = rangeVal.substring(0, dashIdx).trim()
                val eStr = rangeVal.substring(dashIdx + 1).trim()
                if (sStr.isNotEmpty()) startByte = sStr.toLongOrNull() ?: 0L
                if (eStr.isNotEmpty()) endByte = eStr.toLongOrNull() ?: (totalLength - 1)
                isPartial = true
            }
        }

        startByte = startByte.coerceIn(0L, totalLength - 1)
        endByte = endByte.coerceIn(startByte, totalLength - 1)
        val contentLength = endByte - startByte + 1

        val dispositionType = if (isDownload) "attachment" else "inline"
        val encodedName = URLEncoder.encode(file.fileName, "UTF-8").replace("+", "%20")

        val statusLine = if (isPartial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n"
        val headerBuilder = StringBuilder()
            .append(statusLine)
            .append("Content-Type: ${file.mimeType}\r\n")
            .append("Accept-Ranges: bytes\r\n")
            .append("Content-Length: $contentLength\r\n")
            .append("Content-Disposition: $dispositionType; filename=\"${file.fileName}\"; filename*=UTF-8''$encodedName\r\n")
            .append("Access-Control-Allow-Origin: *\r\n")

        if (isPartial) {
            headerBuilder.append("Content-Range: bytes $startByte-$endByte/$totalLength\r\n")
        }
        headerBuilder.append("Connection: keep-alive\r\n\r\n")

        output.write(headerBuilder.toString().toByteArray())
        output.flush()

        if (isHead) return

        repository.log(
            method = if (isDownload) "DOWNLOAD" else "STREAM",
            pathOrAction = file.fileName,
            message = "Streaming local file ($contentLength bytes, Range: $startByte-$endByte)",
            level = LogLevel.SUCCESS,
            clientIp = clientIp,
            statusCode = if (isPartial) 206 else 200
        )

        RandomAccessFile(localFile, "r").use { raf ->
            raf.seek(startByte)
            val buffer = ByteArray(64 * 1024)
            var remaining = contentLength
            while (remaining > 0) {
                val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
                val read = raf.read(buffer, 0, toRead)
                if (read == -1) break
                output.write(buffer, 0, read)
                output.flush()
                remaining -= read
                totalBytesStreamedCounter.addAndGet(read.toLong())
                currentSecondBytes.addAndGet(read.toLong())
            }
        }
    }

    private suspend fun streamTelegramFile(
        file: StreamFileItem,
        botToken: String,
        rangeHeader: String?,
        isDownload: Boolean,
        isHead: Boolean,
        output: OutputStream,
        clientIp: String
    ) {
        val cleanToken = botToken.trim()
        if (cleanToken.isBlank()) {
            sendStyledError(
                output = output,
                code = 500,
                title = "Bot Token Not Configured",
                message = "The Telegram Bot Token is missing in TeleStream settings.",
                suggestion = "Open TeleStream App -> Go to 'Bot Setup' -> Enter your Telegram Bot Token and save.",
                file = file
            )
            return
        }

        val customApiUrl = repository.botConfig.value.customBotApiUrl
        val baseApi = if (customApiUrl.isNotBlank()) customApiUrl.trimEnd('/') else "https://api.telegram.org"

        // 1. Get file path from file entity or cache or query Telegram / MTProto API
        var filePath = file.telegramFilePath ?: telegramFilePathCache[file.telegramFileId]
        var fetchErrorReason: String? = null

        if (filePath.isNullOrBlank()) {
            val result = fetchTelegramFilePathDetailed(cleanToken, file.telegramFileId, baseApi)
            filePath = result.getOrNull()
            if (!filePath.isNullOrBlank()) {
                telegramFilePathCache[file.telegramFileId] = filePath
            } else {
                fetchErrorReason = result.exceptionOrNull()?.message ?: "File path resolution failed"
            }
        }

        if (filePath.isNullOrBlank()) {
            val isLargeFile = file.fileSize > 20 * 1024 * 1024
            val title = if (isLargeFile) "Telegram Cloud 20MB File Limit" else "File Stream Unavailable"
            val message = if (isLargeFile) {
                "Telegram's public cloud API (api.telegram.org) restricts direct bot file downloads to 20MB. This file is ${NetworkUtils.formatBytes(file.fileSize)} (${file.fileName})."
            } else {
                fetchErrorReason ?: "Could not resolve file path from Telegram servers."
            }

            val suggestion = if (isLargeFile) {
                "To stream and download files over 20MB (up to 2,000MB / 2GB), run a local Telegram Bot API Server (via Termux on this phone or Docker on PC) and configure 'Custom Telegram Bot API Server URL' in the TeleStream app under 'Bot Setup'."
            } else {
                "Verify your Telegram Bot token and ensure the bot has permission to access the media file."
            }

            sendStyledError(
                output = output,
                code = 400,
                title = title,
                message = message,
                suggestion = suggestion,
                file = file
            )
            return
        }

        var tgFileUrl = "$baseApi/file/bot$cleanToken/$filePath"
        var reqBuilder = Request.Builder().url(tgFileUrl)
        if (!rangeHeader.isNullOrBlank()) {
            reqBuilder.header("Range", rangeHeader)
        }

        var okHttpCall = okHttpClient.newCall(reqBuilder.build())
        var tgResponse = withContext(Dispatchers.IO) {
            try {
                okHttpCall.execute()
            } catch (e: Exception) {
                null
            }
        }

        val tgContentType = tgResponse?.header("Content-Type") ?: ""
        val isJsonResponse = tgContentType.contains("application/json", ignoreCase = true)
        val isSuccessfulStream = tgResponse != null && (tgResponse.isSuccessful || tgResponse.code == 206) && !isJsonResponse

        if (!isSuccessfulStream) {
            val respCode = tgResponse?.code ?: 502
            val isLargeFile = file.fileSize > 20 * 1024 * 1024
            tgResponse?.close()

            val title = if (isLargeFile) "Telegram Cloud 20MB File Limit" else "Telegram CDN Download Error ($respCode)"
            val message = if (isLargeFile) {
                "Telegram Cloud API refused to stream ${file.fileName} (${NetworkUtils.formatBytes(file.fileSize)}) due to public 20MB Bot API restrictions."
            } else {
                "Telegram download servers returned HTTP $respCode for this file."
            }

            val suggestion = if (isLargeFile) {
                "To stream or download files over 20MB (up to 2GB), connect a local Telegram Bot API server in app settings under 'Bot Setup' -> 'Custom Telegram Bot API Server URL'."
            } else {
                "The file link may have expired on Telegram CDN. Re-send the file in Telegram to generate a fresh link."
            }

            sendStyledError(
                output = output,
                code = respCode,
                title = title,
                message = message,
                suggestion = suggestion,
                file = file
            )
            return
        }

        val body = tgResponse!!.body
        if (body == null) {
            sendNotFound(output)
            tgResponse.close()
            return
        }

        val totalFileSize = if (file.fileSize > 0) file.fileSize else 0L
        var reqStartByte = 0L
        var reqEndByte = if (totalFileSize > 0) totalFileSize - 1 else -1L
        var clientRequestedRange = false

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
            val rangeVal = rangeHeader.removePrefix("bytes=").trim()
            val dashIdx = rangeVal.indexOf('-')
            if (dashIdx != -1) {
                val sStr = rangeVal.substring(0, dashIdx).trim()
                val eStr = rangeVal.substring(dashIdx + 1).trim()
                if (sStr.isNotEmpty()) reqStartByte = sStr.toLongOrNull() ?: 0L
                if (eStr.isNotEmpty()) reqEndByte = eStr.toLongOrNull() ?: reqEndByte
                clientRequestedRange = true
            }
        }

        val upstreamContentLength = body.contentLength()
        val effectiveContentType = if (tgContentType.isNotBlank() && !isJsonResponse) tgContentType else file.mimeType
        val isUpstreamPartial = tgResponse.code == 206
        val upstreamContentRange = tgResponse.header("Content-Range")

        // Check if we should slice the stream locally if upstream returned 200 OK for a range request
        val shouldLocalSlice = !isUpstreamPartial && clientRequestedRange && totalFileSize > 0 &&
                (reqStartByte > 0 || (reqEndByte != -1L && reqEndByte < totalFileSize - 1))

        val isPartial = isUpstreamPartial || shouldLocalSlice
        val effectiveEnd = if (reqEndByte != -1L && totalFileSize > 0) reqEndByte.coerceAtMost(totalFileSize - 1) else if (totalFileSize > 0) totalFileSize - 1 else -1L
        val sliceLength = if (shouldLocalSlice && effectiveEnd >= reqStartByte) effectiveEnd - reqStartByte + 1 else -1L

        val dispositionType = if (isDownload) "attachment" else "inline"
        val encodedName = URLEncoder.encode(file.fileName, "UTF-8").replace("+", "%20")

        val statusLine = if (isPartial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n"
        val headerBuilder = StringBuilder()
            .append(statusLine)
            .append("Content-Type: $effectiveContentType\r\n")
            .append("Accept-Ranges: bytes\r\n")
            .append("Content-Disposition: $dispositionType; filename=\"${file.fileName}\"; filename*=UTF-8''$encodedName\r\n")
            .append("Access-Control-Allow-Origin: *\r\n")
            .append("Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\n")

        val effectiveContentLength = when {
            shouldLocalSlice -> sliceLength
            upstreamContentLength > 0 -> upstreamContentLength
            totalFileSize > 0 && !isPartial -> totalFileSize
            else -> -1L
        }

        if (effectiveContentLength > 0) {
            headerBuilder.append("Content-Length: $effectiveContentLength\r\n")
        }

        if (isUpstreamPartial && !upstreamContentRange.isNullOrBlank()) {
            headerBuilder.append("Content-Range: $upstreamContentRange\r\n")
        } else if (shouldLocalSlice && totalFileSize > 0) {
            headerBuilder.append("Content-Range: bytes $reqStartByte-$effectiveEnd/$totalFileSize\r\n")
        }

        headerBuilder.append("Connection: keep-alive\r\n\r\n")

        output.write(headerBuilder.toString().toByteArray(Charsets.UTF_8))
        output.flush()

        if (isHead) {
            tgResponse.close()
            return
        }

        repository.log(
            method = if (isDownload) "DOWNLOAD" else "STREAM",
            pathOrAction = file.fileName,
            message = "Streaming from Telegram: ${file.fileName} (${NetworkUtils.formatBytes(file.fileSize)})",
            level = LogLevel.SUCCESS,
            clientIp = clientIp,
            statusCode = if (isPartial) 206 else 200
        )

        body.byteStream().use { inputStream ->
            if (shouldLocalSlice && reqStartByte > 0) {
                var skipped = 0L
                while (skipped < reqStartByte) {
                    val n = inputStream.skip(reqStartByte - skipped)
                    if (n <= 0) break
                    skipped += n
                }
            }

            val buffer = ByteArray(64 * 1024)
            var remainingToStream = if (shouldLocalSlice) sliceLength else Long.MAX_VALUE
            var read = 0

            while (remainingToStream > 0) {
                val maxToRead = buffer.size.toLong().coerceAtMost(remainingToStream).toInt()
                read = inputStream.read(buffer, 0, maxToRead)
                if (read == -1) break
                output.write(buffer, 0, read)
                output.flush()
                if (shouldLocalSlice) remainingToStream -= read
                totalBytesStreamedCounter.addAndGet(read.toLong())
                currentSecondBytes.addAndGet(read.toLong())
            }
        }
        tgResponse.close()
    }

    private suspend fun fetchTelegramFilePathDetailed(
        botToken: String,
        fileId: String,
        baseApi: String = "https://api.telegram.org"
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            val endpointsToTry = mutableListOf<String>()
            if (baseApi.isNotBlank()) endpointsToTry.add(baseApi.trimEnd('/'))
            if (!endpointsToTry.contains("https://api.telegram.org")) endpointsToTry.add("https://api.telegram.org")
            if (!endpointsToTry.contains("http://127.0.0.1:8081")) endpointsToTry.add("http://127.0.0.1:8081")

            val cleanToken = botToken.trim()
            var lastErrorDescription = "Failed to resolve file path"

            for (endpoint in endpointsToTry) {
                try {
                    val url = "$endpoint/bot$cleanToken/getFile?file_id=$fileId"
                    val req = Request.Builder().url(url).build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        val bodyStr = resp.body?.string()
                        if (!bodyStr.isNullOrBlank()) {
                            val json = JSONObject(bodyStr)
                            if (json.optBoolean("ok")) {
                                val path = json.optJSONObject("result")?.optString("file_path", "") ?: ""
                                if (path.isNotBlank()) {
                                    return@withContext Result.success(path)
                                }
                            } else {
                                val desc = json.optString("description", "")
                                if (desc.isNotBlank()) {
                                    lastErrorDescription = desc
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    lastErrorDescription = e.message ?: lastErrorDescription
                }
            }
            Result.failure(Exception(lastErrorDescription))
        }
    }

    private suspend fun handleApiStatus(output: OutputStream) {
        val stats = _serverStats.value
        val json = JSONObject().apply {
            put("running", stats.isRunning)
            put("ip", stats.ipAddress)
            put("port", stats.port)
            put("activeConnections", stats.activeConnections)
            put("speedBps", stats.currentSpeedBps)
            put("speedFormatted", NetworkUtils.formatSpeed(stats.currentSpeedBps))
            put("totalStreamed", stats.totalBytesStreamed)
            put("totalStreamedFormatted", NetworkUtils.formatBytes(stats.totalBytesStreamed))
            put("uptimeSeconds", stats.uptimeSeconds)
            put("uptimeFormatted", NetworkUtils.formatUptime(stats.uptimeSeconds))
        }

        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray())
        output.write(bytes)
        output.flush()
    }

    private suspend fun handleApiFiles(output: OutputStream) {
        val files = repository.allFiles.first()
        val jsonArr = JSONArray()
        files.forEach { f ->
            val obj = JSONObject().apply {
                put("id", f.id)
                put("fileName", f.fileName)
                put("fileSize", f.fileSize)
                put("fileSizeFormatted", NetworkUtils.formatBytes(f.fileSize))
                put("mimeType", f.mimeType)
                put("category", f.category.name)
                put("downloadCount", f.downloadCount)
                put("streamUrl", "/stream/${f.id}")
                put("downloadUrl", "/download/${f.id}")
                put("playerUrl", "/player/${f.id}")
            }
            jsonArr.put(obj)
        }

        val bytes = jsonArr.toString().toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray())
        output.write(bytes)
        output.flush()
    }

    private fun sendNotFound(output: OutputStream) {
        sendStyledError(
            output = output,
            code = 404,
            title = "File Not Found",
            message = "The requested file or resource could not be found on this TeleStream server.",
            suggestion = "Check that the download token in the URL is correct, or resend the file in Telegram.",
            file = null
        )
    }

    private fun sendError(output: OutputStream, code: Int, message: String) {
        sendStyledError(
            output = output,
            code = code,
            title = "$code Server Error",
            message = message,
            suggestion = "Please check your server and bot configuration in the TeleStream Android App.",
            file = null
        )
    }

    private fun sendStyledError(
        output: OutputStream,
        code: Int,
        title: String,
        message: String,
        suggestion: String,
        file: StreamFileItem? = null
    ) {
        val fileInfoHtml = if (file != null) {
            """
            <div class="file-card">
                <div class="file-icon">📄</div>
                <div class="file-meta">
                    <div class="file-name">${file.fileName}</div>
                    <div class="file-size">Size: ${NetworkUtils.formatBytes(file.fileSize)} • Type: ${file.mimeType}</div>
                </div>
            </div>
            """.trimIndent()
        } else ""

        val html = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>$code - $title | TeleStream</title>
            <style>
                :root {
                    --bg: #0d1117;
                    --surface: #161b22;
                    --border: #30363d;
                    --primary: #388bfd;
                    --error: #f85149;
                    --text: #f0f6fc;
                    --text-secondary: #8b949e;
                    --warning: #e3b341;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
                body { background: var(--bg); color: var(--text); display: flex; align-items: center; justify-content: center; min-height: 100vh; padding: 20px; }
                .card { background: var(--surface); border: 1px solid var(--border); border-radius: 16px; max-width: 540px; width: 100%; padding: 32px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); text-align: center; }
                .badge { display: inline-flex; align-items: center; gap: 6px; background: rgba(248, 81, 73, 0.15); color: var(--error); padding: 6px 14px; border-radius: 20px; font-weight: 600; font-size: 14px; margin-bottom: 20px; border: 1px solid rgba(248, 81, 73, 0.3); }
                h1 { font-size: 24px; font-weight: 700; margin-bottom: 12px; color: var(--text); }
                p.desc { font-size: 15px; color: var(--text-secondary); line-height: 1.5; margin-bottom: 20px; }
                .file-card { background: rgba(255,255,255,0.03); border: 1px solid var(--border); border-radius: 12px; padding: 14px 16px; display: flex; align-items: center; gap: 14px; text-align: left; margin-bottom: 20px; }
                .file-icon { font-size: 28px; }
                .file-name { font-weight: 600; font-size: 14px; color: var(--text); word-break: break-all; }
                .file-size { font-size: 12px; color: var(--text-secondary); margin-top: 4px; }
                .suggestion-box { background: rgba(227, 179, 65, 0.1); border: 1px solid rgba(227, 179, 65, 0.25); border-radius: 12px; padding: 14px 16px; text-align: left; margin-bottom: 24px; font-size: 13px; color: var(--warning); line-height: 1.5; }
                .btn-group { display: flex; flex-direction: column; gap: 10px; }
                .btn { display: inline-flex; align-items: center; justify-content: center; gap: 8px; padding: 12px 20px; border-radius: 10px; font-weight: 600; font-size: 14px; text-decoration: none; transition: all 0.2s; cursor: pointer; border: none; }
                .btn-primary { background: var(--primary); color: #fff; }
                .btn-primary:hover { background: #2f74d0; }
                .btn-outline { background: transparent; border: 1px solid var(--border); color: var(--text); }
                .btn-outline:hover { background: rgba(255,255,255,0.05); }
                .footer { margin-top: 24px; font-size: 12px; color: var(--text-secondary); }
            </style>
        </head>
        <body>
            <div class="card">
                <div class="badge">⚠️ HTTP $code</div>
                <h1>$title</h1>
                <p class="desc">$message</p>
                $fileInfoHtml
                <div class="suggestion-box">
                    <strong>💡 Solution:</strong> $suggestion
                </div>
                <div class="btn-group">
                    <button class="btn btn-primary" onclick="window.location.reload()">🔄 Retry Download</button>
                    <a href="/" class="btn btn-outline">🏠 Return to TeleStream Web Portal</a>
                </div>
                <div class="footer">TeleStream Fast Web Streaming Server</div>
            </div>
        </body>
        </html>
        """.trimIndent()

        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $code $title\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray())
        output.write(bytes)
        output.flush()
    }
}
