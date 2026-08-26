package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bot.BotPollingStatus
import com.example.data.model.StreamFileItem
import com.example.data.model.UpdateStatus
import com.example.ui.components.FileItemCard
import com.example.ui.components.MtprotoServerStatusCard
import com.example.ui.components.ServerStatusHero
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ServerEmerald
import com.example.ui.theme.TelegramBlue
import com.example.ui.theme.TelegramLightBlue
import com.example.ui.viewmodel.TeleStreamViewModel

@Composable
fun DashboardScreen(
    viewModel: TeleStreamViewModel,
    onNavigateToFiles: () -> Unit,
    onNavigateToBotSetup: () -> Unit,
    onOpenCreateDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val serverStats by viewModel.serverStats.collectAsStateWithLifecycle()
    val mtprotoStats by viewModel.mtprotoStats.collectAsStateWithLifecycle()
    val mtprotoConfig by viewModel.mtprotoConfig.collectAsStateWithLifecycle()
    val botStatus by viewModel.botStatus.collectAsStateWithLifecycle()
    val botConfig by viewModel.botConfig.collectAsStateWithLifecycle()
    val recentFiles by viewModel.filteredFiles.collectAsStateWithLifecycle()
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 0. Update Alert Banner (If available)
        if (updateStatus is UpdateStatus.UpdateAvailable || updateStatus is UpdateStatus.ReadyToInstall) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ServerEmerald.copy(alpha = 0.12f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(ServerEmerald, CyberCyan))
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("dashboard_update_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(ServerEmerald.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NewReleases,
                                    contentDescription = null,
                                    tint = ServerEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (updateStatus is UpdateStatus.UpdateAvailable) "New Update ${(updateStatus as UpdateStatus.UpdateAvailable).release.tagName} Available!" else "Update Downloaded & Ready!",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = ServerEmerald
                                )
                                Text(
                                    text = "Sync & auto-update from GitHub repository",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (updateStatus is UpdateStatus.UpdateAvailable) {
                                    viewModel.downloadAndInstallUpdate((updateStatus as UpdateStatus.UpdateAvailable).release)
                                } else if (updateStatus is UpdateStatus.ReadyToInstall) {
                                    viewModel.installDownloadedUpdate((updateStatus as UpdateStatus.ReadyToInstall).apkFile)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ServerEmerald)
                        ) {
                            Text(
                                text = if (updateStatus is UpdateStatus.ReadyToInstall) "Install" else "Update",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // 1. Top Server Hero Card
        item {
            ServerStatusHero(
                stats = serverStats,
                onToggleServer = { viewModel.toggleServer() },
                onCopyUrl = { viewModel.copyToClipboard(it, "Server URL") }
            )
        }

        // 2. MTProto Server Status & Active Port Monitor
        item {
            MtprotoServerStatusCard(
                isRunning = mtprotoStats.isRunning,
                port = mtprotoConfig.port,
                totalRequests = mtprotoStats.totalRequests,
                onToggle = { viewModel.toggleMtprotoServer() }
            )
        }

        // 3. Telegram Bot Live Status Banner
        item {
            TelegramBotHeroCard(
                botStatus = botStatus,
                botConfig = botConfig,
                onSetupBot = onNavigateToBotSetup,
                onOpenTelegram = {
                    if (botConfig.botUsername.isNotBlank()) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/${botConfig.botUsername}"))
                        context.startActivity(intent)
                    } else {
                        onNavigateToBotSetup()
                    }
                }
            )
        }

        // 3. Quick Action Hub
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenCreateDialog,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Stream", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        val url = viewModel.getWebPortalUrl()
                        if (serverStats.isRunning) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } else {
                            viewModel.copyToClipboard(url, "Web Portal Link")
                        }
                    },
                    modifier = Modifier.weight(1.2f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                ) {
                    Icon(imageVector = Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Web Hub", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // 4. Recent Streams Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚡ Active Stream Links",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (recentFiles.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onNavigateToFiles,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("View All (${recentFiles.size})", fontSize = 12.sp)
                    }
                }
            }
        }

        // 5. Recent Files List (Top 3 on Dashboard)
        if (recentFiles.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = TelegramLightBlue,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No active stream files",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Send any file to the Telegram Bot or tap 'Add Stream' to generate instant web stream & download links.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(recentFiles.take(4), key = { it.id }) { file ->
                FileItemCard(
                    file = file,
                    onPlayOrPreview = { viewModel.setPreviewFile(file) },
                    onCopyDownloadUrl = {
                        viewModel.copyToClipboard(viewModel.getDownloadUrl(file.id), "Direct Download Link")
                    },
                    onCopyStreamUrl = {
                        viewModel.copyToClipboard(viewModel.getStreamUrl(file.id), "Stream Link")
                    },
                    onShowQr = { viewModel.setQrFile(file) },
                    onDelete = { viewModel.deleteFile(file.id) }
                )
            }
        }
    }
}

@Composable
fun TelegramBotHeroCard(
    botStatus: BotPollingStatus,
    botConfig: com.example.data.model.BotConfig,
    onSetupBot: () -> Unit,
    onOpenTelegram: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConfigured = botConfig.botToken.isNotBlank()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(TelegramBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Telegram",
                        tint = TelegramBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Telegram File Bot",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        when (botStatus) {
                            is BotPollingStatus.Active -> {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(ServerEmerald.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("POLLING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ServerEmerald)
                                }
                            }
                            is BotPollingStatus.Connecting -> {
                                Text("Connecting...", fontSize = 11.sp, color = CyberCyan)
                            }
                            is BotPollingStatus.Error -> {
                                Text("Error", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                            else -> {
                                Text(if (isConfigured) "Configured" else "Not Setup", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (botConfig.botUsername.isNotBlank()) "@${botConfig.botUsername}" else "Tap to configure Bot Token",
                        fontSize = 13.sp,
                        color = if (botConfig.botUsername.isNotBlank()) TelegramLightBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (botConfig.botUsername.isNotBlank()) {
                Button(
                    onClick = onOpenTelegram,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                ) {
                    Text("Chat Bot", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = onSetupBot,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Setup Token", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
