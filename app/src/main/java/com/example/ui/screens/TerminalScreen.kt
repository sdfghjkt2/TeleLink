package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.terminal.LinuxCommandEngine
import com.example.terminal.TermLine
import com.example.terminal.TermLineType
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ServerEmerald
import com.example.ui.theme.TelegramBlue
import com.example.ui.viewmodel.TeleStreamViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun TerminalScreen(
    viewModel: TeleStreamViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val botConfig by viewModel.botConfig.collectAsStateWithLifecycle()
    val serverStats by viewModel.serverStats.collectAsStateWithLifecycle()
    val mtprotoStats by viewModel.mtprotoStats.collectAsStateWithLifecycle()

    val linuxEngine = remember { LinuxCommandEngine(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var inputCommand by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var currentJob by remember { mutableStateOf<Job?>(null) }
    var historyIndex by remember { mutableIntStateOf(-1) }
    var fontSizeSp by remember { mutableIntStateOf(12) }

    val terminalLines = remember { mutableStateListOf<TermLine>() }
    val commandHistory = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    // Initialize Ubuntu MOTD on launch
    LaunchedEffect(Unit) {
        if (terminalLines.isEmpty()) {
            terminalLines.addAll(linuxEngine.getMotd(botConfig))
        }
    }

    // Auto-scroll when lines arrive
    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(terminalLines.size - 1)
        }
    }

    fun executeCommand(rawCmd: String) {
        val cmd = rawCmd.trim()
        if (cmd.isEmpty()) return

        if (cmd.equals("clear", ignoreCase = true) || cmd.equals("cls", ignoreCase = true)) {
            terminalLines.clear()
            inputCommand = ""
            return
        }

        if (!commandHistory.contains(cmd)) {
            commandHistory.add(0, cmd)
            if (commandHistory.size > 30) commandHistory.removeAt(commandHistory.lastIndex)
        }
        historyIndex = -1

        terminalLines.add(TermLine(TermLineType.PROMPT, "root@telestream-ubuntu:~# $cmd"))
        inputCommand = ""
        isRunning = true

        currentJob?.cancel()
        currentJob = scope.launch(Dispatchers.Main) {
            try {
                linuxEngine.execute(
                    rawCommand = cmd,
                    botConfig = botConfig,
                    onOutput = { line ->
                        terminalLines.add(line)
                    }
                )
            } finally {
                isRunning = false
            }
        }
    }

    fun handleExtraKey(key: String) {
        when (key) {
            "TAB" -> {
                val suggestions = linuxEngine.getAutocompleteSuggestions(inputCommand)
                if (suggestions.isNotEmpty()) {
                    inputCommand = suggestions.first()
                }
            }
            "↑" -> {
                if (commandHistory.isNotEmpty()) {
                    val nextIdx = (historyIndex + 1).coerceAtMost(commandHistory.lastIndex)
                    historyIndex = nextIdx
                    inputCommand = commandHistory[nextIdx]
                }
            }
            "↓" -> {
                if (historyIndex > 0) {
                    historyIndex--
                    inputCommand = commandHistory[historyIndex]
                } else if (historyIndex == 0) {
                    historyIndex = -1
                    inputCommand = ""
                }
            }
            "ESC" -> {
                inputCommand = ""
            }
            "CTRL+C" -> {
                currentJob?.cancel()
                isRunning = false
                terminalLines.add(TermLine(TermLineType.ERROR, "^C [Process Interrupted]"))
            }
            "CLEAR" -> {
                terminalLines.clear()
            }
            else -> {
                inputCommand += key
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C1017))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Ubuntu & Termux Navigation Session Bar
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Terminal Traffic Indicator
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) ServerEmerald else CyberCyan)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ubuntu 24.04 Termux Shell",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFFE6EDF3)
                    )
                    if (isRunning) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "● RUNNING",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = ServerEmerald
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Font Size Adjuster
                    TextButton(
                        onClick = { fontSizeSp = (fontSizeSp - 1).coerceAtLeast(9) },
                        modifier = Modifier.size(28.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("A-", fontSize = 11.sp, color = Color(0xFF8B949E), fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = { fontSizeSp = (fontSizeSp + 1).coerceAtMost(16) },
                        modifier = Modifier.size(28.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("A+", fontSize = 11.sp, color = Color(0xFF8B949E), fontWeight = FontWeight.Bold)
                    }

                    // Copy Buffer
                    IconButton(
                        onClick = {
                            val textToCopy = terminalLines.joinToString("\n") { it.text }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Terminal Logs", textToCopy))
                            Toast.makeText(context, "Terminal output copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Clear Buffer
                    IconButton(
                        onClick = { terminalLines.clear() },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = "Clear",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Quick Preset Terminal Actions Row
        val quickCommands = listOf(
            "🩺 Diagnose 8081" to "curl -i http://127.0.0.1:8081/",
            "🤖 Test Bot getMe" to "curl -s \"http://127.0.0.1:8081/bot\$BOT_TOKEN/getMe\"",
            "📦 pkg list" to "pkg list",
            "⚡ pkg install telegram-bot-api" to "pkg install telegram-bot-api",
            "🚀 Start 2GB Daemon" to "telegram-bot-api --local --http-port=8082",
            "📊 neofetch" to "neofetch",
            "📈 htop" to "htop",
            "🌐 IP & Network" to "ip addr || ifconfig",
            "⚡ Speedtest" to "speedtest",
            "💾 Storage & RAM" to "df -h /data && free -m"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickCommands.forEach { (label, command) ->
                FilterChip(
                    selected = false,
                    onClick = { executeCommand(command) },
                    label = { Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF161B22),
                        labelColor = Color(0xFF58A6FF)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF30363D))
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Ubuntu Terminal Monospace Viewport
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF090D13),
            border = BorderStroke(1.dp, Color(0xFF30363D)),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(terminalLines) { line ->
                    val color = when (line.type) {
                        TermLineType.PROMPT -> Color(0xFF7EE787) // Ubuntu Bright Terminal Green
                        TermLineType.COMMAND -> Color(0xFF58A6FF) // Cyan / Blue
                        TermLineType.OUTPUT -> Color(0xFFC9D1D9)  // Terminal Text Gray
                        TermLineType.ERROR -> Color(0xFFFF7B72)   // Ubuntu Red
                        TermLineType.WARNING -> Color(0xFFFFD166) // Warning Yellow
                        TermLineType.INFO -> Color(0xFFFFA657)    // Amber
                        TermLineType.SUCCESS -> Color(0xFF3FB950) // Emerald
                        TermLineType.SYSTEM -> Color(0xFF79C0FF)  // Light Blue
                        TermLineType.ASCII_ART -> Color(0xFFD2A8FF) // Ubuntu Purple
                    }

                    Text(
                        text = line.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp + 4).sp,
                        color = color
                    )
                }

                if (isRunning) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = ServerEmerald
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "executing in background...",
                                fontFamily = FontFamily.Monospace,
                                fontSize = (fontSizeSp - 1).sp,
                                color = ServerEmerald
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Termux Style Extra-Keys Bar (ESC, TAB, CTRL, ALT, UP, DOWN, /, -, |, ~)
        val extraKeys = listOf("ESC", "TAB", "CTRL+C", "↑", "↓", "/", "-", "~", "|", "&", "CLEAR")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            extraKeys.forEach { key ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF21262D),
                    border = BorderStroke(1.dp, Color(0xFF30363D)),
                    modifier = Modifier
                        .height(32.dp)
                        .clickable { handleExtraKey(key) }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = key,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFFE6EDF3)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Terminal Prompt Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = inputCommand,
                onValueChange = { inputCommand = it },
                placeholder = {
                    Text(
                        text = "pkg, curl, wget, neofetch, htop, bash...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF484F58)
                    )
                },
                leadingIcon = {
                    Text(
                        text = "root#",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7EE787),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (inputCommand.isNotBlank() && !isRunning) {
                        executeCommand(inputCommand)
                    }
                }),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFFE6EDF3),
                    unfocusedTextColor = Color(0xFFE6EDF3),
                    focusedContainerColor = Color(0xFF161B22),
                    unfocusedContainerColor = Color(0xFF161B22),
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = Color(0xFF30363D)
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("terminal_input_field")
            )

            if (isRunning) {
                IconButton(
                    onClick = {
                        currentJob?.cancel()
                        isRunning = false
                        terminalLines.add(TermLine(TermLineType.ERROR, "^C [Process Stopped]"))
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFDA3633))
                        .testTag("terminal_stop_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = Color.White
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        if (inputCommand.isNotBlank()) {
                            executeCommand(inputCommand)
                        }
                    },
                    enabled = inputCommand.isNotBlank(),
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (inputCommand.isNotBlank()) CyberCyan else Color(0xFF21262D))
                        .testTag("terminal_send_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Execute",
                        tint = if (inputCommand.isNotBlank()) Color(0xFF0D1117) else Color(0xFF484F58)
                    )
                }
            }
        }
    }
}
