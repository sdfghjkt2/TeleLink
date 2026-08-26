package com.example.ui.screens

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AutoUpdateCard
import com.example.ui.components.MtprotoHostHubCard
import com.example.ui.theme.ServerEmerald
import com.example.ui.theme.TelegramBlue
import com.example.ui.theme.TelegramLightBlue
import com.example.ui.viewmodel.TeleStreamViewModel

@Composable
fun BotConfigScreen(
    viewModel: TeleStreamViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentConfig by viewModel.botConfig.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTestingBot.collectAsStateWithLifecycle()
    val testMessage by viewModel.botTestMessage.collectAsStateWithLifecycle()
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()

    var token by remember(currentConfig.botToken) { mutableStateOf(currentConfig.botToken) }
    var showToken by remember { mutableStateOf(false) }
    var apiId by remember(currentConfig.telegramApiId) { mutableStateOf(currentConfig.telegramApiId) }
    var apiHash by remember(currentConfig.telegramApiHash) { mutableStateOf(currentConfig.telegramApiHash) }
    var showApiHash by remember { mutableStateOf(false) }
    var portStr by remember(currentConfig.serverPort) { mutableStateOf(currentConfig.serverPort.toString()) }
    var customDomain by remember(currentConfig.customDomain) { mutableStateOf(currentConfig.customDomain) }
    var customBotApiUrl by remember(currentConfig.customBotApiUrl) { mutableStateOf(currentConfig.customBotApiUrl) }
    var welcomeMsg by remember(currentConfig.welcomeMessage) { mutableStateOf(currentConfig.welcomeMessage) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        item {
            Text(
                text = "🤖 Telegram Bot & Server Config",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Connect your Telegram Bot to automatically convert files sent by users into direct browser download & stream links.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Bot Token Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Telegram Bot Token",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )

                        // Paste from clipboard button
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val pasted = clip.getItemAt(0).text?.toString() ?: ""
                                    if (pasted.isNotBlank()) token = pasted.trim()
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste", tint = TelegramBlue)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        placeholder = { Text("123456789:ABCdefGhIJKlmNoPQRstuvwxYZ...") },
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showToken) "Hide" else "Show"
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("input_bot_token")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Test Bot Token & Save Token Button Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.testBotToken(token) },
                            enabled = !isTesting && token.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("btn_test_bot_token")
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Verifying...", fontSize = 11.sp)
                            } else {
                                Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Test Token", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Button(
                            onClick = { viewModel.saveBotToken(token) },
                            enabled = token.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Token", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        if (currentConfig.botUsername.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/${currentConfig.botUsername}"))
                                    context.startActivity(intent)
                                }
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Open Bot", tint = TelegramBlue)
                            }
                        }
                    }

                    if (testMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = testMessage!!,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Telegram API ID & Hash Card (for 2GB files)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⚡ 2GB Direct Telegram API (my.telegram.org)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://my.telegram.org"))
                                context.startActivity(intent)
                            }
                        ) {
                            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = "Open my.telegram.org", tint = TelegramBlue)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Allows bypassing Telegram's 20MB cloud limit for files up to 2GB (2,000MB) without external servers.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // API ID Field
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "App api_id",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val pasted = clip.getItemAt(0).text?.toString() ?: ""
                                    if (pasted.isNotBlank()) apiId = pasted.trim()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste ID", fontSize = 12.sp)
                        }
                    }

                    OutlinedTextField(
                        value = apiId,
                        onValueChange = { apiId = it },
                        placeholder = { Text("e.g. 12345678 (numbers only)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // API Hash Field
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "App api_hash",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val pasted = clip.getItemAt(0).text?.toString() ?: ""
                                    if (pasted.isNotBlank()) apiHash = pasted.trim()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste Hash", fontSize = 12.sp)
                        }
                    }

                    OutlinedTextField(
                        value = apiHash,
                        onValueChange = { apiHash = it },
                        placeholder = { Text("e.g. 0123456789abcdef0123456789abcdef") },
                        visualTransformation = if (showApiHash) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiHash = !showApiHash }) {
                                Icon(
                                    imageVector = if (showApiHash) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showApiHash) "Hide" else "Show"
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.saveApiCredentials(apiId, apiHash) },
                        enabled = apiId.isNotBlank() && apiHash.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save API ID & Hash", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (apiId.isNotBlank() && apiHash.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "API ID & Hash Saved",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Telegram's public cloud (api.telegram.org) strictly limits bot downloads to 20MB. To download up to 2,000MB (2GB), run Telegram's local bot-api server on this phone (via Termux) or on a PC:",
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Termux Command
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📱 Run on this phone (Termux):", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                    TextButton(
                                        onClick = {
                                            val cmd = "pkg update -y && pkg install -y telegram-bot-api && telegram-bot-api --api-id=${apiId.trim()} --api-hash=${apiHash.trim()} --local --http-port=8081"
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Termux Command", cmd))
                                            Toast.makeText(context, "Copied Termux command to clipboard!", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Text("Copy Command", fontSize = 11.sp)
                                    }
                                }

                                Text(
                                    text = "pkg install telegram-bot-api && telegram-bot-api --api-id=${apiId.trim()} --api-hash=... --local --http-port=8081",
                                    fontSize = 10.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = TelegramBlue
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Then set 'Custom Telegram Bot API Server URL' below to http://127.0.0.1:8081",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // MTProto 2GB Server Host Hub
        item {
            MtprotoHostHubCard(
                apiId = apiId,
                apiHash = apiHash,
                botToken = token,
                currentCustomUrl = customBotApiUrl,
                onApplyCustomUrl = { appliedUrl ->
                    customBotApiUrl = appliedUrl
                    viewModel.saveCustomBotApiUrl(appliedUrl)
                }
            )
        }

        // Server Port & Tunnel Config
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "🌐 Network & Domain Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = portStr,
                        onValueChange = { portStr = it },
                        label = { Text("Local HTTP Server Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("input_server_port")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customDomain,
                        onValueChange = { customDomain = it },
                        label = { Text("Custom Domain / Public Tunnel URL (Optional)") },
                        placeholder = { Text("https://my-app.loca.lt or https://xyz.ngrok.app") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val port = portStr.toIntOrNull() ?: 8080
                            viewModel.saveNetworkSettings(port, customDomain)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Network Port & Tunnel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = customBotApiUrl,
                        onValueChange = { customBotApiUrl = it },
                        label = { Text("Custom Telegram Bot API Server URL / MTProto Endpoint") },
                        placeholder = { Text("http://127.0.0.1:8081 or leave empty") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Quick Chips & Save Endpoint Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = customBotApiUrl == "http://127.0.0.1:8081",
                            onClick = { customBotApiUrl = "http://127.0.0.1:8081" },
                            label = { Text("📱 127.0.0.1:8081", fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                        FilterChip(
                            selected = customBotApiUrl.isBlank() || customBotApiUrl == "https://api.telegram.org",
                            onClick = { customBotApiUrl = "https://api.telegram.org" },
                            label = { Text("☁️ Cloud API", fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            viewModel.saveCustomBotApiUrl(customBotApiUrl)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save & Apply MTProto Endpoint", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "💡 Understanding Download Links & Reachability:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• 🚀 Global CDN Links: Sent automatically in Telegram. 100% reachable on any device worldwide with zero setup!\n• 📶 Mobile Data (5G/4G): Carrier NAT blocks external devices from reaching local IPs (10.x.x.x). Use a free tunnel (Ngrok, Cloudflare Tunnel) here if you want your local server accessible externally.\n• 📦 2GB File Limit: Official Telegram Bot API limits bot downloads to 20MB. For files up to 2GB, point 'Custom Bot API Server URL' to a local bot-api server or use local files.",
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = welcomeMsg,
                        onValueChange = { welcomeMsg = it },
                        label = { Text("Bot Welcome Message (/start)") },
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.saveWelcomeMessage(welcomeMsg) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Welcome Message", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // GitHub Repository Auto-Update Settings
        item {
            AutoUpdateCard(
                currentVersion = viewModel.appVersionName,
                updateStatus = updateStatus,
                config = currentConfig,
                onCheckForUpdates = { repoUrl ->
                    viewModel.checkForUpdates(repoUrl, showToast = true)
                },
                onDownloadAndInstall = { release ->
                    viewModel.downloadAndInstallUpdate(release)
                },
                onInstallDownloadedApk = { apkFile ->
                    viewModel.installDownloadedUpdate(apkFile)
                },
                onSaveConfig = { newConfig ->
                    viewModel.saveBotConfig(newConfig)
                }
            )
        }

        // Step-by-Step BotFather Guide
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = null,
                                tint = TelegramLightBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "How to create your Telegram Bot",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/BotFather"))
                                context.startActivity(intent)
                            }
                        ) {
                            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = "Open BotFather", tint = TelegramBlue)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    GuideStep(number = "1", text = "Open @BotFather on Telegram and tap Start.")
                    GuideStep(number = "2", text = "Send the command /newbot and choose a name & username.")
                    GuideStep(number = "3", text = "Copy the HTTP API Token provided by BotFather.")
                    GuideStep(number = "4", text = "Paste the token above and tap 'Save & Apply'.")
                    GuideStep(number = "5", text = "Start TeleStream Server! Send any video or file to your bot.")
                }
            }
        }

        // Save Button
        item {
            Button(
                onClick = {
                    val port = portStr.toIntOrNull() ?: 8080
                    viewModel.saveBotConfig(
                        currentConfig.copy(
                            botToken = token.trim(),
                            telegramApiId = apiId.trim(),
                            telegramApiHash = apiHash.trim(),
                            serverPort = port,
                            customDomain = customDomain.trim(),
                            customBotApiUrl = customBotApiUrl.trim(),
                            welcomeMessage = welcomeMsg.trim()
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_save_bot_config"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SAVE & APPLY CONFIGURATION", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun GuideStep(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(TelegramBlue.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = number, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TelegramBlue)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
        )
    }
}
