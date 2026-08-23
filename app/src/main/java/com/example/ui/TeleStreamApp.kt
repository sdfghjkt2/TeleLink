package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UpdateStatus
import com.example.ui.components.CreateStreamDialog
import com.example.ui.components.PlayerPreviewDialog
import com.example.ui.components.QrCodeDialog
import com.example.ui.components.UpdatePromptDialog
import com.example.ui.screens.BotConfigScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.WebPortalAndLogsScreen
import com.example.ui.theme.ServerEmerald
import com.example.ui.theme.TelegramBlue
import com.example.ui.theme.TelegramLightBlue
import com.example.ui.viewmodel.TeleStreamViewModel

enum class NavigationTab(val title: String) {
    DASHBOARD("Dashboard"),
    FILES("Streams"),
    BOT_SETUP("Bot Setup"),
    LOGS("Logs & Web")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeleStreamApp(viewModel: TeleStreamViewModel) {
    var currentTab by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    val serverStats by viewModel.serverStats.collectAsStateWithLifecycle()
    val previewFile by viewModel.previewFile.collectAsStateWithLifecycle()
    val qrFile by viewModel.qrFile.collectAsStateWithLifecycle()
    val filesList by viewModel.filteredFiles.collectAsStateWithLifecycle()
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(TelegramBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TeleStream",
                            fontWeight = FontWeight.Black,
                            fontSize = 19.sp,
                            letterSpacing = 0.5.sp
                        )

                        if (serverStats.isRunning) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ServerEmerald)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (updateStatus is UpdateStatus.UpdateAvailable || updateStatus is UpdateStatus.Downloading || updateStatus is UpdateStatus.ReadyToInstall) {
                                showUpdateDialog = true
                            } else {
                                currentTab = 2 // Go to Bot & Updates config tab
                            }
                        },
                        modifier = Modifier.testTag("topbar_updater_btn")
                    ) {
                        BadgedBox(
                            badge = {
                                if (updateStatus is UpdateStatus.UpdateAvailable || updateStatus is UpdateStatus.ReadyToInstall) {
                                    Badge(containerColor = ServerEmerald) {
                                        Text("NEW", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (updateStatus is UpdateStatus.UpdateAvailable) Icons.Default.NewReleases else Icons.Default.SystemUpdate,
                                contentDescription = "Check for Updates",
                                tint = if (updateStatus is UpdateStatus.UpdateAvailable) ServerEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                // Tab 0: Dashboard
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == 0) Icons.Default.Speed else Icons.Outlined.Speed,
                            contentDescription = "Dashboard"
                        )
                    },
                    label = { Text("Dashboard", fontSize = 11.sp, fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.testTag("tab_dashboard")
                )

                // Tab 1: Files / Streams
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (filesList.isNotEmpty()) {
                                    Badge(containerColor = TelegramBlue) {
                                        Text("${filesList.size}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (currentTab == 1) Icons.Default.Folder else Icons.Outlined.Folder,
                                contentDescription = "Streams"
                            )
                        }
                    },
                    label = { Text("Streams", fontSize = 11.sp, fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.testTag("tab_files")
                )

                // Tab 2: Bot Setup
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == 2) Icons.Default.SmartToy else Icons.Outlined.SmartToy,
                            contentDescription = "Bot Setup"
                        )
                    },
                    label = { Text("Bot Setup", fontSize = 11.sp, fontWeight = if (currentTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.testTag("tab_bot_setup")
                )

                // Tab 3: Logs & Web
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == 3) Icons.Default.Language else Icons.Outlined.Language,
                            contentDescription = "Web & Logs"
                        )
                    },
                    label = { Text("Web & Logs", fontSize = 11.sp, fontWeight = if (currentTab == 3) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.testTag("tab_web_logs")
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentTab) {
                0 -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToFiles = { currentTab = 1 },
                    onNavigateToBotSetup = { currentTab = 2 },
                    onOpenCreateDialog = { showCreateDialog = true }
                )
                1 -> FilesScreen(
                    viewModel = viewModel,
                    onOpenCreateDialog = { showCreateDialog = true }
                )
                2 -> BotConfigScreen(
                    viewModel = viewModel
                )
                3 -> WebPortalAndLogsScreen(
                    viewModel = viewModel
                )
            }
        }

        // Dialogs
        if (showCreateDialog) {
            CreateStreamDialog(
                onDismiss = { showCreateDialog = false },
                onCreateStream = { name, tgId, sizeMb, mime ->
                    val sizeBytes = (sizeMb * 1024 * 1024).toLong()
                    viewModel.createManualStream(name, tgId, sizeBytes, mime)
                }
            )
        }

        if (previewFile != null) {
            val file = previewFile!!
            PlayerPreviewDialog(
                file = file,
                downloadUrl = viewModel.getDownloadUrl(file.id),
                streamUrl = viewModel.getStreamUrl(file.id),
                playerUrl = viewModel.getPlayerUrl(file.id),
                onDismiss = { viewModel.setPreviewFile(null) },
                onCopyUrl = { viewModel.copyToClipboard(it) }
            )
        }

        if (qrFile != null) {
            val file = qrFile!!
            QrCodeDialog(
                file = file,
                downloadUrl = viewModel.getDownloadUrl(file.id),
                playerUrl = viewModel.getPlayerUrl(file.id),
                onDismiss = { viewModel.setQrFile(null) },
                onCopyUrl = { viewModel.copyToClipboard(it) }
            )
        }

        if (showUpdateDialog && (updateStatus is UpdateStatus.UpdateAvailable || updateStatus is UpdateStatus.Downloading || updateStatus is UpdateStatus.ReadyToInstall)) {
            UpdatePromptDialog(
                currentVersion = viewModel.appVersionName,
                updateStatus = updateStatus,
                onDismiss = { showUpdateDialog = false },
                onDownloadAndInstall = { release ->
                    viewModel.downloadAndInstallUpdate(release)
                },
                onInstallDownloadedApk = { apkFile ->
                    viewModel.installDownloadedUpdate(apkFile)
                }
            )
        }
    }
}
