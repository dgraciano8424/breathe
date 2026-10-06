package com.dgraciano.breathe.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import java.text.DateFormat
import java.util.Date
import com.dgraciano.breathe.service.MonitoringSnapshot
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dgraciano.breathe.data.repository.MAX_REMINDER_LENGTH
import kotlinx.coroutines.launch
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
    val permissions by viewModel.permissions.collectAsState()
    val monitoring by viewModel.monitoring.collectAsState()
    val snoozedUntil by viewModel.snoozedUntil.collectAsState()
    val snoozeBusy by viewModel.snoozeBusy.collectAsState()
    val snoozeError by viewModel.snoozeError.collectAsState()
    val personalReminder by viewModel.personalReminder.collectAsState()
    val reminderSaving by viewModel.reminderSaving.collectAsState()
    val reminderError by viewModel.reminderError.collectAsState()
    var showReminderEditor by remember { mutableStateOf(false) }
    var reminderDraft by remember { mutableStateOf("") }
    val editScope = rememberCoroutineScope()
    val context = LocalContext.current
    var showTestPicker by remember { mutableStateOf(false) }
    var testError by remember { mutableStateOf<String?>(null) }
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

    LaunchedEffect(snoozedUntil) { if (snoozedUntil > 0) showTestPicker = false }

    if (showReminderEditor) {
        AlertDialog(
            onDismissRequest = { if (!reminderSaving) showReminderEditor = false },
            title = { Text("Your personal reminder") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("An optional nudge on every pause screen. Write what you would like to remember, such as taking a short walk. It stays on this device and is not saved in choice history or exports.")
                    OutlinedTextField(
                        value = reminderDraft,
                        onValueChange = { reminderDraft = it; viewModel.clearReminderError() },
                        label = { Text("Reminder") },
                        modifier = Modifier.fillMaxWidth(), maxLines = 3,
                        enabled = !reminderSaving,
                        isError = reminderDraft.length > MAX_REMINDER_LENGTH,
                        supportingText = { Text("${reminderDraft.length}/$MAX_REMINDER_LENGTH characters") }
                    )
                    if (reminderDraft.isNotEmpty()) TextButton(onClick = { reminderDraft = ""; viewModel.clearReminderError() }, enabled = !reminderSaving) { Text("Clear text") }
                    reminderError?.let { Text(it) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !reminderSaving && reminderDraft.length <= MAX_REMINDER_LENGTH,
                    onClick = { editScope.launch { if (viewModel.saveReminder(reminderDraft)) showReminderEditor = false } }
                ) { Text(if (reminderSaving) "Saving..." else "Save") }
            },
            dismissButton = { TextButton(onClick = { showReminderEditor = false }, enabled = !reminderSaving) { Text("Cancel") } }
        )
    }

    if (showTestPicker) {
        AlertDialog(
            onDismissRequest = { showTestPicker = false },
            title = { Text("Test your pause") },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose an app to open. A breathing pause should appear over it. This is a real visit; you can go back immediately.")
                    apps.forEach { row ->
                        TextButton(onClick = {
                            val launched = runCatching {
                                val intent = context.packageManager.getLaunchIntentForPackage(row.app.packageName)
                                    ?: error("Unavailable")
                                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }.isSuccess
                            if (launched) showTestPicker = false else testError = "That app could not be opened. Try another app."
                        }) { Text(row.app.appName) }
                    }
                    testError?.let { Text(it) }
                }
            },
            confirmButton = { TextButton(onClick = { showTestPicker = false }) { Text("Cancel") } }
        )
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
                item {
                    MonitoringCard(
                        access = permissions, status = monitoring, ready = isMonitoringActive, snoozed = snoozedUntil > 0,
                        hasApps = apps.isNotEmpty(), onFix = onFixPermissions,
                        onTest = { testError = null; showTestPicker = true },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                item {
                    SnoozeCard(snoozedUntil, snoozeBusy, snoozeError, viewModel::snooze, viewModel::resumePauses)
                }

                item {
                    OutlinedButton(
                        onClick = { reminderDraft = personalReminder; viewModel.clearReminderError(); showReminderEditor = true },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
                    ) { Text(if (personalReminder.isBlank()) "Add a personal reminder" else "Edit your personal reminder") }
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

/**
 * Shows permission grants separately from the live service and app-list connection.
 * Setup returns through the accessibility disclosure; testing opens a chosen app.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MonitoringCard(
    access: MonitoringPermissions,
    status: MonitoringSnapshot,
    ready: Boolean,
    snoozed: Boolean,
    hasApps: Boolean,
    onFix: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var details by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BreatheSand.copy(alpha = 0.14f))
            .padding(18.dp)
    ) {
        Text(
            if (snoozed && ready) "PAUSES ARE SNOOZED" else if (ready && status.issue == null) "MONITORING IS READY" else if (!access.accessibility || !access.overlay) "SETUP NEEDED" else "CHECK MONITORING",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = BreatheSand,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (snoozed && ready) "You are taking a break" else if (ready) "Your pause is ready to try" else if (!access.accessibility || !access.overlay) "Breathe needs both permissions" else "Android has not confirmed a working connection",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = BreatheTextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            status.issue ?: if (snoozed && ready) {
                "Your app list and permissions stay saved. Resume below when you are ready."
            } else if (ready) {
                if (hasApps) "Try opening one of your chosen apps to confirm a pause appears." else "Add one app below, then test your pause."
            } else if (!access.accessibility || !access.overlay) {
                "Check both permissions in setup. Your chosen apps and history are saved."
            } else if (!status.connected) {
                "The permission is enabled, but the service is disconnected. Check setup and switch Breathe off and on in Accessibility settings."
            } else "Your chosen apps are loading. Reopen Breathe if this does not finish.",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = BreatheTextSecondary
        )
        FlowRow {
            TextButton(onClick = onTest, enabled = ready && hasApps && !snoozed) { Text("Test a pause") }
            TextButton(onClick = onFix) { Text("Check setup") }
            TextButton(onClick = { details = !details }) { Text(if (details) "Hide details" else "Details") }
        }
        if (details) {
            Text("Accessibility permission: ${if (access.accessibility) "on" else "off"}\nService connection: ${if (status.connected) "connected" else "disconnected"}\nDisplay over other apps: ${if (access.overlay) "allowed" else "needed"}\nApp list: ${if (status.appsLoaded) "loaded" else "waiting"}", fontSize = 12.sp, color = BreatheTextSecondary)
            Text(status.lastPauseAt?.let { "Last pause shown: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))}" }
                ?: "No pause has been confirmed in this app session yet.", fontSize = 12.sp, color = BreatheTextSecondary)
        }
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
    val next = progress.nextLevel ?: return "${progress.activeDays} active days — your rhythm keeps growing"
    val remaining = (next.minDays - progress.activeDays).coerceAtLeast(0)
    return "$remaining more active ${if (remaining == 1L) "day" else "days"} until ${next.name}"
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
                "YOUR PATTERNS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BreatheSecondary,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = progress?.let { "${it.activeDays} active ${if (it.activeDays == 1L) "day" else "days"} · ${it.lifetimeChoices} ${if (it.lifetimeChoices == 1L) "choice" else "choices"}" } ?: "See your progress",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = BreatheTextPrimary
            )
            if (progress != null) {
                Text(
                    text = "Continue and Go back both count",
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
        SummaryItem(value = "$attempts", label = "Choices today")
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(36.dp)
                .background(BreatheDivider)
        )
        SummaryItem(value = "$declined", label = "Went back")
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(36.dp)
                .background(BreatheDivider)
        )
        SummaryItem(value = formatMinutes(minutesSaved.toLong()), label = "Estimated time")
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
@OptIn(ExperimentalLayoutApi::class)
private fun PauseDurationPicker(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SnoozeCard(until: Long, busy: Boolean, error: String?, onSnooze: (Int) -> Unit, onResume: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)).background(BreatheSurface.copy(alpha = 0.7f)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Take a break", fontWeight = FontWeight.SemiBold, color = BreatheTextPrimary)
        Text(
            if (until > 0) "Pauses resume at ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(until))}."
            else "Snooze all app pauses for a little while. Your app list and history stay saved.",
            color = BreatheTextSecondary, fontSize = 13.sp
        )
        if (until > 0) {
            OutlinedButton(onClick = onResume, enabled = !busy) { Text("Resume now") }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60).forEach { minutes ->
                    OutlinedButton(onClick = { onSnooze(minutes) }, enabled = !busy) {
                        Text(if (minutes == 60) "1 hour" else "$minutes minutes")
                    }
                }
            }
        }
        error?.let { Text(it, color = BreatheTextSecondary, fontSize = 13.sp) }
    }
}
