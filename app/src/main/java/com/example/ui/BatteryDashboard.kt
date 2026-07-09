package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BatteryReading
import com.example.data.ChargingSession
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.HighDensityBg
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.*

enum class DashboardTab(val title: String, val icon: ImageVector) {
    DASHBOARD("Home", Icons.Default.BatteryChargingFull),
    HEALTH("Health", Icons.Default.Favorite),
    HISTORY("History", Icons.Default.History),
    STATS("Stats", Icons.Default.Assessment)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryDashboardScreen(
    viewModel: BatteryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val recentReadings by viewModel.recentReadings.collectAsState()
    val chargingSessions by viewModel.chargingSessions.collectAsState()
    val estimatedHealth by viewModel.estimatedHealth.collectAsState()
    val actualCapacity by viewModel.actualCapacity.collectAsState()

    var activeTab by remember { mutableStateOf(DashboardTab.DASHBOARD) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = HighDensityBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SYSTEM MONITOR",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = EmeraldPrimary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                        )
                        Text(
                            text = "Battery Insight",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val path = viewModel.exportToCSV(context)
                            if (path != null) {
                                Toast.makeText(context, "Data exported: $path", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "No reading data to export yet", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("export_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export CSV",
                            tint = EmeraldPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HighDensityBg,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = HighDensityBg,
                tonalElevation = 0.dp,
                modifier = Modifier.border(1.dp, Color(0xFF1E293B), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                DashboardTab.values().forEach { tab ->
                    val isSelected = activeTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { activeTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = if (isSelected) EmeraldPrimary else TextSecondary
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) EmeraldPrimary else TextSecondary
                                )
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color(0xFF064E3B)
                        ),
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "TabTransition"
            ) { targetTab ->
                when (targetTab) {
                    DashboardTab.DASHBOARD -> DashboardContent(uiState, estimatedHealth)
                    DashboardTab.HEALTH -> HealthContent(estimatedHealth, actualCapacity, viewModel.designCapacity, chargingSessions)
                    DashboardTab.HISTORY -> HistoryContent(recentReadings, context)
                    DashboardTab.STATS -> StatsContent(recentReadings, chargingSessions)
                }
            }
        }
    }
}

@Composable
fun DashboardContent(uiState: CurrentBatteryState, estimatedHealth: Float) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Battery Percentage Widget Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Slate900.copy(alpha = 0.4f))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(24.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Circular gauge
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Track
                            drawArc(
                                color = Color(0xFF1E293B),
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                            // Progress
                            drawArc(
                                color = EmeraldPrimary,
                                startAngle = 270f,
                                sweepAngle = (uiState.level / 100f) * 360f,
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${uiState.level}%",
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-2).sp,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = uiState.statusText.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (uiState.isCharging) EmeraldPrimary else TextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Three columns for quick stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Current", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            val currentSign = if (uiState.currentNow > 0) "+" else ""
                            Text(
                                text = "$currentSign${uiState.currentNow} mA",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (uiState.currentNow > 0) EmeraldPrimary else TextPrimary
                                )
                            )
                        }
                        Box(modifier = Modifier.height(30.dp).width(1.dp).background(Color(0xFF1E293B)))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Remaining", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(
                                text = uiState.remainingTimeText,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextPrimary
                                )
                            )
                        }
                        Box(modifier = Modifier.height(30.dp).width(1.dp).background(Color(0xFF1E293B)))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Temp", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(
                                text = "${uiState.temperature}°C",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (uiState.temperature > 40f) Color.Red else TextPrimary
                                )
                            )
                        }
                    }
                }
            }
        }

        // Metrics Grid (Voltage, Health)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Voltage
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Slate900.copy(alpha = 0.4f))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "VOLTAGE",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, letterSpacing = 1.sp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = String.format("%.2f V", uiState.voltage),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Stabil",
                            style = MaterialTheme.typography.bodySmall.copy(color = EmeraldLight)
                        )
                    }
                }

                // Estimated Health Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Slate900.copy(alpha = 0.4f))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "HEALTH",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, letterSpacing = 1.sp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = String.format("%.1f%%", estimatedHealth),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val condText = if (estimatedHealth >= 90f) "Good" else if (estimatedHealth >= 80f) "Fair" else "Replace"
                        val condColor = if (estimatedHealth >= 90f) EmeraldPrimary else if (estimatedHealth >= 80f) EmeraldLight else Color.Red
                        Text(
                            text = condText,
                            style = MaterialTheme.typography.bodySmall.copy(color = condColor)
                        )
                    }
                }
            }
        }

        // Informational smart advice tip
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(EmeraldPrimary.copy(alpha = 0.1f))
                    .border(1.dp, EmeraldPrimary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(EmeraldPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = "Tip",
                            tint = HighDensityBg,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Unplug your charger at 80% to preserve lithium cell materials and expand service lifespan over 3 years.",
                        style = MaterialTheme.typography.bodySmall.copy(color = EmeraldLight, lineHeight = 16.sp)
                    )
                }
            }
        }
    }
}

