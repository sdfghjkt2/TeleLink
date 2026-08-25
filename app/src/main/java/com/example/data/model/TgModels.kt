package com.example.data.model

import com.squareup.moshi.JsonClass

enum class ServerMode {
    LOCAL_SANDBOX,   // Full local mock Telegram engine with virtual chats & instant response simulation
    MTPROTO_GATEWAY  // Proxies Bot API calls to live Telegram MTProto cloud servers with local server capabilities (2GB files, logging)
}

@JsonClass(generateAdapter = true)
data class TgResponse<T>(
    val ok: Boolean,
    val result: T? = null,
    val error_code: Int? = null,
    val description: String? = null,
    val parameters: TgResponseParameters? = null
)

@JsonClass(generateAdapter = true)
data class TgResponseParameters(
    val migrate_to_chat_id: Long? = null,
    val retry_after: Int? = null
)

@JsonClass(generateAdapter = true)
data class TgUser(
    val id: Long,
    val is_bot: Boolean,
    val first_name: String,
    val last_name: String? = null,
    val username: String? = null,
    val language_code: String? = "en",
    val can_join_groups: Boolean? = true,
    val can_read_all_group_messages: Boolean? = false,
    val supports_inline_queries: Boolean? = false
)

@JsonClass(generateAdapter = true)
data class TgChat(
    val id: Long,
    val type: String = "private", // "private", "group", "supergroup", "channel"
    val title: String? = null,
    val username: String? = null,
    val first_name: String? = null,
    val last_name: String? = null
)

@JsonClass(generateAdapter = true)
data class TgMessage(
    val message_id: Long,
    val from: TgUser? = null,
    val sender_chat: TgChat? = null,
    val date: Long = System.currentTimeMillis() / 1000,
    val chat: TgChat,
    val forward_from: TgUser? = null,
    val forward_date: Long? = null,
    val reply_to_message: TgMessage? = null,
    val text: String? = null,
    val caption: String? = null
)

@JsonClass(generateAdapter = true)
data class TgUpdate(
    val update_id: Long,
    val message: TgMessage? = null,
    val edited_message: TgMessage? = null,
    val channel_post: TgMessage? = null,
    val edited_channel_post: TgMessage? = null,
    val callback_query: TgCallbackQuery? = null
)

@JsonClass(generateAdapter = true)
data class TgCallbackQuery(
    val id: String,
    val from: TgUser,
    val message: TgMessage? = null,
    val data: String? = null
)

@JsonClass(generateAdapter = true)
data class TgWebhookInfo(
    val url: String,
    val has_custom_certificate: Boolean = false,
    val pending_update_count: Int = 0,
    val ip_address: String? = null,
    val last_error_date: Long? = null,
    val last_error_message: String? = null,
    val max_connections: Int? = 40,
    val allowed_updates: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class TgBotCommand(
    val command: String,
    val description: String
)

data class MtprotoDcInfo(
    val id: Int,
    val name: String,
    val location: String,
    val ip: String,
    val port: Int = 443,
    val isTest: Boolean = false
) {
    companion object {
        val DEFAULT_DCS = listOf(
            MtprotoDcInfo(1, "DC1 (Production)", "Miami, USA", "149.154.175.50", 443, false),
            MtprotoDcInfo(2, "DC2 (Production)", "Amsterdam, NL", "149.154.167.51", 443, false),
            MtprotoDcInfo(3, "DC3 (Production)", "Miami, USA", "149.154.175.100", 443, false),
            MtprotoDcInfo(4, "DC4 (Production)", "Amsterdam, NL", "149.154.167.91", 443, false),
            MtprotoDcInfo(5, "DC5 (Production)", "Singapore", "91.108.56.165", 443, false),
            MtprotoDcInfo(1, "DC1 (Test Environment)", "Miami, USA", "149.154.175.10", 443, true),
            MtprotoDcInfo(2, "DC2 (Test Environment)", "Amsterdam, NL", "149.154.167.40", 443, true),
            MtprotoDcInfo(3, "DC3 (Test Environment)", "Miami, USA", "149.154.175.11", 443, true)
        )
    }
}

data class ServerRuntimeStats(
    val isRunning: Boolean = false,
    val port: Int = 8081,
    val localIp: String = "127.0.0.1",
    val activeConnections: Int = 0,
    val totalRequests: Long = 0,
    val requestsPerSec: Double = 0.0,
    val errorCount: Long = 0,
    val uptimeSeconds: Long = 0,
    val avgLatencyMs: Long = 0,
    val lastRequestTimestamp: Long = 0
)
