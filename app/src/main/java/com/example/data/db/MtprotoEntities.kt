package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.ServerMode

@Entity(tableName = "bots")
data class BotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val token: String,
    val botId: Long,
    val firstName: String,
    val username: String,
    val canJoinGroups: Boolean = true,
    val canReadAllGroupMessages: Boolean = false,
    val supportsInlineQueries: Boolean = false,
    val webhookUrl: String = "",
    val webhookSecretToken: String = "",
    val webhookMaxConnections: Int = 40,
    val commandsJson: String = "[]",
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "server_config")
data class ServerConfigEntity(
    @PrimaryKey val id: Int = 1,
    val port: Int = 8081,
    val host: String = "0.0.0.0",
    val mode: String = ServerMode.LOCAL_SANDBOX.name,
    val selectedDcId: Int = 2,
    val isTestDc: Boolean = false,
    val apiId: String = "35633835",
    val apiHash: String = "a7d8cdf50ddc75cbed4a0f709a2bcd78",
    val maxConnections: Int = 100,
    val simulateLatencyMs: Int = 0,
    val rateLimitPerSec: Int = 0,
    val localModeEnabled: Boolean = true,
    val maxFileSizeMb: Int = 2000,
    val autoStartOnLaunch: Boolean = false,
    val customBotApiUrl: String = "https://api.telegram.org"
)

@Entity(tableName = "api_request_logs")
data class ApiRequestLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val clientIp: String,
    val httpMethod: String,
    val path: String,
    val botToken: String,
    val apiMethod: String,
    val queryParams: String,
    val requestHeaders: String,
    val requestBody: String,
    val statusCode: Int,
    val responseBody: String,
    val latencyMs: Long,
    val isError: Boolean = false,
    val errorMessage: String? = null
)

@Entity(tableName = "sandbox_messages")
data class SandboxMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val botToken: String,
    val chatId: Long,
    val messageId: Long,
    val isFromBot: Boolean,
    val senderName: String,
    val text: String,
    val mediaType: String? = null,
    val mediaCaption: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
