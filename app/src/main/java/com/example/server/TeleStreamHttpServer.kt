package com.example.server

import com.example.data.model.LogLevel
import com.example.data.model.ServerStats
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class TeleStreamHttpServer(
    private val repository: StreamRepository,
    private val okHttpClient: OkHttpClient = OkHttpClient()
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
                val socket = ServerSocket(port)
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
            socket.soTimeout = 30000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = BufferedOutputStream(socket.getOutputStream())

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
                    handleStreamOrDownload(fileId, isDownload = false, headers = headers, output = output, clientIp = clientIp)
                }

                // Direct Download Endpoint (attachment header)
                path.startsWith("/download/") -> {
                    val fileId = path.removePrefix("/download/").trim()
                    handleStreamOrDownload(fileId, isDownload = true, headers = headers, output = output, clientIp = clientIp)
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
        output: OutputStream,
        clientIp: String
    ) {
        val file = repository.getFileById(fileId)
        if (file == null) {
            sendNotFound(output)
            return
        }

        // Increment stats
        repository.incrementDownloadCount(file.id)

        val rangeHeader = headers["range"]
        val botConfig = repository.botConfig.value

        if (file.isLocalFile && file.localFilePath != null) {
            streamLocalFile(file, rangeHeader, isDownload, output, clientIp)
        } else {
            streamTelegramFile(file, botConfig.botToken, rangeHeader, isDownload, output, clientIp)
        }
    }

    private suspend fun streamLocalFile(
        file: StreamFileItem,
        rangeHeader: String?,
        isDownload: Boolean,
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
        output: OutputStream,
        clientIp: String
    ) {
        if (botToken.isBlank()) {
            sendError(output, 500, "Telegram Bot Token is not configured in TeleStream App.")
            return
        }

        // Get file path from Telegram API if not cached
        var filePath = telegramFilePathCache[file.telegramFileId]
        if (filePath == null) {
            filePath = fetchTelegramFilePath(botToken, file.telegramFileId)
            if (filePath != null) {
                telegramFilePathCache[file.telegramFileId] = filePath
            }
        }

        if (filePath == null) {
            sendError(output, 502, "Could not resolve file path from Telegram servers.")
            return
        }

        var tgFileUrl = "https://api.telegram.org/file/bot$botToken/$filePath"
        var reqBuilder = Request.Builder().url(tgFileUrl)
        if (!rangeHeader.isNullOrBlank()) {
            reqBuilder.header("Range", rangeHeader)
        }

        var okHttpCall = okHttpClient.newCall(reqBuilder.build())
        var tgResponse = withContext(Dispatchers.IO) { okHttpCall.execute() }

        // If path expired, retry fetching a fresh path from Telegram API once
        if (tgResponse.code == 404 || tgResponse.code == 403 || tgResponse.code == 400) {
            tgResponse.close()
            telegramFilePathCache.remove(file.telegramFileId)
            val freshPath = fetchTelegramFilePath(botToken, file.telegramFileId)
            if (freshPath != null) {
                telegramFilePathCache[file.telegramFileId] = freshPath
                tgFileUrl = "https://api.telegram.org/file/bot$botToken/$freshPath"
                reqBuilder = Request.Builder().url(tgFileUrl)
                if (!rangeHeader.isNullOrBlank()) {
                    reqBuilder.header("Range", rangeHeader)
                }
                okHttpCall = okHttpClient.newCall(reqBuilder.build())
                tgResponse = withContext(Dispatchers.IO) { okHttpCall.execute() }
            }
        }

        if (!tgResponse.isSuccessful && tgResponse.code != 206) {
            sendError(output, tgResponse.code, "Telegram File API returned ${tgResponse.code}")
            tgResponse.close()
            return
        }

        val body = tgResponse.body
        if (body == null) {
            sendNotFound(output)
            tgResponse.close()
            return
        }

        val contentLength = body.contentLength()
        val tgContentType = tgResponse.header("Content-Type") ?: file.mimeType
        val isPartial = tgResponse.code == 206
        val contentRange = tgResponse.header("Content-Range")

        val dispositionType = if (isDownload) "attachment" else "inline"
        val encodedName = URLEncoder.encode(file.fileName, "UTF-8").replace("+", "%20")

        val statusLine = if (isPartial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n"
        val headerBuilder = StringBuilder()
            .append(statusLine)
            .append("Content-Type: $tgContentType\r\n")
            .append("Accept-Ranges: bytes\r\n")
            .append("Content-Disposition: $dispositionType; filename=\"${file.fileName}\"; filename*=UTF-8''$encodedName\r\n")
            .append("Access-Control-Allow-Origin: *\r\n")

        if (contentLength > 0) {
            headerBuilder.append("Content-Length: $contentLength\r\n")
        }
        if (!contentRange.isNullOrBlank()) {
            headerBuilder.append("Content-Range: $contentRange\r\n")
        }
        headerBuilder.append("Connection: keep-alive\r\n\r\n")

        output.write(headerBuilder.toString().toByteArray())
        output.flush()

        repository.log(
            method = if (isDownload) "DOWNLOAD" else "STREAM",
            pathOrAction = file.fileName,
            message = "Streaming from Telegram: ${file.fileName} (${NetworkUtils.formatBytes(file.fileSize)})",
            level = LogLevel.SUCCESS,
            clientIp = clientIp,
            statusCode = tgResponse.code
        )

        body.byteStream().use { inputStream ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                output.flush()
                totalBytesStreamedCounter.addAndGet(read.toLong())
                currentSecondBytes.addAndGet(read.toLong())
            }
        }
        tgResponse.close()
    }

    private suspend fun fetchTelegramFilePath(botToken: String, fileId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot$botToken/getFile?file_id=$fileId"
                val req = Request.Builder().url(url).build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string() ?: return@withContext null
                        val json = JSONObject(bodyStr)
                        if (json.optBoolean("ok")) {
                            return@withContext json.getJSONObject("result").optString("file_path")
                        }
                    }
                }
            } catch (_: Exception) {}
            null
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
        val body = "<h1>404 Not Found</h1><p>TeleStream file or resource not found.</p>"
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray())
        output.write(bytes)
        output.flush()
    }

    private fun sendError(output: OutputStream, code: Int, message: String) {
        val body = "<h1>$code Error</h1><p>$message</p>"
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $code Error\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray())
        output.write(bytes)
        output.flush()
    }
}
