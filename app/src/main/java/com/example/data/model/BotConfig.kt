package com.example.data.model

data class BotConfig(
    val botToken: String = "",
    val botUsername: String = "",
    val botName: String = "",
    val telegramApiId: String = "", // from my.telegram.org (for direct large downloads)
    val telegramApiHash: String = "", // from my.telegram.org (for direct large downloads)
    val isBotActive: Boolean = false,
    val serverPort: Int = 8080,
    val customDomain: String = "", // e.g. ngrok or local ip
    val customBotApiUrl: String = "", // e.g. http://localhost:8081 for 2GB local bot-api server
    val adminUserIds: String = "", // comma-separated telegram user IDs
    val autoDeleteAfterDays: Int = 0, // 0 = never
    val welcomeMessage: String = "👋 Hello! Send me any file, video, or audio and I will generate a high-speed direct browser download link and instant web streaming player for you!",
    val isPublicBot: Boolean = true,
    val githubRepoUrl: String = "https://github.com/sdfghjkt2/TeleLink",
    val autoCheckUpdates: Boolean = true
)

data class ServerStats(
    val isRunning: Boolean = false,
    val ipAddress: String = "127.0.0.1",
    val port: Int = 8080,
    val activeConnections: Int = 0,
    val currentSpeedBps: Long = 0L, // Bytes per second
    val totalBytesStreamed: Long = 0L,
    val totalFilesServed: Int = 0,
    val uptimeSeconds: Long = 0L,
    val networkName: String = "Wi-Fi"
)
