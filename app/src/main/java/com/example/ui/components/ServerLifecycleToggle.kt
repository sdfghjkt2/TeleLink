package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ServerStats
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ServerEmerald
import com.example.ui.theme.TelegramBlue
import com.example.ui.theme.TelegramLightBlue
import com.example.util.NetworkUtils

/**
 * UI component featuring a 'Start/Stop Server' toggle switch that manages the lifecycle
 * of the local streaming / Node.js HTTP server.
 */
@Composable
fun ServerLifecycleToggle(
    stats: ServerStats,
    onToggleServer: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Local Server Lifecycle",
    subtitle: String = "Node.js & HTTP Streaming Service"
) {
    val isRunning = stats.isRunning

    val infiniteTransition = rememberInfiniteTransition(label = "serverPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isRunning) ServerEmerald.copy(alpha = 0.6f) else Color(0xFF475569).copy(alpha = 0.4f),
        label = "borderColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("server_lifecycle_toggle_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                if (isRunning) listOf(ServerEmerald.copy(alpha = 0.6f), CyberCyan.copy(alpha = 0.5f))
                else listOf(Color(0xFF64748B).copy(alpha = 0.4f), Color(0xFF334155).copy(alpha = 0.3f))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Main Switch Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isRunning) ServerEmerald.copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                            )
                            .border(
                                1.dp,
                                if (isRunning) ServerEmerald.copy(alpha = 0.4f) else Color.Gray.copy(alpha = 0.2f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = "Server status",
                            tint = if (isRunning) ServerEmerald else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // The Toggle Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isRunning) "Running" else "Stopped",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isRunning) ServerEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Switch(
                        checked = isRunning,
                        onCheckedChange = { checked ->
                            onToggleServer(checked)
                        },
                        modifier = Modifier.testTag("start_stop_server_switch"),
                        thumbContent = {
                            if (isRunning) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Server ON",
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                    tint = Color(0xFF0F172A)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Server OFF",
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                    tint = Color.White
                                )
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ServerEmerald,
                            checkedTrackColor = ServerEmerald.copy(alpha = 0.35f),
                            checkedBorderColor = ServerEmerald,
                            uncheckedThumbColor = Color(0xFF64748B),
                            uncheckedTrackColor = Color(0xFF334155).copy(alpha = 0.5f),
                            uncheckedBorderColor = Color(0xFF475569)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status Badge & Endpoint preview
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .then(if (isRunning) Modifier.scale(pulseScale) else Modifier)
                                .clip(CircleShape)
                                .background(if (isRunning) ServerEmerald else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRunning) "http://${stats.ipAddress}:${stats.port}" else "Server Offline (Port ${stats.port})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isRunning) TelegramLightBlue else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "Connections",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isRunning) "${stats.activeConnections} clients" else "0 clients",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
