package com.example.data.model

import java.io.File

data class AppReleaseInfo(
    val tagName: String,
    val releaseName: String,
    val releaseNotes: String,
    val publishedAt: String,
    val apkDownloadUrl: String,
    val apkSize: Long = 0L,
    val htmlUrl: String = "https://github.com/sdfghjkt2/TeleLink",
    val isNewer: Boolean = false,
    val targetVersionName: String = ""
)

sealed interface UpdateStatus {
    object Idle : UpdateStatus
    object Checking : UpdateStatus
    data class UpdateAvailable(val release: AppReleaseInfo) : UpdateStatus
    data class UpToDate(val version: String, val checkedAt: Long) : UpdateStatus
    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedBps: Long,
        val release: AppReleaseInfo
    ) : UpdateStatus
    data class ReadyToInstall(
        val apkFile: File,
        val release: AppReleaseInfo
    ) : UpdateStatus
    object Installing : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}
