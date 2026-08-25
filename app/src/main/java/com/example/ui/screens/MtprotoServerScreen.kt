package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.ApiRequestLogEntity
import com.example.data.db.ServerConfigEntity
import com.example.data.model.MtprotoDcInfo
import com.example.data.model.ServerMode
import com.example.ui.theme.ServerEmerald
import com.example.ui.theme.TelegramBlue
import com.example.ui.viewmodel.TeleStreamViewModel
import com.example.util.NetworkUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MtprotoServerScreen(viewModel: TeleStreamViewModel) {
    val context = LocalContext.current
    val serverStats by viewModel.mtprotoStats.collectAsStateWithLifecycle()
    val serverConfig by viewModel.mtprotoConfig.collectAsStateWithLifecycle()
    val botsList by viewModel.mtprotoBots.collectAsStateWithLifecycle()
    val logsList by viewModel.mtprotoLogs.collectAsStateWithLifecycle()
    val simulatorMessages by viewModel.mtprotoSimulatorMessages.collectAsStateWithLifecycle()
    val simulatorText by viewModel.mtprotoSimulatorText.collectAsStateWithLifecycle()

    var selectedSubTab by remember { mutableIntStateOf(0) }
    var selectedLogDetail by remember { mutableStateOf<ApiRequestLogEntity?>(null) }

    val subTabs = listOf(
        "⚡ Controls & DC" to Icons.Default.Dns,
        "🤖 Chat Sandbox" to Icons.Default.SmartToy,
        "📊 Traffic Logs" to Icons.Default.Speed,
        "💻 API Console" to Icons.Default.Code
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Sub-Tab Navigation Bar
        ScrollableTabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = TelegramBlue,
            edgePadding = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            subTabs.forEachIndexed { index, (title, icon) ->
                Tab(
                    selected = selectedSubTab == index,
                    onClick = { selectedSubTab = index },
                    icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (selectedSubTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        when (selectedSubTab) {
            0 -> MtprotoControlsSubTab(
                viewModel = viewModel,
                serverStats = serverStats,
                serverConfig = serverConfig
            )
            1 -> MtprotoSandboxSubTab(
                viewModel = viewModel,
                serverStats = serverStats,
                messages = simulatorMessages,
                userText = simulatorText,
                onUserTextChange = { viewModel.setMtprotoSimulatorText(it) },
                onSendMessage = { viewModel.sendMtprotoSimulatorMessage() },
                onClearChat = { viewModel.clearMtprotoSimulatorChat() }
            )
            2 -> MtprotoTrafficLogsSubTab(
                logs = logsList,
                onClearLogs = { viewModel.clearMtprotoLogs() },
                onSelectLog = { selectedLogDetail = it }
            )
            3 -> MtprotoConsoleSubTab(viewModel = viewModel)
        }

        // Detailed Log Inspection Dialog
        selectedLogDetail?.let { log ->
            LogDetailModalDialog(log = log, onDismiss = { selectedLogDetail = null })
        }
    }
}

@Composable
private fun MtprotoControlsSubTab(
    viewModel: TeleStreamViewModel,
    serverStats: com.example.data.model.ServerRuntimeStats,
    serverConfig: ServerConfigEntity
) {
    val context = LocalContext.current
    var editPort by remember(serverConfig.port) { mutableStateOf(serverConfig.port.toString()) }
    var editLatency by remember(serverConfig.simulateLatencyMs) { mutableStateOf(serverConfig.simulateLatencyMs.toString()) }
    var localMode by remember(serverConfig.localModeEnabled) { mutableStateOf(serverConfig.localModeEnabled) }
    var maxFileSize by remember(serverConfig.maxFileSizeMb) { mutableStateOf(serverConfig.maxFileSizeMb.toString()) }
    var customCloudUrl by remember(serverConfig.customBotApiUrl) { mutableStateOf(serverConfig.customBotApiUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Server State Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (serverStats.isRunning) ServerEmerald.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
            ),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (serverStats.isRunning) ServerEmerald else Color(0xFFFF5252))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (serverStats.isRunning) "MTProto Server Active" else "MTProto Server Offline",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Mode: ${serverConfig.mode} (DC${serverConfig.selectedDcId})",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.toggleMtprotoServer() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (serverStats.isRunning) Color(0xFFE53935) else ServerEmerald
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (serverStats.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (serverStats.isRunning) "Stop" else "Start", fontWeight = FontWeight.Bold)
                    }
                }

                if (serverStats.isRunning) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF141A23),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Local API Endpoint:", fontSize = 10.sp, color = Color(0xFF90CAF9))
                                Text(
                                    text = "http://${NetworkUtils.getLocalIpAddress()}:${serverStats.port}/bot<TOKEN>/",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                            IconButton(
                                onClick = {
                                    val endpoint = "http://${NetworkUtils.getLocalIpAddress()}:${serverStats.port}"
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("MTProto Server Endpoint", endpoint))
                                    Toast.makeText(context, "Copied endpoint to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TelegramBlue, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Live Real-Time Telemetry Stats Grid
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📊 Live Server Metrics", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricStatBox("Total Requests", serverStats.totalRequests.toString(), Modifier.weight(1f))
                    MetricStatBox("Rate (RPS)", String.format(Locale.US, "%.1f", serverStats.requestsPerSec), Modifier.weight(1f))
                    MetricStatBox("Errors", serverStats.errorCount.toString(), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricStatBox("Avg Latency", "${serverStats.avgLatencyMs} ms", Modifier.weight(1f))
                    MetricStatBox("Uptime", NetworkUtils.formatUptime(serverStats.uptimeSeconds), Modifier.weight(1f))
                    MetricStatBox("Active Conns", serverStats.activeConnections.toString(), Modifier.weight(1f))
                }
            }
        }

        // Server Mode Toggle (Gateway vs. Sandbox)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("⚙️ Operational Server Engine Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = serverConfig.mode == ServerMode.LOCAL_SANDBOX.name,
                        onClick = { viewModel.updateMtprotoServerMode(ServerMode.LOCAL_SANDBOX) },
                        label = { Text("🧪 Local Mock Sandbox", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TelegramBlue.copy(alpha = 0.2f)),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = serverConfig.mode == ServerMode.MTPROTO_GATEWAY.name,
                        onClick = { viewModel.updateMtprotoServerMode(ServerMode.MTPROTO_GATEWAY) },
                        label = { Text("🚀 MTProto Cloud Gateway", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ServerEmerald.copy(alpha = 0.2f)),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (serverConfig.mode == ServerMode.LOCAL_SANDBOX.name) {
                        "Local Sandbox: Simulates Telegram bot server entirely offline on device with instant virtual mock chats and webhook dispatch."
                    } else {
                        "MTProto Gateway: Proxies Bot API requests to live Telegram MTProto cloud servers with local server capabilities (2GB file limit unlock, query inspection)."
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // MTProto Data Center (DC) Selector
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🌐 MTProto Data Center (DC) Routing", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MtprotoDcInfo.DEFAULT_DCS.forEach { dc ->
                        val isSelected = serverConfig.selectedDcId == dc.id && serverConfig.isTestDc == dc.isTest
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectMtprotoDc(dc) },
                            label = { Text("${dc.name} (${dc.location})", fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }

        // Advanced Server Parameters
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🔧 Server Socket Configuration", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editPort,
                        onValueChange = { editPort = it },
                        label = { Text("Server Port") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxFileSize,
                        onValueChange = { maxFileSize = it },
                        label = { Text("Max File (MB)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customCloudUrl,
                    onValueChange = { customCloudUrl = it },
                    label = { Text("Cloud Gateway Upstream URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val portInt = editPort.toIntOrNull() ?: 8081
                        val maxMbInt = maxFileSize.toIntOrNull() ?: 2000
                        viewModel.updateMtprotoConfig(
                            serverConfig.copy(
                                port = portInt,
                                maxFileSizeMb = maxMbInt,
                                customBotApiUrl = customCloudUrl.trim()
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save & Apply Socket Settings", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MetricStatBox(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MtprotoSandboxSubTab(
    viewModel: TeleStreamViewModel,
    serverStats: com.example.data.model.ServerRuntimeStats,
    messages: List<com.example.data.db.SandboxMessageEntity>,
    userText: String,
    onUserTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onClearChat: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Chat Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Bot Sandbox Live Chat", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            IconButton(onClick = onClearChat, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ClearAll, contentDescription = "Clear Chat", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }

        // Quick Command Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("/start", "/ping", "/info", "/help", "Hello Bot! 👋").forEach { cmd ->
                FilterChip(
                    selected = false,
                    onClick = {
                        onUserTextChange(cmd)
                    },
                    label = { Text(cmd, fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Message Stream
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No sandbox messages yet.\nType a message or command below to test your bot!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        val isBot = msg.isFromBot
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isBot) Arrangement.Start else Arrangement.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 12.dp,
                                    topEnd = 12.dp,
                                    bottomStart = if (isBot) 2.dp else 12.dp,
                                    bottomEnd = if (isBot) 12.dp else 2.dp
                                ),
                                color = if (isBot) TelegramBlue.copy(alpha = 0.18f) else TelegramBlue,
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = msg.senderName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBot) TelegramBlue else Color.White
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = msg.text,
                                        fontSize = 12.sp,
                                        color = if (isBot) MaterialTheme.colorScheme.onSurface else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Message Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = userText,
                onValueChange = onUserTextChange,
                placeholder = { Text("Type message (e.g. /start)...", fontSize = 12.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSendMessage() }),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onSendMessage,
                enabled = userText.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (userText.isNotBlank()) TelegramBlue else MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (userText.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MtprotoTrafficLogsSubTab(
    logs: List<ApiRequestLogEntity>,
    onClearLogs: () -> Unit,
    onSelectLog: (ApiRequestLogEntity) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Incoming MTProto / Bot API Traffic (${logs.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row {
                TextButton(onClick = onClearLogs) {
                    Text("Clear", fontSize = 11.sp)
                }
            }
        }

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No API traffic recorded yet.\nSend requests to http://127.0.0.1:8081/bot<TOKEN>/... to inspect real-time packets.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(logs) { log ->
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectLog(log) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (log.statusCode in 200..299) ServerEmerald.copy(alpha = 0.2f) else Color(0xFFFF5252).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "${log.statusCode}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (log.statusCode in 200..299) Color(0xFF2E7D32) else Color(0xFFC62828),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "${log.httpMethod} ${if (log.apiMethod.isNotBlank()) log.apiMethod else log.path}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "${log.clientIp} • ${log.latencyMs}ms • ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text("Inspect →", fontSize = 11.sp, color = TelegramBlue)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MtprotoConsoleSubTab(viewModel: TeleStreamViewModel) {
    val method by viewModel.apiTesterMethod.collectAsStateWithLifecycle()
    val token by viewModel.apiTesterToken.collectAsStateWithLifecycle()
    val body by viewModel.apiTesterBody.collectAsStateWithLifecycle()
    val responseBody by viewModel.apiTesterResponseBody.collectAsStateWithLifecycle()
    val isLoading by viewModel.isApiTesterLoading.collectAsStateWithLifecycle()

    val presetMethods = listOf("getMe", "sendMessage", "getUpdates", "sendPhoto", "sendChatAction", "setWebhook", "getMyCommands")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("💻 Bot API Console & Endpoint Tester", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        // Method presets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presetMethods.forEach { m ->
                FilterChip(
                    selected = method == m,
                    onClick = { viewModel.setApiTesterMethod(m) },
                    label = { Text(m, fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        OutlinedTextField(
            value = token,
            onValueChange = { viewModel.setApiTesterToken(it) },
            label = { Text("Bot Token") },
            placeholder = { Text("123456789:ABCdef...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = body,
            onValueChange = { viewModel.setApiTesterBody(it) },
            label = { Text("JSON Payload Body") },
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        )

        Button(
            onClick = { viewModel.executeApiTest() },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Execute POST Request", fontWeight = FontWeight.Bold)
            }
        }

        // Response Canvas
        responseBody?.let { resp ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0F141C),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = resp,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFF81D4FA),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun LogDetailModalDialog(
    log: ApiRequestLogEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${log.httpMethod} ${log.path}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Status: HTTP ${log.statusCode} • Latency: ${log.latencyMs}ms", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text("Client IP: ${log.clientIp}", fontSize = 11.sp)

                if (log.queryParams.isNotBlank()) {
                    Text("Query Params:\n${log.queryParams}", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }

                if (log.requestBody.isNotBlank()) {
                    Text("Request Body:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF1E2632), modifier = Modifier.fillMaxWidth()) {
                        Text(log.requestBody, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF80DEEA), modifier = Modifier.padding(8.dp))
                    }
                }

                Text("Response Body:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF1E2632), modifier = Modifier.fillMaxWidth()) {
                    Text(log.responseBody, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFFA5D6A7), modifier = Modifier.padding(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val fullLog = "HTTP ${log.statusCode} ${log.httpMethod} ${log.path}\nReq: ${log.requestBody}\nResp: ${log.responseBody}"
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Log Packet", fullLog))
                    Toast.makeText(context, "Log packet copied!", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Copy Log")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
