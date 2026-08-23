package com.example.data.model

data class BotConfig(
    val botToken: String = "",
    val botUsername: String = "",
    val botName: String = "",
    val isBotActive: Boolean = false,
    val serverPort: Int = 8080,
    val customDomain: String = "", // e.g. ngrok or local ip
    val adminUserIds: String = "", // comma-separated telegram user IDs
    val autoDeleteAfterDays: Int = 0, // 0 = never
    val welcomeMessage: String = "👋 Hello! Send me any file, video, or audio and I will generate a high-speed direct browser download link and instant web streaming player for you!",
    val isPublicBot: Boolean = true
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