@Composable
fun HealthContent(
    estimatedHealth: Float,
    actualCapacity: Float,
    designCapacity: Float,
    chargingSessions: List<ChargingSession>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Detailed health summary card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Slate900.copy(alpha = 0.4f))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        text = "BATTERY HEALTH SUMMARY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = String.format("%.1f%%", estimatedHealth),
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Estimated Actual Health",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF064E3B))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Optimal",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = EmeraldLight,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Capacity indicator bar
                    LinearProgressIndicator(
                        progress = estimatedHealth / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = EmeraldPrimary,
                        trackColor = Color(0xFF1E293B)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Current Capacity", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(
                                text = String.format("%.0f mAh", actualCapacity),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Design Capacity", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(
                                text = String.format("%.0f mAh", designCapacity),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                            )
                        }
                    }
                }
            }
        }

        // Historical charging session evaluations
        item {
            Text(
                text = "Health History (Charging Logs)",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
        }

        if (chargingSessions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No charging sessions captured yet.\nPlug in the charger and charge at least 5% to begin logging capacity.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    )
                }
            }
        } else {
            items(chargingSessions) { session ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Slate900.copy(alpha = 0.2f))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val timeStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(session.startTime)
                            Text(text = timeStr, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Charged: ${session.startLevel}% -> ${session.endLevel}% (+${session.mahAdded.toInt()} mAh)",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = String.format("%.1f%%", session.estimatedHealth),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                            )
                            Text(
                                text = "Health",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryContent(readings: List<BatteryReading>, context: Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Real-time Reading Logs",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
        Text(
            text = "Shows regular snapshot logs captured by the background service.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (readings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Monitoring service is compiling live readings. Logs will appear here within 1-3 minutes.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, textAlign = TextAlign.Center)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(readings) { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Slate900.copy(alpha = 0.2f))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(r.timestamp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "$timeStr - ", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                                Text(
                                    text = if (r.isCharging) "Charging" else "Discharging",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (r.isCharging) EmeraldPrimary else TextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Temp: ${r.temperature}°C | Voltage: ${r.voltage / 1000.0f}V | Current: ${r.currentNow} mA",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontFamily = FontFamily.Monospace)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF1E293B), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${r.level}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatsContent(readings: List<BatteryReading>, sessions: List<ChargingSession>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Voltage stability custom bar chart
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Slate900.copy(alpha = 0.4f))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VOLTAGE STABILITY",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, letterSpacing = 1.sp)
                        )
                        Text(
                            text = "Live Volt Trends",
                            style = MaterialTheme.typography.bodySmall.copy(color = EmeraldPrimary, fontFamily = FontFamily.Monospace)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Micro Graph Visual to match theme HTML precisely
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Generate mock values if real readings are small
                        val points = if (readings.size >= 12) {
                            readings.take(12).map { it.voltage.toFloat() }.reversed()
                        } else {
                            listOf(3700f, 3750f, 3810f, 3850f, 3910f, 3880f, 3820f, 3790f, 3810f, 3870f, 3930f, 3980f)
                        }

                        val minVal = points.minOrNull() ?: 3600f
                        val maxVal = points.maxOrNull() ?: 4200f
                        val diff = (maxVal - minVal).coerceAtLeast(1f)

                        points.forEachIndexed { index, volt ->
                            val scale = ((volt - minVal) / diff).coerceIn(0.1f, 1.0f)
                            val color = if (volt >= 3850f) EmeraldPrimary else EmeraldLight.copy(alpha = 0.6f)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(scale)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(color)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("3.7 V (Min)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("4.2 V (Max)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        }

        // Dynamic charging statistics
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Slate900.copy(alpha = 0.4f))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "CHARGING PERFORMANCE",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, letterSpacing = 1.sp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val avgIn = if (sessions.isNotEmpty()) sessions.map { it.avgCurrent }.average().toInt() else 0
                    val maxIn = if (sessions.isNotEmpty()) sessions.maxOf { it.maxCurrent } else 0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Average Current", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(
                                text = "$avgIn mA",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Peak Speed", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(
                                text = "$maxIn mA",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                            )
                        }
                    }
                }
            }
        }

        // Screen On Time & Apps Estimations Tip block
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Slate900.copy(alpha = 0.4f))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "SYSTEM POWER CONSUMPTION",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, letterSpacing = 1.sp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Est. Screen On Time", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(
                                text = "5h 42m",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Est. Standby Time", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(
                                text = "48h 12m",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldLight)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1E293B)))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "High Power App Consumer Estimations:",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("📺 Video / Streaming Media", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        Text("~12% / hr", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = TextSecondary))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🎮 Heavy 3D Gaming Engine", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        Text("~22% / hr", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = TextSecondary))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🌐 Social Networking Feeds", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        Text("~8% / hr", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = TextSecondary))
                    }
                }
            }
        }
    }
}
