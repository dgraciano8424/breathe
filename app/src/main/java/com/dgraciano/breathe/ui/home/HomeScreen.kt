package com.dgraciano.breathe.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dgraciano.breathe.data.model.BlockedApp
import com.dgraciano.breathe.data.model.UserProgress
import com.dgraciano.breathe.ui.components.NimbusBuddy
import com.dgraciano.breathe.ui.components.WaveBackground
import com.dgraciano.breathe.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddApp: () -> Unit,
    onViewStats: () -> Unit,
    onAchievements: () -> Unit,
    onFixPermissions: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val apps by viewModel.blockedApps.collectAsState()
    val todayAttempts by viewModel.todayAttempts.collectAsState()
    val todayDeclined by viewModel.todayDeclined.collectAsState()
    val todayMinutesSaved by viewModel.todayMinutesSaved.collectAsState()
    val nimbusStrength by viewModel.nimbusStrength.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isMonitoringActive by viewModel.isMonitoringActive.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Both permissions are revoked from Settings, which does not take this screen out of
    // composition — so a LaunchedEffect would never re-run and the screen would keep
    // claiming monitoring was on. Same pattern as OnboardingScreen, for the same reason.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshMonitoringState()
                viewModel.refreshStats()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize().background(BreatheBackground)) {
        WaveBackground(modifier = Modifier.fillMaxSize())

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Breathe",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = BreatheTextPrimary
                        )
                    },
                    actions = {
                        IconButton(onClick = onAchievements) {
                            Icon(
                                Icons.Outlined.EmojiEvents,
                                contentDescription = "Achievements",
                                tint = BreatheTextSecondary
                            )
                        }
                        IconButton(onClick = onViewStats) {
                            Icon(
                                Icons.Outlined.BarChart,
                                contentDescription = "Stats",
                                tint = BreatheTextSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onAddApp,
                    containerColor = BreathePrimary,
                    contentColor = BreatheOnPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add app")
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // First, and above everything else: if monitoring is off, no other number
                // on this screen means what it appears to mean.
                if (!isMonitoringActive) {
                    item {
                        MonitoringOffCard(
                            onFix = onFixPermissions,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Level name is shown by the Journey card directly below, so
                        // Nimbus stays uncaptioned here to avoid repeating it.
                        NimbusBuddy(strength = nimbusStrength)
                    }
                }

                item {
                    JourneyCard(
                        progress = progress,
                        onClick = onAchievements,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                item {
                    if (todayAttempts > 0 || todayDeclined > 0) {
                        TodaySummaryCard(
                            attempts = todayAttempts,
                            declined = todayDeclined,
                            minutesSaved = todayMinutesSaved,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }

                item {
                    InsightsCard(
                        progress = progress,
                        onClick = onViewStats,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                if (apps.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { onAddApp() }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(
                                            Brush.radialGradient(
                                                listOf(
                                                    BreathePrimary.copy(alpha = 0.2f),
                                                    BreatheSecondary.copy(alpha = 0.05f)
                                                )
                                            ),
                                            RoundedCornerShape(32.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        tint = BreathePrimary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "No apps monitored yet",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    color = BreatheTextPrimary
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Tap here to add apps you want\na mindful pause before opening.",
                                    textAlign = TextAlign.Center,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = BreatheTextSecondary
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "MONITORED APPS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BreathePrimary,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                    items(apps, key = { it.app.packageName }) { appWithStats ->
                        BlockedAppRow(
                            app = appWithStats.app,
                            usageMinutes = appWithStats.usageMinutes,
                            onRemove = { viewModel.removeApp(appWithStats.app) },
                            onPauseSecondsChange = { seconds ->
                                viewModel.setPauseSeconds(appWithStats.app.packageName, seconds)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Prominent entry point into the achievements ("Your Journey") screen. */
/**
 * Shown when accessibility or overlay permission is missing.
 *
 * Without both, no pause can appear — and because everything else on this screen is a
 * count of past pauses, the app otherwise looks like it is working and simply has nothing
 * to report yet. This says the quiet part out loud.
 *
 * Tapping returns to onboarding rather than jumping straight to Settings, so the
 * accessibility disclosure and consent step are not bypassed.
 */
@Composable
private fun MonitoringOffCard(
    onFix: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BreatheSand.copy(alpha = 0.14f))
            .clickable { onFix() }
            .padding(18.dp)
    ) {
        Text(
            "MONITORING IS OFF",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = BreatheSand,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Breathe cannot pause anything right now",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = BreatheTextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "A permission it needs was turned off. Some phones do this on their own to " +
                "save battery. Tap to check the setup.",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = BreatheTextSecondary
        )
    }
}

@Composable
private fun JourneyCard(
    progress: UserProgress?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val level = progress?.currentLevel
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        BreathePrimary.copy(alpha = 0.18f),
                        BreatheSecondary.copy(alpha = 0.10f)
                    )
                )
            )
            .clickable { onClick() }
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "YOUR JOURNEY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BreathePrimary,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = level?.let { "${it.emoji}  ${it.name}" } ?: "Beginning your journey",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BreatheTextPrimary
                )
                if (level != null) {
                    Text(
                        text = level.description,
                        fontSize = 13.sp,
                        color = BreatheTextSecondary
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = BreathePrimary
            )
        }

        if (progress != null) {
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { progress.progressToNext },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = BreathePrimary,
                trackColor = BreatheDivider
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = nextLevelLabel(progress),
                fontSize = 12.sp,
                color = BreatheTextMuted
            )
        }
    }
}

private fun nextLevelLabel(progress: UserProgress): String {
    val next = progress.nextLevel ?: return "Highest level reached — ${progress.hoursDisplay} reclaimed"
    val remaining = (next.minMinutes - progress.totalMinutesSaved).coerceAtLeast(0)
    return "${formatMinutes(remaining)} of mindful time until ${next.name}"
}

/** Entry point into the stats ("Insights & Fulfillment") screen. */
@Composable
private fun InsightsCard(
    progress: UserProgress?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BreatheSurface.copy(alpha = 0.7f))
            .clickable { onClick() }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "INSIGHTS & FULFILLMENT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BreatheSecondary,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = progress?.let { "${it.hoursDisplay} reclaimed so far" } ?: "See your progress",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = BreatheTextPrimary
            )
            if (progress != null) {
                Text(
                    text = "${progress.lifetimeDeclines} mindful choices made",
                    fontSize = 13.sp,
                    color = BreatheTextSecondary
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = BreatheSecondary
        )
    }
}

@Composable
private fun TodaySummaryCard(
    attempts: Int,
    declined: Int,
    minutesSaved: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(BreatheSurface.copy(alpha = 0.8f), BreatheSurfaceHigh.copy(alpha = 0.8f))),
                RoundedCornerShape(16.dp)
            )
            .padding(vertical = 18.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SummaryItem(value = "$attempts", label = "Pauses today")
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(36.dp)
                .background(BreatheDivider)
        )
        SummaryItem(value = "$declined", label = "Resisted")
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(36.dp)
                .background(BreatheDivider)
        )
        SummaryItem(value = formatMinutes(minutesSaved.toLong()), label = "Saved")
    }
}

private fun formatMinutes(minutes: Long): String {
    if (minutes <= 0) return "0m"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

@Composable
private fun SummaryItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = BreathePrimary)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = BreatheTextMuted)
    }
}

