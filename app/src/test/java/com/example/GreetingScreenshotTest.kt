package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.ServerStats
import com.example.ui.components.ServerStatusHero
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val sampleStats = ServerStats(
        isRunning = true,
        ipAddress = "192.168.1.100",
        port = 8080,
        activeConnections = 2,
        currentSpeedBps = 5_242_880L,
        uptimeSeconds = 3600L,
        networkName = "Wi-Fi (Home_5G)"
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        ServerStatusHero(
            stats = sampleStats,
            onToggleServer = {},
            onCopyUrl = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}

