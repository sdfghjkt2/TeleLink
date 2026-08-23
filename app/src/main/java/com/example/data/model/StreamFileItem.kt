package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FileCategory {
    VIDEO,
    AUDIO,
    DOCUMENT,
    IMAGE,
    ARCHIVE,
    OTHER
}

@Entity(tableName = "stream_files")
data class StreamFileItem(
    @PrimaryKey val id: String, // Unique token e.g. "tg_982749283"
    val telegramFileId: String,
    val telegramFileUniqueId: String = "",
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val category: FileCategory = FileCategory.DOCUMENT,
    val telegramChatId: Long = 0L,
    val telegramMessageId: Long = 0L,
    val uploaderName: String = "User",
    val createdAt: Long = System.currentTimeMillis(),
    val downloadCount: Int = 0,
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val isLocalFile: Boolean = false,
    val localFilePath: String? = null,
    val customTag: String = ""
)