@Composable
private fun BlockedAppRow(
    app: BlockedApp,
    usageMinutes: Int,
    onRemove: () -> Unit,
    onPauseSecondsChange: (Int) -> Unit
) {
    ListItem(
        headlineContent = {
            Text(app.appName, color = BreatheTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Column {
                Text(
                    text = formatUsage(usageMinutes),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (usageMinutes > 60) Color(0xFFFF8A80) else BreatheTextSecondary
                )
                Spacer(Modifier.height(6.dp))
                PauseDurationPicker(
                    selected = app.pauseSeconds,
                    onSelect = onPauseSecondsChange
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove ${app.appName}",
                    tint = BreatheTextMuted
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
    HorizontalDivider(color = BreatheDivider, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
}

/** Row of pause lengths; the selected one is filled in. */
@Composable
private fun PauseDurationPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Pause",
            fontSize = 11.sp,
            color = BreatheTextMuted
        )
        BlockedApp.PAUSE_OPTIONS.forEach { seconds ->
            val isSelected = seconds == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) BreathePrimary.copy(alpha = 0.22f) else Color.Transparent
                    )
                    .clickable { onSelect(seconds) }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${seconds}s",
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) BreathePrimary else BreatheTextSecondary
                )
            }
        }
    }
}

private fun formatUsage(minutes: Int): String {
    if (minutes == 0) return "Mindful today - no usage yet"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 -> "$h h $m m spent this week"
        else -> "$m m spent this week"
    }
}
