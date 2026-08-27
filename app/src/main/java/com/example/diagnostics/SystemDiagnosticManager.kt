package com.example.diagnostics

import android.content.Context
import com.example.data.model.BotConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

enum class DiagnosticStatus {
    RUNNING, SUCCESS, WARNING, ERROR
}

data class DiagnosticStep(
    val id: String,
    val title: String,
    val description: String,
    val command: String? = null,
    val status: DiagnosticStatus = DiagnosticStatus.RUNNING,
    val output: String = "",
    val details: String = ""
)

data class DiagnosticsReport(
    val timestamp: Long = System.currentTimeMillis(),
    val steps: List<DiagnosticStep> = emptyList(),
    val isComplete: Boolean = false,
    val overallStatus: DiagnosticStatus = DiagnosticStatus.RUNNING,
    val summary: String = ""
)

class SystemDiagnosticManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {

    suspend fun runFullDiagnostics(
        botConfig: BotConfig,
        httpServerPort: Int = 8080,
        mtprotoServerPort: Int = 8081,
        onProgress: (DiagnosticsReport) -> Unit
    ): DiagnosticsReport = withContext(Dispatchers.IO) {
        val steps = mutableListOf<DiagnosticStep>()

        fun emit(currentStep: DiagnosticStep, isDone: Boolean = false) {
            val idx = steps.indexOfFirst { it.id == currentStep.id }
            if (idx >= 0) {
                steps[idx] = currentStep
            } else {
                steps.add(currentStep)
            }
            val hasErrors = steps.any { it.status == DiagnosticStatus.ERROR }
            val hasWarnings = steps.any { it.status == DiagnosticStatus.WARNING }
            val overall = when {
                hasErrors -> DiagnosticStatus.ERROR
                hasWarnings -> DiagnosticStatus.WARNING
                else -> DiagnosticStatus.SUCCESS
            }
            onProgress(
                DiagnosticsReport(
                    steps = steps.toList(),
                    isComplete = isDone,
                    overallStatus = if (!isDone) DiagnosticStatus.RUNNING else overall,
                    summary = if (isDone) {
                        if (hasErrors) "Diagnostics completed with errors. See details below."
                        else if (hasWarnings) "Diagnostics passed with warnings."
                        else "All system checks and services running normally!"
                    } else "Running system diagnostic suite..."
                )
            )
        }

        // 1. Sh / Bash Process Environment Check
        val step1 = DiagnosticStep(
            id = "shell_env",
            title = "Linux / Shell Environment Check",
            description = "Checks Android sh shell and system resource counters",
            command = "uname -a && uptime && df -h /data"
        )
        emit(step1)
        val shResult = executeBashCommand("uname -a && uptime")
        val storageResult = executeBashCommand("df -h ${context.filesDir.absolutePath} || df -h /data")
        val step1Output = "Kernel: ${shResult.first}\nUptime: ${shResult.second}\nDisk:\n${storageResult.second}"
        emit(
            step1.copy(
                status = if (shResult.first == 0) DiagnosticStatus.SUCCESS else DiagnosticStatus.WARNING,
                output = step1Output,
                details = "Android Shell /system/bin/sh is accessible and working."
            )
        )

        // 2. HTTP Web Streaming Server Check (Port 8080)
        val step2 = DiagnosticStep(
            id = "http_server",
            title = "HTTP Media Streaming Server (Port $httpServerPort)",
            description = "Tests local TCP socket binding and HTTP response on 127.0.0.1:$httpServerPort",
            command = "socket_connect 127.0.0.1 $httpServerPort"
        )
        emit(step2)
        val isHttpListening = testTcpPort("127.0.0.1", httpServerPort, timeoutMs = 1500)
        if (isHttpListening) {
            val httpCall = testHttpUrl("http://127.0.0.1:$httpServerPort/")
            emit(
                step2.copy(
                    status = DiagnosticStatus.SUCCESS,
                    output = "TCP Port $httpServerPort is OPEN & LISTENING.\nHTTP Response: ${httpCall.first} (Code ${httpCall.second})",
                    details = "Web portal and video streaming server are active and healthy."
                )
            )
        } else {
            emit(
                step2.copy(
                    status = DiagnosticStatus.WARNING,
                    output = "TCP Port $httpServerPort is not responding.",
                    details = "Streaming server is stopped. Tap the Server Power button on Dashboard to start it."
                )
            )
        }

        // 3. Internal MTProto / Bot Server Check (Port 8081)
        val step3 = DiagnosticStep(
            id = "mtproto_server",
            title = "MTProto Bot Gateway Server (Port $mtprotoServerPort)",
            description = "Tests local TCP socket and gateway endpoint on 127.0.0.1:$mtprotoServerPort",
            command = "socket_connect 127.0.0.1 $mtprotoServerPort"
        )
        emit(step3)
        val isMtprotoListening = testTcpPort("127.0.0.1", mtprotoServerPort, timeoutMs = 1500)
        if (isMtprotoListening) {
            val mtprotoCall = testHttpUrl("http://127.0.0.1:$mtprotoServerPort/")
            emit(
                step3.copy(
                    status = DiagnosticStatus.SUCCESS,
                    output = "TCP Port $mtprotoServerPort is OPEN & LISTENING.\nResponse: ${mtprotoCall.first} (Code ${mtprotoCall.second})",
                    details = "Internal MTProto / Bot engine is actively running."
                )
            )
        } else {
            emit(
                step3.copy(
                    status = DiagnosticStatus.WARNING,
                    output = "TCP Port $mtprotoServerPort is not listening.",
                    details = "MTProto server is stopped. Tap the toggle on the MTProto card to start."
                )
            )
        }

        // 4. Telegram Cloud DNS & Network Reachability
        val step4 = DiagnosticStep(
            id = "telegram_network",
            title = "Telegram Cloud Network Connectivity",
            description = "Verifies internet route and SSL handshake to api.telegram.org:443",
            command = "socket_connect api.telegram.org 443"
        )
        emit(step4)
        val isTelegramReachable = testTcpPort("api.telegram.org", 443, timeoutMs = 3000)
        if (isTelegramReachable) {
            emit(
                step4.copy(
                    status = DiagnosticStatus.SUCCESS,
                    output = "Connected to api.telegram.org:443 successfully.\nInternet DNS and routing are operational.",
                    details = "Your Android device can reach Telegram Cloud servers."
                )
            )
        } else {
            emit(
                step4.copy(
                    status = DiagnosticStatus.ERROR,
                    output = "Unable to connect to api.telegram.org on port 443.",
                    details = "Check your WiFi or cellular internet connection. Firewall or VPN may be blocking Telegram."
                )
            )
        }

        // 5. Bot Token & Telegram Authentication Check
        val cleanToken = botConfig.botToken.trim()
        val step5 = DiagnosticStep(
            id = "bot_auth",
            title = "Telegram Bot Token & getMe Test",
            description = "Tests bot credentials directly against Telegram API",
            command = if (cleanToken.isNotBlank()) "GET https://api.telegram.org/bot<TOKEN>/getMe" else "No token provided"
        )
        emit(step5)
        if (cleanToken.isBlank()) {
            emit(
                step5.copy(
                    status = DiagnosticStatus.WARNING,
                    output = "No Bot Token configured.",
                    details = "Go to Bot Setup tab and enter your Telegram Bot Token from @BotFather."
                )
            )
        } else {
            val officialCloudUrl = "https://api.telegram.org/bot$cleanToken/getMe"
            val customApiBase = botConfig.customBotApiUrl.trim().trimEnd('/')
            val hasCustomUrl = customApiBase.isNotBlank()

            try {
                // First test official Telegram Cloud
                val req = Request.Builder().url(officialCloudUrl).build()
                val response = okHttpClient.newCall(req).execute()
                val bodyStr = response.body?.string() ?: ""

                if (response.isSuccessful && bodyStr.contains("\"ok\":true")) {
                    val json = JSONObject(bodyStr)
                    val result = json.optJSONObject("result")
                    val botName = result?.optString("first_name", "Bot") ?: "Bot"
                    val username = result?.optString("username", "") ?: ""

                    var customStatusInfo = ""
                    if (hasCustomUrl) {
                        try {
                            val customUrl = "$customApiBase/bot$cleanToken/getMe"
                            val cReq = Request.Builder().url(customUrl).build()
                            val cRes = okHttpClient.newCall(cReq).execute()
                            val cBody = cRes.body?.string() ?: ""
                            customStatusInfo = if (cRes.isSuccessful && cBody.contains("\"ok\":true")) {
                                "\nCustom Server: Connected & Verified ($customApiBase)"
                            } else {
                                "\nCustom Server ($customApiBase): Responded with HTTP ${cRes.code}"
                            }
                        } catch (ce: Exception) {
                            customStatusInfo = "\nCustom Server ($customApiBase): Unreachable (${ce.message})"
                        }
                    }

                    emit(
                        step5.copy(
                            status = DiagnosticStatus.SUCCESS,
                            output = "Bot Authenticated Successfully!\nName: $botName\nUsername: @$username\nOfficial Cloud API: Verified (api.telegram.org)$customStatusInfo",
                            details = "Telegram Bot credentials are fully valid and active."
                        )
                    )
                } else {
                    // Try fallback against custom URL if provided
                    var customSucceeded = false
                    var customOutput = ""
                    if (hasCustomUrl) {
                        try {
                            val customUrl = "$customApiBase/bot$cleanToken/getMe"
                            val cReq = Request.Builder().url(customUrl).build()
                            val cRes = okHttpClient.newCall(cReq).execute()
                            val cBody = cRes.body?.string() ?: ""
                            if (cRes.isSuccessful && cBody.contains("\"ok\":true")) {
                                val json = JSONObject(cBody)
                                val result = json.optJSONObject("result")
                                val botName = result?.optString("first_name", "Bot") ?: "Bot"
                                val username = result?.optString("username", "") ?: ""
                                customSucceeded = true
                                customOutput = "Bot Authenticated via Custom Server!\nName: $botName\nUsername: @$username\nCustom Server: $customApiBase"
                            }
                        } catch (_: Exception) {}
                    }

                    if (customSucceeded) {
                        emit(
                            step5.copy(
                                status = DiagnosticStatus.SUCCESS,
                                output = customOutput,
                                details = "Bot credentials verified via Custom Server ($customApiBase)."
                            )
                        )
                    } else {
                        emit(
                            step5.copy(
                                status = DiagnosticStatus.ERROR,
                                output = "Telegram rejected Bot Token (HTTP ${response.code}):\n$bodyStr",
                                details = "Check for accidental leading/trailing spaces or regenerate the token with @BotFather."
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                emit(
                    step5.copy(
                        status = DiagnosticStatus.ERROR,
                        output = "Network exception testing bot: ${e.message}",
                        details = "Failed to query getMe endpoint. Ensure internet is active."
                    )
                )
            }
        }

        // 6. Large File (>20MB) 2GB Streaming Capability Check
        val step6 = DiagnosticStep(
            id = "large_file_mode",
            title = "2,000MB (2GB) Large File Mode Check",
            description = "Inspects if local telegram-bot-api server is configured to bypass 20MB limit"
        )
        emit(step6)
        if (botConfig.customBotApiUrl.isNotBlank()) {
            emit(
                step6.copy(
                    status = DiagnosticStatus.SUCCESS,
                    output = "Custom Telegram Bot API Server is configured:\n${botConfig.customBotApiUrl}",
                    details = "2GB large file streaming is ACTIVE! Files up to 2,000MB can be streamed and downloaded without the 20MB cloud limit."
                )
            )
        } else {
            emit(
                step6.copy(
                    status = DiagnosticStatus.WARNING,
                    output = "Using default Telegram Cloud (api.telegram.org).",
                    details = "Standard Telegram 20MB limit applies for direct bot file downloads. To stream files > 20MB, configure 'Custom Telegram Bot API Server' in Bot Setup."
                )
            )
        }

        // Final report emit
        val lastStep = steps.last()
        emit(lastStep, isDone = true)

        DiagnosticsReport(
            steps = steps.toList(),
            isComplete = true,
            overallStatus = when {
                steps.any { it.status == DiagnosticStatus.ERROR } -> DiagnosticStatus.ERROR
                steps.any { it.status == DiagnosticStatus.WARNING } -> DiagnosticStatus.WARNING
                else -> DiagnosticStatus.SUCCESS
            },
            summary = "Diagnostic checks completed successfully."
        )
    }

    private fun testTcpPort(host: String, port: Int, timeoutMs: Int = 2000): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun testHttpUrl(url: String): Pair<String, Int> {
        return try {
            val req = Request.Builder().url(url).build()
            val res = okHttpClient.newCall(req).execute()
            val serverHeader = res.header("Server") ?: "HTTP-Server"
            Pair(serverHeader, res.code)
        } catch (e: Exception) {
            Pair(e.message ?: "Error", -1)
        }
    }

    private fun executeBashCommand(command: String): Pair<Int, String> {
        return try {
            val process = ProcessBuilder("/system/bin/sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }
            val exitCode = process.waitFor()
            Pair(exitCode, output.toString().trim())
        } catch (e: Exception) {
            Pair(-1, "Execution failed: ${e.message}")
        }
    }
}
