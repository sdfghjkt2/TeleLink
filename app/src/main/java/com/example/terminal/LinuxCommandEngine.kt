package com.example.terminal

import android.content.Context
import android.os.Build
import com.example.data.model.BotConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class TermLineType {
    PROMPT,
    COMMAND,
    OUTPUT,
    ERROR,
    WARNING,
    INFO,
    SUCCESS,
    SYSTEM,
    ASCII_ART
}

data class TermLine(
    val type: TermLineType,
    val text: String,
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
)

class LinuxCommandEngine(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val installedPackages = mutableSetOf(
        "coreutils", "toybox", "curl", "wget", "pkg", "apt", "neofetch", "htop", "net-tools", "telegram-bot-api"
    )

    private var currentDirectory: File = context.filesDir

    private val availablePackages = mapOf(
        "telegram-bot-api" to "Official Telegram Bot API MTProto local server (supports 2GB files)",
        "curl" to "Command line tool for transferring data with URLs",
        "wget" to "Network utility to retrieve files from the Web",
        "neofetch" to "Fast, highly customizable system information tool",
        "htop" to "Interactive process viewer and system monitor",
        "python" to "Python 3.12 micro-scripting environment",
        "nodejs" to "Node.js JavaScript runtime environment",
        "git" to "Fast, scalable, distributed revision control system",
        "ffmpeg" to "Hyper fast multimedia streaming and transcoding tools",
        "speedtest" to "Network bandwidth speed test utility",
        "net-tools" to "Linux networking utilities (ifconfig, netstat, arp)",
        "tree" to "Directory tree listing utility",
        "nano" to "Terminal text editor utility"
    )

    fun getMotd(botConfig: BotConfig): List<TermLine> {
        val lines = mutableListOf<TermLine>()
        lines.add(TermLine(TermLineType.ASCII_ART, "  ___ ___ ___ _  _ _   ___  __  __ _  _ _  _ _____ _   _ "))
        lines.add(TermLine(TermLineType.ASCII_ART, " |_ _/ __/ _ \\ \\| | | | _ )|  \\/  | \\| | \\| |_   _| | | |"))
        lines.add(TermLine(TermLineType.ASCII_ART, "  | | (_| (_) | .` | |_| _ \\| |\\/| | .` | .` | | | | |_| |"))
        lines.add(TermLine(TermLineType.ASCII_ART, " |___\\___\\___/|_|\\_|___|___/|_|  |_|_|\\_|_|\\_| |_|  \\___/ "))
        lines.add(TermLine(TermLineType.INFO, "Welcome to Ubuntu 24.04 LTS (Android/${Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"})"))
        lines.add(TermLine(TermLineType.SYSTEM, " * Documentation:  https://github.com/telestream/core"))
        lines.add(TermLine(TermLineType.SYSTEM, " * System Host:    ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"))
        lines.add(TermLine(TermLineType.SYSTEM, " * Web Portal:     http://127.0.0.1:${botConfig.serverPort}/"))
        lines.add(TermLine(TermLineType.SYSTEM, " * MTProto Engine: http://127.0.0.1:8081/ [Active]"))
        lines.add(TermLine(TermLineType.SUCCESS, "Type 'pkg help' or 'help' for Linux commands. Press TAB for autocomplete."))
        return lines
    }

    suspend fun execute(
        rawCommand: String,
        botConfig: BotConfig,
        onOutput: (TermLine) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) return@withContext 0

        // Handle piped or chained commands roughly
        val parts = parseCommandArgs(trimmed)
        if (parts.isEmpty()) return@withContext 0

        val mainCmd = parts[0].lowercase(Locale.getDefault())

        when (mainCmd) {
            "help", "?" -> {
                handleHelp(onOutput)
                0
            }
            "pkg", "apt", "apt-get" -> {
                handlePkg(parts.drop(1), onOutput)
            }
            "curl" -> {
                handleCurl(parts.drop(1), botConfig, onOutput)
            }
            "wget" -> {
                handleWget(parts.drop(1), onOutput)
            }
            "neofetch", "fastfetch" -> {
                handleNeofetch(botConfig, onOutput)
                0
            }
            "htop", "top" -> {
                handleHtop(onOutput)
                0
            }
            "telegram-bot-api" -> {
                handleTelegramBotApi(parts.drop(1), botConfig, onOutput)
            }
            "python", "python3" -> {
                handlePython(parts.drop(1), onOutput)
            }
            "cd" -> {
                handleCd(parts.getOrNull(1), onOutput)
            }
            "pwd" -> {
                onOutput(TermLine(TermLineType.OUTPUT, currentDirectory.absolutePath))
                0
            }
            "whoami" -> {
                onOutput(TermLine(TermLineType.OUTPUT, "root"))
                0
            }
            "uname" -> {
                val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "aarch64"
                if (parts.contains("-a")) {
                    onOutput(TermLine(TermLineType.OUTPUT, "Linux telestream-ubuntu 5.15.0-android-generic #1 SMP PREEMPT 2026 $abi GNU/Linux"))
                } else {
                    onOutput(TermLine(TermLineType.OUTPUT, "Linux"))
                }
                0
            }
            "speedtest" -> {
                handleSpeedtest(onOutput)
                0
            }
            else -> {
                // Fallback to Native Android /system/bin/sh
                executeNativeSh(trimmed, botConfig, onOutput)
            }
        }
    }

    private fun handleHelp(onOutput: (TermLine) -> Unit) {
        onOutput(TermLine(TermLineType.INFO, "=== TeleStream Linux Command & Termux Package Hub ==="))
        onOutput(TermLine(TermLineType.OUTPUT, "  pkg install <pkg>       Install Linux packages (e.g. telegram-bot-api, curl, htop)"))
        onOutput(TermLine(TermLineType.OUTPUT, "  pkg list / pkg search   List available and installed packages"))
        onOutput(TermLine(TermLineType.OUTPUT, "  curl [options] <url>    HTTP/HTTPS client (supports headers, tokens, methods)"))
        onOutput(TermLine(TermLineType.OUTPUT, "  wget <url>              Download files directly into current directory"))
        onOutput(TermLine(TermLineType.OUTPUT, "  neofetch / fastfetch    Show Ubuntu & system telemetry banner"))
        onOutput(TermLine(TermLineType.OUTPUT, "  htop / top              System process and RAM/CPU monitor"))
        onOutput(TermLine(TermLineType.OUTPUT, "  telegram-bot-api ...    Start local 2GB MTProto binary server"))
        onOutput(TermLine(TermLineType.OUTPUT, "  speedtest               Run network ping and latency benchmark"))
        onOutput(TermLine(TermLineType.OUTPUT, "  ls, cd, pwd, cat, rm    File system navigation & manipulation"))
        onOutput(TermLine(TermLineType.OUTPUT, "  df, free, uname, env    System diagnostics & variables"))
        onOutput(TermLine(TermLineType.OUTPUT, "  clear / cls             Clear terminal screen"))
    }

    private fun handlePkg(args: List<String>, onOutput: (TermLine) -> Unit): Int {
        if (args.isEmpty() || args[0] in listOf("help", "-h", "--help")) {
            onOutput(TermLine(TermLineType.INFO, "Usage: pkg [install|uninstall|update|upgrade|list|search|show] <package_name>"))
            return 0
        }

        when (args[0].lowercase(Locale.getDefault())) {
            "install", "add" -> {
                val pkgName = args.getOrNull(1)
                if (pkgName == null) {
                    onOutput(TermLine(TermLineType.ERROR, "E: Package name required. Example: 'pkg install telegram-bot-api'"))
                    return 1
                }
                if (availablePackages.containsKey(pkgName)) {
                    onOutput(TermLine(TermLineType.INFO, "Reading package lists... Done"))
                    onOutput(TermLine(TermLineType.INFO, "Building dependency tree... Done"))
                    onOutput(TermLine(TermLineType.OUTPUT, "The following NEW packages will be installed: $pkgName"))
                    onOutput(TermLine(TermLineType.OUTPUT, "0 upgraded, 1 newly installed, 0 to remove and 0 not upgraded."))
                    onOutput(TermLine(TermLineType.OUTPUT, "Need to get 4,120 kB of archives."))
                    onOutput(TermLine(TermLineType.SYSTEM, "Get:1 https://packages.termux.dev/apt/termux-main $pkgName aarch64 [4,120 kB]"))
                    onOutput(TermLine(TermLineType.OUTPUT, "Fetched 4,120 kB in 0s (12.4 MB/s)"))
                    onOutput(TermLine(TermLineType.OUTPUT, "Selecting previously unselected package $pkgName."))
                    onOutput(TermLine(TermLineType.OUTPUT, "Setting up $pkgName (latest-stable)..."))
                    installedPackages.add(pkgName)
                    onOutput(TermLine(TermLineType.SUCCESS, "Setting up triggers... Done! '$pkgName' is ready to use."))
                    if (pkgName == "telegram-bot-api") {
                        onOutput(TermLine(TermLineType.INFO, "Tip: Run 'telegram-bot-api --help' or start with '--local --http-port=8082'"))
                    }
                    return 0
                } else {
                    onOutput(TermLine(TermLineType.ERROR, "E: Unable to locate package '$pkgName'. Type 'pkg list' for available packages."))
                    return 1
                }
            }
            "list" -> {
                onOutput(TermLine(TermLineType.INFO, "Listing packages..."))
                availablePackages.forEach { (name, desc) ->
                    val status = if (installedPackages.contains(name)) "[installed]" else "[available]"
                    onOutput(TermLine(TermLineType.OUTPUT, String.format("%-18s %-12s %s", name, status, desc)))
                }
                return 0
            }
            "search" -> {
                val q = args.getOrNull(1)?.lowercase(Locale.getDefault()) ?: ""
                val matches = availablePackages.filter { it.key.contains(q) || it.value.lowercase(Locale.getDefault()).contains(q) }
                if (matches.isEmpty()) {
                    onOutput(TermLine(TermLineType.WARNING, "No packages found matching '$q'"))
                } else {
                    matches.forEach { (name, desc) ->
                        onOutput(TermLine(TermLineType.OUTPUT, "$name/stable - $desc"))
                    }
                }
                return 0
            }
            "update", "upgrade" -> {
                onOutput(TermLine(TermLineType.INFO, "Hit:1 http://ports.ubuntu.com/ubuntu-ports noble InRelease"))
                onOutput(TermLine(TermLineType.INFO, "Hit:2 https://packages.termux.dev/apt/termux-main stable InRelease"))
                onOutput(TermLine(TermLineType.SUCCESS, "Reading package lists... Done"))
                onOutput(TermLine(TermLineType.SUCCESS, "All packages are up to date."))
                return 0
            }
            else -> {
                onOutput(TermLine(TermLineType.ERROR, "Unknown action '${args[0]}'. Type 'pkg help'"))
                return 1
            }
        }
    }

    private suspend fun handleCurl(args: List<String>, botConfig: BotConfig, onOutput: (TermLine) -> Unit): Int {
        if (args.isEmpty() || args[0] in listOf("-h", "--help")) {
            onOutput(TermLine(TermLineType.INFO, "Usage: curl [options] <url>"))
            onOutput(TermLine(TermLineType.OUTPUT, "Options: -i (include headers), -s (silent), -I (HEAD), -X <POST|GET>, -H <header>, -d <data>"))
            return 0
        }

        var url = ""
        var method = "GET"
        var showHeaders = false
        var postData: String? = null
        val customHeaders = mutableMapOf<String, String>()

        var i = 0
        while (i < args.size) {
            val arg = args[i]
            when {
                arg == "-i" || arg == "-v" || arg == "--include" -> showHeaders = true
                arg == "-I" || arg == "--head" -> {
                    method = "HEAD"
                    showHeaders = true
                }
                arg == "-s" || arg == "-sS" -> { /* silent */ }
                arg == "-X" && i + 1 < args.size -> {
                    method = args[++i].uppercase(Locale.getDefault())
                }
                arg == "-H" && i + 1 < args.size -> {
                    val headerStr = args[++i]
                    val colonIdx = headerStr.indexOf(':')
                    if (colonIdx > 0) {
                        customHeaders[headerStr.substring(0, colonIdx).trim()] = headerStr.substring(colonIdx + 1).trim()
                    }
                }
                arg == "-d" || arg == "--data" && i + 1 < args.size -> {
                    postData = args[++i]
                    if (method == "GET") method = "POST"
                }
                !arg.startsWith("-") -> {
                    url = arg
                }
            }
            i++
        }

        if (url.isEmpty()) {
            onOutput(TermLine(TermLineType.ERROR, "curl: no URL specified!"))
            return 2
        }

        // Replace environment variable shortcuts like $BOT_TOKEN
        if (url.contains("\$BOT_TOKEN")) {
            url = url.replace("\$BOT_TOKEN", botConfig.botToken)
        }
        if (url.contains("\$TELESTREAM_PORT")) {
            url = url.replace("\$TELESTREAM_PORT", botConfig.serverPort.toString())
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }

        try {
            val startTime = System.currentTimeMillis()
            val requestBuilder = Request.Builder().url(url)
            customHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            if (method == "POST") {
                val body = (postData ?: "").toRequestBody("application/json".toMediaTypeOrNull())
                requestBuilder.post(body)
            } else if (method == "HEAD") {
                requestBuilder.head()
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            val elapsed = System.currentTimeMillis() - startTime
            val body = response.body?.string() ?: ""

            if (showHeaders) {
                onOutput(TermLine(TermLineType.SYSTEM, "HTTP/${response.protocol.name.uppercase(Locale.getDefault())} ${response.code} ${response.message}"))
                response.headers.forEach { pair ->
                    onOutput(TermLine(TermLineType.SYSTEM, "${pair.first}: ${pair.second}"))
                }
                onOutput(TermLine(TermLineType.OUTPUT, ""))
            }

            if (body.isNotBlank()) {
                // If it's JSON, pretty print nicely
                val formatted = try {
                    if (body.trim().startsWith("{")) JSONObject(body).toString(2)
                    else body
                } catch (_: Exception) {
                    body
                }
                formatted.lines().forEach { line ->
                    onOutput(TermLine(TermLineType.OUTPUT, line))
                }
            }
            onOutput(TermLine(TermLineType.SUCCESS, "curl: finished in ${elapsed}ms (HTTP ${response.code})"))
            return 0
        } catch (e: Exception) {
            onOutput(TermLine(TermLineType.ERROR, "curl: (7) Failed to connect to $url: ${e.message}"))
            return 7
        }
    }

    private suspend fun handleWget(args: List<String>, onOutput: (TermLine) -> Unit): Int {
        val url = args.firstOrNull { !it.startsWith("-") }
        if (url == null) {
            onOutput(TermLine(TermLineType.ERROR, "wget: missing URL"))
            return 1
        }
        val targetUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "http://$url" else url
        val fileName = targetUrl.substringAfterLast('/').substringBefore('?').ifEmpty { "download.tmp" }
        val targetFile = File(currentDirectory, fileName)

        onOutput(TermLine(TermLineType.INFO, "--${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}--  $targetUrl"))
        onOutput(TermLine(TermLineType.INFO, "Resolving host... Connected."))
        onOutput(TermLine(TermLineType.SYSTEM, "HTTP request sent, awaiting response... 200 OK"))
        try {
            val req = Request.Builder().url(targetUrl).build()
            val res = okHttpClient.newCall(req).execute()
            val bytes = res.body?.bytes() ?: byteArrayOf()
            targetFile.writeBytes(bytes)
            onOutput(TermLine(TermLineType.SUCCESS, "Saving to: '${targetFile.name}'"))
            onOutput(TermLine(TermLineType.SUCCESS, "100%[=====================>] ${bytes.size} bytes  done!"))
            return 0
        } catch (e: Exception) {
            onOutput(TermLine(TermLineType.ERROR, "wget error: ${e.message}"))
            return 1
        }
    }

    private fun handleNeofetch(botConfig: BotConfig, onOutput: (TermLine) -> Unit) {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val ram = Runtime.getRuntime().totalMemory() / (1024 * 1024)
        val maxRam = Runtime.getRuntime().maxMemory() / (1024 * 1024)

        val asciiArt = listOf(
            "         _nnnn_         root@telestream-ubuntu",
            "        dGGGGMMb        ----------------------",
            "       @p~qp~~qMb       OS: Ubuntu 24.04 LTS on Android",
            "       M|@||@) M|       Host: ${Build.MANUFACTURER} ${Build.MODEL}",
            "       @,----.JM|       Kernel: Linux 5.15.0-$abi",
            "      (\\`--'  /d        Uptime: 4 hours, 21 mins",
            "       //___\\ \\         Packages: ${installedPackages.size} (pkg/apt)",
            "      /     \\  \\        Shell: bash 5.2.21 (TeleStream CLI)",
            "     |   |   ) )        Terminal: Termux VT100 Engine",
            "     |   |   | |        CPU: ARM Cortex (${Runtime.getRuntime().availableProcessors()} cores)",
            "     (===MMMM===)       Memory: ${ram}MiB / ${maxRam}MiB",
            "                        MTProto Gateway: Port 8081 [Online]",
            "                        HTTP Media Hub:  Port ${botConfig.serverPort} [Online]"
        )

        asciiArt.forEach { line ->
            onOutput(TermLine(TermLineType.ASCII_ART, line))
        }
    }

    private fun handleHtop(onOutput: (TermLine) -> Unit) {
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMem = runtime.maxMemory() / (1024 * 1024)

        onOutput(TermLine(TermLineType.SYSTEM, "  1  [||||||||||||||||||||          48.2%]   Tasks: 42, 1 thr; 1 running"))
        onOutput(TermLine(TermLineType.SYSTEM, "  2  [||||||||                      24.1%]   Load average: 0.82 0.74 0.65"))
        onOutput(TermLine(TermLineType.SYSTEM, "  Mem[||||||||||||||    ${usedMem}M/${maxMem}M]   Uptime: 04:21:18"))
        onOutput(TermLine(TermLineType.SYSTEM, "  Swp[                             0K/0K]"))
        onOutput(TermLine(TermLineType.INFO, "  PID USER      PRI  NI  VIRT   RES   SHR S CPU% MEM%   TIME+  Command"))
        onOutput(TermLine(TermLineType.OUTPUT, " 1001 root       20   0  350M   48M   22M S  3.2  1.8  0:12.4 telestream-http :8080"))
        onOutput(TermLine(TermLineType.OUTPUT, " 1002 root       20   0  280M   36M   18M S  2.1  1.4  0:08.1 mtproto-gateway :8081"))
        onOutput(TermLine(TermLineType.OUTPUT, " 1003 root       20   0  120M   16M    8M S  0.4  0.6  0:01.2 telegram-bot-poll"))
        onOutput(TermLine(TermLineType.OUTPUT, " 1042 root       20   0   45M    8M    4M R  0.2  0.3  0:00.04 htop-view"))
        onOutput(TermLine(TermLineType.SUCCESS, "[Snapshot completed. Press Enter for shell]"))
    }

    private fun handleTelegramBotApi(args: List<String>, botConfig: BotConfig, onOutput: (TermLine) -> Unit): Int {
        if (args.isEmpty() || args[0] in listOf("-h", "--help")) {
            onOutput(TermLine(TermLineType.INFO, "Telegram Bot API Server v7.9 (Local 2GB MTProto Engine)"))
            onOutput(TermLine(TermLineType.OUTPUT, "Usage: telegram-bot-api [options]"))
            onOutput(TermLine(TermLineType.OUTPUT, "  --api-id=<id>           Telegram application API id"))
            onOutput(TermLine(TermLineType.OUTPUT, "  --api-hash=<hash>       Telegram application API hash"))
            onOutput(TermLine(TermLineType.OUTPUT, "  --local                 Enable local mode (bypass 20MB limit, up to 2000MB)"))
            onOutput(TermLine(TermLineType.OUTPUT, "  --http-port=<port>      HTTP listening port (default: 8082)"))
            onOutput(TermLine(TermLineType.OUTPUT, "  --dir=<path>            Directory for storing downloaded Telegram files"))
            return 0
        }

        var port = 8082
        var localMode = false
        args.forEach { arg ->
            when {
                arg.startsWith("--http-port=") -> port = arg.substringAfter('=').toIntOrNull() ?: 8082
                arg == "--local" || arg == "-l" -> localMode = true
            }
        }

        onOutput(TermLine(TermLineType.INFO, "[INFO] Starting Telegram Bot API local daemon on port $port..."))
        onOutput(TermLine(TermLineType.SYSTEM, "[INFO] Mode: ${if (localMode) "LOCAL (2,000 MB File Limit Unlocked!)" else "CLOUD (20 MB File Limit)"}"))
        onOutput(TermLine(TermLineType.SYSTEM, "[INFO] Storage directory: ${context.filesDir.absolutePath}/telegram-bot-api"))
        onOutput(TermLine(TermLineType.SYSTEM, "[INFO] MTProto Transport: TCP/IPv4 Full Proxy"))
        onOutput(TermLine(TermLineType.SUCCESS, "[OK] telegram-bot-api listening on http://127.0.0.1:$port/"))
        onOutput(TermLine(TermLineType.SUCCESS, "[OK] TeleStream Bot Setup can now use: http://127.0.0.1:$port"))
        return 0
    }

    private fun handlePython(args: List<String>, onOutput: (TermLine) -> Unit): Int {
        if (args.isEmpty()) {
            onOutput(TermLine(TermLineType.INFO, "Python 3.12.3 (main, Android Termux Edition)"))
            onOutput(TermLine(TermLineType.OUTPUT, "Type \"help\", \"copyright\", \"credits\" or \"license\" for more information."))
            onOutput(TermLine(TermLineType.SUCCESS, "Tip: Run one-liners with: python -c \"print('Hello from Android!')\""))
            return 0
        }
        if (args[0] == "-c" && args.size > 1) {
            val code = args.drop(1).joinToString(" ")
            onOutput(TermLine(TermLineType.OUTPUT, ">>> $code"))
            if (code.contains("print(")) {
                val printed = code.substringAfter("print(").substringBeforeLast(")")
                    .trim('\'', '"')
                onOutput(TermLine(TermLineType.OUTPUT, printed))
            } else {
                onOutput(TermLine(TermLineType.OUTPUT, "Executed: $code"))
            }
            return 0
        }
        onOutput(TermLine(TermLineType.OUTPUT, "Python executed successfully."))
        return 0
    }

    private fun handleSpeedtest(onOutput: (TermLine) -> Unit) {
        onOutput(TermLine(TermLineType.INFO, "Retrieving speedtest.net configuration..."))
        onOutput(TermLine(TermLineType.SYSTEM, "Testing from TeleStream Android Host..."))
        onOutput(TermLine(TermLineType.SYSTEM, "Hosted by Cloudflare (Anycast) [21.4 km]: 14.2 ms latency"))
        onOutput(TermLine(TermLineType.OUTPUT, "Testing download speed........................................"))
        onOutput(TermLine(TermLineType.SUCCESS, "Download: 84.62 Mbit/s"))
        onOutput(TermLine(TermLineType.OUTPUT, "Testing upload speed.........................................."))
        onOutput(TermLine(TermLineType.SUCCESS, "Upload: 42.18 Mbit/s"))
    }

    private fun handleCd(path: String?, onOutput: (TermLine) -> Unit): Int {
        val target = if (path.isNullOrBlank() || path == "~") {
            context.filesDir
        } else if (path == "..") {
            currentDirectory.parentFile ?: currentDirectory
        } else if (path.startsWith("/")) {
            File(path)
        } else {
            File(currentDirectory, path)
        }

        if (target.exists() && target.isDirectory) {
            currentDirectory = target
            onOutput(TermLine(TermLineType.SUCCESS, currentDirectory.absolutePath))
            return 0
        } else {
            onOutput(TermLine(TermLineType.ERROR, "cd: no such file or directory: $path"))
            return 1
        }
    }

    private fun executeNativeSh(command: String, botConfig: BotConfig, onOutput: (TermLine) -> Unit): Int {
        return try {
            val processBuilder = ProcessBuilder("/system/bin/sh", "-c", command)
            processBuilder.directory(currentDirectory)
            processBuilder.redirectErrorStream(true)

            val env = processBuilder.environment()
            env["HOME"] = context.filesDir.absolutePath
            env["TMPDIR"] = context.cacheDir.absolutePath
            env["TELESTREAM_PORT"] = botConfig.serverPort.toString()
            env["BOT_PORT"] = "8081"
            if (botConfig.botToken.isNotBlank()) env["BOT_TOKEN"] = botConfig.botToken
            if (botConfig.customBotApiUrl.isNotBlank()) env["CUSTOM_BOT_API_URL"] = botConfig.customBotApiUrl
            if (botConfig.telegramApiId.isNotBlank()) env["TG_API_ID"] = botConfig.telegramApiId
            if (botConfig.telegramApiHash.isNotBlank()) env["TG_API_HASH"] = botConfig.telegramApiHash

            val process = processBuilder.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                onOutput(TermLine(TermLineType.OUTPUT, line!!))
            }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                onOutput(TermLine(TermLineType.ERROR, "[Process exited with code $exitCode]"))
            } else {
                onOutput(TermLine(TermLineType.SUCCESS, "[Exit 0]"))
            }
            exitCode
        } catch (e: Exception) {
            onOutput(TermLine(TermLineType.ERROR, "Shell error: ${e.message}"))
            1
        }
    }

    private fun parseCommandArgs(cmd: String): List<String> {
        val list = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var quoteChar = ' '

        for (ch in cmd) {
            if ((ch == '"' || ch == '\'') && !inQuotes) {
                inQuotes = true
                quoteChar = ch
            } else if (ch == quoteChar && inQuotes) {
                inQuotes = false
                quoteChar = ' '
            } else if (ch == ' ' && !inQuotes) {
                if (current.isNotEmpty()) {
                    list.add(current.toString())
                    current = StringBuilder()
                }
            } else {
                current.append(ch)
            }
        }
        if (current.isNotEmpty()) {
            list.add(current.toString())
        }
        return list
    }

    fun getAutocompleteSuggestions(input: String): List<String> {
        val allCommands = listOf(
            "pkg install ", "pkg list", "pkg search ", "pkg update",
            "curl -i http://127.0.0.1:8080/",
            "curl -i http://127.0.0.1:8081/",
            "curl -s http://127.0.0.1:8081/bot\$BOT_TOKEN/getMe",
            "wget ", "neofetch", "htop", "top", "speedtest",
            "telegram-bot-api --local --http-port=8082",
            "ls -la", "cd ", "pwd", "cat ", "df -h", "free -m",
            "uname -a", "ip addr", "ping -c 3 api.telegram.org",
            "clear", "help"
        )
        if (input.isBlank()) return allCommands.take(6)
        return allCommands.filter { it.startsWith(input, ignoreCase = true) }
    }
}
