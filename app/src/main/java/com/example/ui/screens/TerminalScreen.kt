package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.ui.theme.ServerEmerald
import com.example.ui.theme.TelegramBlue
import com.example.ui.viewmodel.TeleStreamViewModel
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TerminalLine(
    val type: LineType,
    val text: String,
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
) {
    enum class LineType {
        COMMAND,
        OUTPUT,
        ERROR,
        INFO,
        SUCCESS
    }
}

@Composable
fun TerminalScreen(
    viewModel: TeleStreamViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val botConfig by viewModel.botConfig.collectAsStateWithLifecycle()

    var inputCommand by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var currentJob by remember { mutableStateOf<Job?>(null) }
    var currentProcess by remember { mutableStateOf<Process?>(null) }

    val terminalLines = remember {
        mutableStateListOf(
            TerminalLine(TerminalLine.LineType.INFO, "TeleStream Terminal Shell v1.2.0"),
            TerminalLine(TerminalLine.LineType.INFO, "Android Linux Environment (sh) ready."),
            TerminalLine(TerminalLine.LineType.INFO, "Type commands below or tap quick preset chips."),
            TerminalLine(TerminalLine.LineType.OUTPUT, "Working dir: " + context.filesDir.absolutePath)
        )
    }

    val commandHistory = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    // Auto-scroll when new lines are appended
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
            terminalLines.add(TerminalLine(TerminalLine.LineType.INFO, "Terminal screen cleared."))
            inputCommand = ""
            return
        }

        if (!commandHistory.contains(cmd)) {
            commandHistory.add(0, cmd)
            if (commandHistory.size > 25) commandHistory.removeAt(commandHistory.lastIndex)
        }

        terminalLines.add(TerminalLine(TerminalLine.LineType.COMMAND, "$ $cmd"))
        inputCommand = ""
        isRunning = true

        currentJob?.cancel()
        currentJob = scope.launch(Dispatchers.IO) {
            try {
                val appDir = context.filesDir
                val processBuilder = ProcessBuilder("sh", "-c", cmd)
                processBuilder.directory(appDir)
                processBuilder.redirectErrorStream(false)

                // Environment injection
                val env = processBuilder.environment()
                env["HOME"] = appDir.absolutePath
                env["TMPDIR"] = context.cacheDir.absolutePath
                env["TELESTREAM_PORT"] = botConfig.serverPort.toString()
                env["BOT_PORT"] = "8081"
                if (botConfig.botToken.isNotBlank()) env["BOT_TOKEN"] = botConfig.botToken
                if (botConfig.customBotApiUrl.isNotBlank()) env["CUSTOM_BOT_API_URL"] = botConfig.customBotApiUrl
                if (botConfig.telegramApiId.isNotBlank()) env["TG_API_ID"] = botConfig.telegramApiId
                if (botConfig.telegramApiHash.isNotBlank()) env["TG_API_HASH"] = botConfig.telegramApiHash

                val process = processBuilder.start()
                currentProcess = process

                val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
                val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

                val outJob = launch {
                    var line: String? = null
                    while (isActive) {
                        val current = stdoutReader.readLine() ?: break
                        withContext(Dispatchers.Main) {
                            terminalLines.add(TerminalLine(TerminalLine.LineType.OUTPUT, current))
                        }
                    }
                }

                val errJob = launch {
                    var errLine: String? = null
                    while (isActive) {
                        val currentErr = stderrReader.readLine() ?: break
                        withContext(Dispatchers.Main) {
                            terminalLines.add(TerminalLine(TerminalLine.LineType.ERROR, currentErr))
                        }
                    }
                }

                outJob.join()
                errJob.join()

                val exitCode = process.waitFor()
                withContext(Dispatchers.Main) {
                    if (exitCode == 0) {
                        terminalLines.add(TerminalLine(TerminalLine.LineType.SUCCESS, "[Process finished with exit code 0]"))
                    } else {
                        terminalLines.add(TerminalLine(TerminalLine.LineType.ERROR, "[Process exited with code $exitCode]"))
                    }
                    isRunning = false
                    currentProcess = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    terminalLines.add(TerminalLine(TerminalLine.LineType.ERROR, "Command failed: ${e.localizedMessage ?: e.message}"))
                    isRunning = false
                    currentProcess = null
                }
            }
        }
    }

    fun stopRunningProcess() {
        try {
            currentProcess?.destroy()
            currentJob?.cancel()
            terminalLines.add(TerminalLine(TerminalLine.LineType.ERROR, "^C [Process terminated by user]"))
        } catch (_: Exception) {}
        isRunning = false
        currentProcess = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Terminal Header Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) ServerEmerald else TelegramBlue)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Android Shell Terminal",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (isRunning) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "● RUNNING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ServerEmerald
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Copy Output
                    IconButton(
                        onClick = {
                            val textToCopy = terminalLines.joinToString("\n") { it.text }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Terminal Output", textToCopy))
                            Toast.makeText(context, "Terminal output copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Output",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Clear Terminal
                    IconButton(
                        onClick = {
                            terminalLines.clear()
                            terminalLines.add(TerminalLine(TerminalLine.LineType.INFO, "Terminal cleared."))
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = "Clear Terminal",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Preset Commands Row
        val quickCommands = listOf(
            "🩺 Diagnose MTProto (8081)" to "echo '=== Testing Port 8081 MTProto Server ===' && curl -s -i -m 3 http://127.0.0.1:8081/ || echo 'Port 8081 unreachable'",
            "🤖 Test Bot getMe" to "if [ -n \"\$BOT_TOKEN\" ]; then echo '=== Querying getMe via Port 8081 ===' && curl -s -m 5 \"http://127.0.0.1:8081/bot\$BOT_TOKEN/getMe\"; else echo 'No Bot Token configured in Bot Setup'; fi",
            "🔌 Test Streaming (8080)" to "curl -I -m 2 http://127.0.0.1:8080/ || echo 'Port 8080 check complete'",
            "🌐 IP & Network" to "ip addr show || ifconfig",
            "⚡ Ping Telegram" to "ping -c 3 api.telegram.org || ping -c 3 8.8.8.8",
            "💾 Storage & RAM" to "df -h /data && free -m",
            "📱 System Info" to "uname -a && uptime",
            "📁 App Files" to "ls -la",
            "⚙️ Env Vars" to "env"
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
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Terminal Screen (Dark Monospace Canvas)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0F141C),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263242)),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(terminalLines) { line ->
                    val color = when (line.type) {
                        TerminalLine.LineType.COMMAND -> Color(0xFF64B5F6) // Light Telegram Blue
                        TerminalLine.LineType.OUTPUT -> Color(0xFFCFD8DC)  // Soft Gray/White
                        TerminalLine.LineType.ERROR -> Color(0xFFFF5252)   // Coral Red
                        TerminalLine.LineType.INFO -> Color(0xFFFFB74D)    // Warm Amber
                        TerminalLine.LineType.SUCCESS -> Color(0xFF69F0AE) // Emerald Green
                    }

                    Text(
                        text = line.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
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
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "executing...",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = ServerEmerald
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Terminal Input Field & Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputCommand,
                onValueChange = { inputCommand = it },
                placeholder = {
                    Text(
                        text = "Enter shell command (e.g. ls, ping, df, curl)...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                },
                leadingIcon = {
                    Text(
                        text = "$",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TelegramBlue,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (inputCommand.isNotBlank() && !isRunning) {
                        executeCommand(inputCommand)
                    }
                }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TelegramBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("terminal_input_field")
            )

            if (isRunning) {
                IconButton(
                    onClick = { stopRunningProcess() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE53935))
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
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (inputCommand.isNotBlank()) TelegramBlue else MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("terminal_send_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Execute Command",
                        tint = if (inputCommand.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
