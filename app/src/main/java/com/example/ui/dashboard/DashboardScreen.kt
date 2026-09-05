package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FormatUtils
import com.example.data.model.NetworkType
import com.example.ui.components.MetricCard
import com.example.ui.components.UsageAccessBanner
import com.example.viewmodel.DashboardUiState

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val snapshot = state.snapshot

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header: App Name & Subtitle & Status Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "NetWatch",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Lightweight Network Monitor",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Real-time Status Badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (state.isMonitoring) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    1.dp,
                    if (state.isMonitoring) MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outline
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (state.isMonitoring) MaterialTheme.colorScheme.secondary else Color.Gray,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (state.isMonitoring) "ACTIVE" else "IDLE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (state.isMonitoring) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Monitoring Mode Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("monitoring_mode_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Security Mode",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "MONITORING MODE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = state.monitoringMode,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "No VPN • Standard TrafficStats / NetworkStats",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Verified",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Current Real-time Speed Cards (Download & Upload Rates)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Download Speed Card
            MetricCard(
                title = "Download Speed",
                value = if (snapshot.isStatsSupported) FormatUtils.formatSpeed(snapshot.rxSpeedBps)
                        else "Unavailable in Lightweight Mode",
                subtitle = "Real delta: (current - prev) / Δt",
                icon = Icons.Default.ArrowDownward,
                iconTint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("download_speed_card")
            )

            // Upload Speed Card
            MetricCard(
                title = "Upload Speed",
                value = if (snapshot.isStatsSupported) FormatUtils.formatSpeed(snapshot.txSpeedBps)
                        else "Unavailable in Lightweight Mode",
                subtitle = "Real delta: (current - prev) / Δt",
                icon = Icons.Default.ArrowUpward,
                iconTint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("upload_speed_card")
            )
        }

        // Live Rate Pulse Wave (Purely visual presentation of real RX/TX activity)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE ACTIVITY PULSE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.secondary, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RX", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("TX", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val rxSpeed = snapshot.rxSpeedBps
                val txSpeed = snapshot.txSpeedBps
                val rxColor = MaterialTheme.colorScheme.secondary
                val txColor = MaterialTheme.colorScheme.tertiary

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    val width = size.width
                    val height = size.height

                    // Baseline
                    drawLine(
                        color = Color.DarkGray.copy(alpha = 0.5f),
                        start = Offset(0f, height / 2),
                        end = Offset(width, height / 2),
                        strokeWidth = 1f
                    )

                    // Draw amplitude proportional to real speeds (0 when 0)
                    val maxExpectedSpeed = 10_000_000f // 10 MB/s reference scale
                    val rxFraction = (rxSpeed.toFloat() / maxExpectedSpeed).coerceIn(0.05f, 1f)
                    val txFraction = (txSpeed.toFloat() / maxExpectedSpeed).coerceIn(0.05f, 1f)

                    if (rxSpeed > 0) {
                        val pathRx = Path()
                        pathRx.moveTo(0f, height / 2)
                        val segments = 8
                        for (i in 1..segments) {
                            val x = width * (i.toFloat() / segments)
                            val y = if (i % 2 == 1) height / 2 - (height * 0.4f * rxFraction) else height / 2 + (height * 0.1f * rxFraction)
                            pathRx.lineTo(x, y)
                        }
                        drawPath(path = pathRx, color = rxColor, style = Stroke(width = 2.5f))
                    }

                    if (txSpeed > 0) {
                        val pathTx = Path()
                        pathTx.moveTo(0f, height / 2)
                        val segments = 8
                        for (i in 1..segments) {
                            val x = width * (i.toFloat() / segments)
                            val y = if (i % 2 == 1) height / 2 + (height * 0.35f * txFraction) else height / 2 - (height * 0.1f * txFraction)
                            pathTx.lineTo(x, y)
                        }
                        drawPath(path = pathTx, color = txColor, style = Stroke(width = 2.5f))
                    }
                }
            }
        }

        // Cumulative Totals (Since Boot / System counters)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Total Downloaded",
                value = if (snapshot.isStatsSupported) FormatUtils.formatBytes(snapshot.totalRxBytes)
                        else "Unavailable in Lightweight Mode",
                subtitle = "Cumulative since boot",
                icon = Icons.Default.CloudDownload,
                iconTint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("total_rx_card")
            )

            MetricCard(
                title = "Total Uploaded",
                value = if (snapshot.isStatsSupported) FormatUtils.formatBytes(snapshot.totalTxBytes)
                        else "Unavailable in Lightweight Mode",
                subtitle = "Cumulative since boot",
                icon = Icons.Default.CloudUpload,
                iconTint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("total_tx_card")
            )
        }

        // Network Status & System Polling Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "NETWORK & SYSTEM STATUS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val netIcon = when (snapshot.networkType) {
                            NetworkType.WIFI -> Icons.Default.Wifi
                            NetworkType.MOBILE -> Icons.Default.CellTower
                            NetworkType.DISCONNECTED -> Icons.Default.WifiOff
                            else -> Icons.Default.NetworkCheck
                        }
                        Icon(
                            imageVector = netIcon,
                            contentDescription = snapshot.networkType.displayName,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Network Transport",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = snapshot.networkType.displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Interval",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Refresh Interval",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${state.refreshIntervalSec} seconds",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Show Usage Access banner if not yet granted
        if (!state.hasUsageAccess) {
            UsageAccessBanner()
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
