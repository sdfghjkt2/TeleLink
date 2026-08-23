package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LogLevel {
    INFO,
    SUCCESS,
    WARN,
    ERROR
}

@Entity(tableName = "server_logs")
data class ServerLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val method: String, // "GET", "STREAM", "BOT", "SYSTEM"
    val pathOrAction: String,
    val clientIp: String = "",
    val statusCode: Int = 200,
    val message: String
)
