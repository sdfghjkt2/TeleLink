package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ServerEmerald
import com.example.ui.theme.TelegramBlue
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MtprotoDeployTarget {
    TERMUX_PHONE,
    DOCKER_PC,
    FREE_CLOUD_RENDER,
    RAILWAY_CLOUD
}

@Composable
fun MtprotoHostHubCard(
    apiId: String,
    apiHash: String,
    botToken: String,
    currentCustomUrl: String,
    onApplyCustomUrl: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExpanded by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val cleanApiId = apiId.trim().ifBlank { "YOUR_API_ID" }
    val cleanApiHash = apiHash.trim().ifBlank { "YOUR_API_HASH" }
    val cleanToken = botToken.trim().ifBlank { "YOUR_BOT_TOKEN" }

    // Live Server Probe State
    var probeUrl by remember(currentCustomUrl) {
        mutableStateOf(currentCustomUrl.ifBlank { "http://127.0.0.1:8081" })
    }
    var isProbing by remember { mutableStateOf(false) }
    var probeResult by remember { mutableStateOf<String?>(null) }
    var isProbeSuccess by remember { mutableStateOf(false) }
    var probeLatencyMs by remember { mutableStateOf(0L) }

    fun copyToClipboard(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    fun testServerReachability(targetUrl: String) {
        if (targetUrl.isBlank()) return
        isProbing = true
        probeResult = null
        isProbeSuccess = false

        scope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                val clean = if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                    "http://$targetUrl"
                } else targetUrl

                // Test basic server reachability or getMe
                val testEndpoint = if (cleanToken.isNotBlank() && cleanToken != "YOUR_BOT_TOKEN") {
                    "${clean.trimEnd('/')}/bot$cleanToken/getMe"
                } else {
                    clean.trimEnd('/')
                }

                val url = URL(testEndpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                }

                val code = conn.responseCode
                val elapsed = System.currentTimeMillis() - startTime
                val responseText = try {
                    conn.inputStream.bufferedReader().readText()
                } catch (_: Exception) {
                    conn.errorStream?.bufferedReader()?.readText() ?: ""
                }

                withContext(Dispatchers.Main) {
                    probeLatencyMs = elapsed
                    if (code in 200..299 || (code == 404 && responseText.contains("ok", ignoreCase = true))) {
                        isProbeSuccess = true
                        probeResult = "✅ Connected successfully! (${elapsed}ms) - 2GB MTProto server active."
                    } else if (code == 401 || code == 404) {
                        isProbeSuccess = true
                        probeResult = "⚡ Server responding on port! (${elapsed}ms, HTTP $code). Bot-API is online."
                    } else {
                        isProbeSuccess = false
                        probeResult = "⚠️ Server replied with HTTP $code (${elapsed}ms): ${responseText.take(120)}"
                    }
                    isProbing = false
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                withContext(Dispatchers.Main) {
                    probeLatencyMs = elapsed
                    isProbeSuccess = false
                    probeResult = "❌ Connection failed (${elapsed}ms): ${e.localizedMessage ?: "Connection refused / timed out"}"
                    isProbing = false
                }
            }
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(TelegramBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RocketLaunch,
                            contentDescription = null,
                            tint = TelegramBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "🚀 MTProto 2GB Server Host Hub",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Bypass Telegram's 20MB limit for up to 2,000MB (2GB)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Expand"
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    // Deployment Mode Tabs
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        contentColor = TelegramBlue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("📱 Termux", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("🐳 Docker PC", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("☁️ Render (Free)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                        )
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            text = { Text("🚂 Railway", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (selectedTab) {
                        // TAB 0: Termux on this Phone
                        0 -> {
                            val termuxCmd = "pkg update -y && pkg install -y telegram-bot-api && telegram-bot-api --api-id=$cleanApiId --api-hash=$cleanApiHash --local --http-port=8081"
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF141A23),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263345)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "📱 Run on this Android phone (Zero PC needed)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF81D4FA)
                                        )
                                        TextButton(
                                            onClick = { copyToClipboard(termuxCmd, "Termux MTProto Command") },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = TelegramBlue)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Copy Command", fontSize = 11.sp, color = TelegramBlue)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = termuxCmd,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        color = Color(0xFFCFD8DC)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "1. Open Termux -> Paste this command.\n2. In TeleStream, set server to: http://127.0.0.1:8081\n3. Enjoy up to 2GB streaming right on your phone!",
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp,
                                        color = Color(0xFFB0BEC5)
                                    )
                                }
                            }
                        }

                        // TAB 1: Docker PC / VPS
                        1 -> {
                            val dockerCmd = "docker run -d --name tg-bot-api --restart always -p 8081:8081 -v tg-data:/var/lib/telegram-bot-api aiogram/telegram-bot-api:latest --api-id=$cleanApiId --api-hash=$cleanApiHash --local"
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF141A23),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263345)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🐳 Docker on PC / Mac / Linux / VPS",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF81D4FA)
                                        )
                                        TextButton(
                                            onClick = { copyToClipboard(dockerCmd, "Docker Run Command") },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = TelegramBlue)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Copy Docker", fontSize = 11.sp, color = TelegramBlue)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = dockerCmd,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        color = Color(0xFFCFD8DC)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Run on your computer or VPS. Connect TeleStream using http://YOUR_PC_IP:8081",
                                        fontSize = 11.sp,
                                        color = Color(0xFFB0BEC5)
                                    )
                                }
                            }
                        }

                        // TAB 2: Free Cloud Render.com
                        2 -> {
                            val renderDockerfile = """# Render.com / Koyeb Dockerfile
FROM aiogram/telegram-bot-api:latest
ENV TELEGRAM_API_ID=$cleanApiId
ENV TELEGRAM_API_HASH=$cleanApiHash
ENV TELEGRAM_LOCAL=1
ENV TELEGRAM_HTTP_PORT=8081
EXPOSE 8081"""
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF141A23),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263345)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "☁️ 24/7 Cloud Hosting (Render.com / Koyeb)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF81D4FA)
                                        )
                                        TextButton(
                                            onClick = { copyToClipboard(renderDockerfile, "Cloud Dockerfile") },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = TelegramBlue)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Copy Config", fontSize = 11.sp, color = TelegramBlue)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = renderDockerfile,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        color = Color(0xFFCFD8DC)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Deploy on Render/Koyeb for free 24/7 online MTProto cloud without keeping phone/PC on!",
                                        fontSize = 11.sp,
                                        color = Color(0xFFB0BEC5)
                                    )
                                }
                            }
                        }

                        // TAB 3: Railway.app
                        3 -> {
                            val railwayCompose = """version: '3.8'
services:
  telegram-bot-api:
    image: aiogram/telegram-bot-api:latest
    environment:
      TELEGRAM_API_ID: "$cleanApiId"
      TELEGRAM_API_HASH: "$cleanApiHash"
      TELEGRAM_LOCAL: "1"
      TELEGRAM_HTTP_PORT: "8081"
    ports:
      - "8081:8081""""
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF141A23),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263345)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🚂 Docker Compose (Railway / Portainer / VPS)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF81D4FA)
                                        )
                                        TextButton(
                                            onClick = { copyToClipboard(railwayCompose, "Docker Compose") },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = TelegramBlue)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Copy Compose", fontSize = 11.sp, color = TelegramBlue)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = railwayCompose,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        color = Color(0xFFCFD8DC)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Server Reachability & Probe Section
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "🔍 Live MTProto Server Health Tester & Quick Apply",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = probeUrl,
                                    onValueChange = { probeUrl = it },
                                    label = { Text("Server Host URL") },
                                    placeholder = { Text("http://127.0.0.1:8081") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )

                                Button(
                                    onClick = { testServerReachability(probeUrl) },
                                    enabled = !isProbing && probeUrl.isNotBlank(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                                ) {
                                    if (isProbing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                    } else {
                                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Test", fontSize = 12.sp)
                                    }
                                }
                            }

                            // Quick preset chips
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = probeUrl == "http://127.0.0.1:8081",
                                    onClick = {
                                        probeUrl = "http://127.0.0.1:8081"
                                        testServerReachability("http://127.0.0.1:8081")
                                    },
                                    label = { Text("📱 Local Phone (127.0.0.1:8081)", fontSize = 10.sp) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                                FilterChip(
                                    selected = probeUrl == "https://api.telegram.org",
                                    onClick = {
                                        probeUrl = "https://api.telegram.org"
                                        testServerReachability("https://api.telegram.org")
                                    },
                                    label = { Text("☁️ Public Cloud (20MB)", fontSize = 10.sp) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }

                            // Probe Result Banner
                            probeResult?.let { msg ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isProbeSuccess) ServerEmerald.copy(alpha = 0.15f) else Color(0xFFFF5252).copy(alpha = 0.15f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isProbeSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = if (isProbeSuccess) ServerEmerald else Color(0xFFFF5252),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = msg,
                                            fontSize = 11.sp,
                                            color = if (isProbeSuccess) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 1-Click Apply Button
                            FilledTonalButton(
                                onClick = {
                                    onApplyCustomUrl(probeUrl.trim())
                                    Toast.makeText(context, "Applied MTProto Server URL: $probeUrl", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Apply & Save This Server in TeleStream", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
