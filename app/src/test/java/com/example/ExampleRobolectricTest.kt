package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.FileCategory
import com.example.util.NetworkUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("TeleStream", appName)
  }

  @Test
  fun `test byte formatting`() {
    assertEquals("0 B", NetworkUtils.formatBytes(0L))
    assertEquals("1 KB", NetworkUtils.formatBytes(1024L))
    assertEquals("1 MB", NetworkUtils.formatBytes(1024 * 1024L))
    assertEquals("1.5 GB", NetworkUtils.formatBytes((1.5 * 1024 * 1024 * 1024).toLong()))
  }

  @Test
  fun `test category detection`() {
    assertEquals(FileCategory.VIDEO, NetworkUtils.detectCategory("movie.mp4", "video/mp4"))
    assertEquals(FileCategory.AUDIO, NetworkUtils.detectCategory("song.mp3", "audio/mpeg"))
    assertEquals(FileCategory.DOCUMENT, NetworkUtils.detectCategory("report.pdf", "application/pdf"))
  }

  @Test
  fun `test server stats state`() {
    val stats = com.example.data.model.ServerStats(
      isRunning = true,
      ipAddress = "192.168.1.50",
      port = 8080,
      activeConnections = 3,
      currentSpeedBps = 1048576L,
      uptimeSeconds = 120L,
      networkName = "Wi-Fi (Home)"
    )
    assertEquals(true, stats.isRunning)
    assertEquals(8080, stats.port)
    assertEquals(3, stats.activeConnections)
  }

  @Test
  fun `test version comparison logic`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val updater = com.example.util.GitHubUpdateManager(context)

    assertEquals(true, updater.isVersionNewer("v1.1", "1.0"))
    assertEquals(true, updater.isVersionNewer("v2.0.0", "1.9.9"))
    assertEquals(true, updater.isVersionNewer("v1.0.1", "1.0.0"))
    assertEquals(false, updater.isVersionNewer("v1.0", "1.0"))
    assertEquals(false, updater.isVersionNewer("v1.0", "1.1"))
  }
}

