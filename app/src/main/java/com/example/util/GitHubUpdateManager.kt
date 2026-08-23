package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.model.AppReleaseInfo
import com.example.data.model.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class GitHubUpdateManager(private val context: Context) {

    companion object {
        const val DEFAULT_REPO = "https://github.com/sdfghjkt2/TeleLink"
        const val DEFAULT_REPO_OWNER = "sdfghjkt2"
        const val DEFAULT_REPO_NAME = "TeleLink"
        private const val TAG = "GitHubUpdateManager"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    val currentVersionName: String = try {
        BuildConfig.VERSION_NAME.ifBlank { "1.0" }
    } catch (e: Exception) {
        "1.0"
    }

    val currentVersionCode: Int = try {
        BuildConfig.VERSION_CODE
    } catch (e: Exception) {
        1
    }

    /**
     * Check GitHub for the latest release from the repository.
     */
    suspend fun checkForUpdates(repoUrl: String = DEFAULT_REPO): Result<AppReleaseInfo?> = withContext(Dispatchers.IO) {
        _updateStatus.value = UpdateStatus.Checking
        try {
            val (owner, repo) = parseRepoOwnerAndName(repoUrl)
            val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"

            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "TeleLink-Android-Updater/$currentVersionName")
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val jsonStr = response.body?.string() ?: ""
                if (jsonStr.isNotBlank()) {
                    val releaseJson = JSONObject(jsonStr)
                    val tagName = releaseJson.optString("tag_name", "v1.0")
                    val releaseName = releaseJson.optString("name", tagName)
                    val body = releaseJson.optString("body", "No release notes provided.")
                    val publishedAt = releaseJson.optString("published_at", "")
                    val htmlUrl = releaseJson.optString("html_url", repoUrl)

                    // Find .apk asset in release assets
                    var apkDownloadUrl = ""
                    var apkSize = 0L

                    val assets = releaseJson.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkDownloadUrl = asset.optString("browser_download_url", "")
                                apkSize = asset.optLong("size", 0L)
                                break
                            }
                        }
                    }

                    // Fallback to standard release direct apk download url if no specific asset matched
                    if (apkDownloadUrl.isBlank()) {
                        apkDownloadUrl = "https://github.com/$owner/$repo/releases/latest/download/app-release.apk"
                    }

                    val isNewer = isVersionNewer(tagName, currentVersionName)
                    val releaseInfo = AppReleaseInfo(
                        tagName = tagName,
                        releaseName = releaseName,
                        releaseNotes = body,
                        publishedAt = publishedAt,
                        apkDownloadUrl = apkDownloadUrl,
                        apkSize = apkSize,
                        htmlUrl = htmlUrl,
                        isNewer = isNewer,
                        targetVersionName = tagName.removePrefix("v").removePrefix("V")
                    )

                    if (isNewer) {
                        _updateStatus.value = UpdateStatus.UpdateAvailable(releaseInfo)
                    } else {
                        _updateStatus.value = UpdateStatus.UpToDate(currentVersionName, System.currentTimeMillis())
                    }

                    return@withContext Result.success(releaseInfo)
                }
            } else if (response.code == 404) {
                // No release published yet, check tags endpoint
                val tagsUrl = "https://api.github.com/repos/$owner/$repo/tags"
                val tagsRequest = Request.Builder()
                    .url(tagsUrl)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "TeleLink-Android-Updater/$currentVersionName")
                    .build()
                val tagsResponse = client.newCall(tagsRequest).execute()

                if (tagsResponse.isSuccessful) {
                    val tagsJson = tagsResponse.body?.string() ?: "[]"
                    val tagArray = JSONArray(tagsJson)
                    if (tagArray.length() > 0) {
                        val firstTag = tagArray.getJSONObject(0).optString("name", "v1.0")
                        val isNewer = isVersionNewer(firstTag, currentVersionName)
                        val fallbackRelease = AppReleaseInfo(
                            tagName = firstTag,
                            releaseName = "Release $firstTag",
                            releaseNotes = "Latest build from GitHub repository $owner/$repo.",
                            publishedAt = "",
                            apkDownloadUrl = "https://github.com/$owner/$repo/releases/download/$firstTag/app-release.apk",
                            apkSize = 0L,
                            htmlUrl = "https://github.com/$owner/$repo/tree/$firstTag",
                            isNewer = isNewer,
                            targetVersionName = firstTag.removePrefix("v").removePrefix("V")
                        )
                        if (isNewer) {
                            _updateStatus.value = UpdateStatus.UpdateAvailable(fallbackRelease)
                        } else {
                            _updateStatus.value = UpdateStatus.UpToDate(currentVersionName, System.currentTimeMillis())
                        }
                        return@withContext Result.success(fallbackRelease)
                    }
                }

                // If no releases/tags exist yet on repo, report up-to-date
                _updateStatus.value = UpdateStatus.UpToDate(currentVersionName, System.currentTimeMillis())
                return@withContext Result.success(null)
            } else {
                val errorMsg = "GitHub check returned HTTP ${response.code}: ${response.message}"
                _updateStatus.value = UpdateStatus.Error(errorMsg)
                return@withContext Result.failure(Exception(errorMsg))
            }

            _updateStatus.value = UpdateStatus.UpToDate(currentVersionName, System.currentTimeMillis())
            Result.success(null)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            val msg = e.localizedMessage ?: "Failed to connect to GitHub"
            _updateStatus.value = UpdateStatus.Error(msg)
            Result.failure(e)
        }
    }

    /**
     * Downloads the APK file from GitHub release with real-time byte progress & speed calculation.
     */
    suspend fun downloadUpdate(release: AppReleaseInfo): Result<File> = withContext(Dispatchers.IO) {
        try {
            _updateStatus.value = UpdateStatus.Downloading(
                progress = 0f,
                bytesDownloaded = 0L,
                totalBytes = release.apkSize,
                speedBps = 0L,
                release = release
            )

            val request = Request.Builder()
                .url(release.apkDownloadUrl)
                .header("User-Agent", "TeleLink-Android-Updater/$currentVersionName")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = "Download failed: HTTP ${response.code} (${response.message})"
                _updateStatus.value = UpdateStatus.Error(err)
                return@withContext Result.failure(Exception(err))
            }

            val body = response.body ?: throw Exception("Empty response body from update server")
            val totalBytes = if (release.apkSize > 0) release.apkSize else body.contentLength()

            val updatesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }

            val safeTag = release.tagName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val apkFile = File(updatesDir, "TeleLink_update_$safeTag.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(apkFile)

            val buffer = ByteArray(32 * 1024)
            var bytesDownloaded = 0L
            var read: Int
            var lastProgressTime = System.currentTimeMillis()
            var bytesSinceLastCalc = 0L
            var currentSpeedBps = 0L

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesDownloaded += read
                        bytesSinceLastCalc += read

                        val now = System.currentTimeMillis()
                        val diff = now - lastProgressTime
                        if (diff >= 400) {
                            currentSpeedBps = (bytesSinceLastCalc * 1000L) / diff
                            lastProgressTime = now
                            bytesSinceLastCalc = 0L

                            val progress = if (totalBytes > 0) {
                                (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0.5f
                            }

                            _updateStatus.value = UpdateStatus.Downloading(
                                progress = progress,
                                bytesDownloaded = bytesDownloaded,
                                totalBytes = totalBytes,
                                speedBps = currentSpeedBps,
                                release = release
                            )
                        }
                    }
                    output.flush()
                }
            }

            _updateStatus.value = UpdateStatus.ReadyToInstall(apkFile, release)
            Result.success(apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading update APK", e)
            val msg = e.localizedMessage ?: "Failed to download update APK"
            _updateStatus.value = UpdateStatus.Error(msg)
            Result.failure(e)
        }
    }

    /**
     * Launches the Android Package Installer for the downloaded APK.
     */
    fun installUpdate(apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                _updateStatus.value = UpdateStatus.Error("APK file not found or empty.")
                return Result.failure(Exception("APK file invalid"))
            }

            _updateStatus.value = UpdateStatus.Installing

            // Check Unknown Sources Permission for Android O+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            context.startActivity(installIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package installer", e)
            val msg = e.localizedMessage ?: "Failed to launch package installer"
            _updateStatus.value = UpdateStatus.Error(msg)
            Result.failure(e)
        }
    }

    fun resetStatus() {
        _updateStatus.value = UpdateStatus.Idle
    }

    /**
     * Checks whether the remote release version tag is strictly newer than current app version.
     */
    fun isVersionNewer(remoteTag: String, currentVersion: String): Boolean {
        val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V")
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")

        if (cleanRemote == cleanCurrent) return false

        val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }

        // If numeric parts are identical, lexicographical check
        return cleanRemote.compareTo(cleanCurrent, ignoreCase = true) > 0
    }

    private fun parseRepoOwnerAndName(url: String): Pair<String, String> {
        val clean = url.trim().removeSuffix("/").removeSuffix(".git")
        val segments = clean.split("/")
        if (segments.size >= 2) {
            val repo = segments.last()
            val owner = segments[segments.size - 2]
            if (owner.isNotBlank() && repo.isNotBlank() && !owner.contains("github.com", ignoreCase = true)) {
                return Pair(owner, repo)
            }
        }
        return Pair(DEFAULT_REPO_OWNER, DEFAULT_REPO_NAME)
    }
}
