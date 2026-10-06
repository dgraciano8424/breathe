package com.dgraciano.breathe.ui.stats

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NaturePeople
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.dgraciano.breathe.ui.components.rememberReducedMotion
import com.dgraciano.breathe.data.model.AppStat
import com.dgraciano.breathe.ui.components.WaveBackground
import com.dgraciano.breathe.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val working by viewModel.working.collectAsState()
    val reducedMotion = rememberReducedMotion()
    var confirmClear by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { destination ->
        destination?.let(viewModel::exportHistory)
    }
    var showContent by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.loadStats()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (confirmClear) {
        AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear your history?") },
            text = { Text("Delete all recorded choices and reset your progress and estimates? Your monitored apps and pause lengths stay saved. This cannot be undone. Export first if you want a copy.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; viewModel.clearHistory() }) { Text("Clear history") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep history") } })
    }

    LaunchedEffect(Unit) {
        showContent = true
    }

    Box(modifier = Modifier.fillMaxSize().background(BreatheBackground)) {
        WaveBackground(modifier = Modifier.fillMaxSize())

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("Your patterns", color = BreatheTextPrimary, fontWeight = FontWeight.SemiBold)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = BreatheTextSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            if (state.isLoading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BreathePrimary)
                }
                return@Scaffold
            }

            if (state.errorMessage != null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(state.errorMessage.orEmpty(), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadStats() }) { Text("Try again") }
                }
                return@Scaffold
            }

            AnimatedVisibility(
                visible = showContent,
                enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(250)),
                exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(150))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Fulfillment Section
                    FulfillmentSection(
                        choices = state.todayAttempts,
                        declined = state.todayDeclined,
                        minutesSaved = state.todayMinutesSaved
                    )

                    SectionLabel("Daily Rhythm")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            value = "${state.todayAttempts}",
                            label = "Choices",
                            accent = BreathePrimary
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            value = "${state.todayDeclined}",
                            label = "Went back",
                            accent = BreatheSecondary
                        )
                    }

                    SectionLabel("Weekly Growth")
                    StatCardLarge(
                        value = "${state.weeklyDeclined}",
                        label = "Times you chose to go back",
                        subtext = if (state.weeklyMinutesSaved > 0) {
                            "Estimated skipped session time: ${formatDuration(state.weeklyMinutesSaved)} this week."
                        } else {
                            "Your choices this week will appear here."
                        },
                        accent = BreatheSecondary
                    )

                    if (state.topApps.isNotEmpty()) {
                        SectionLabel("Most Frequent Pauses")
                        TopAppsCard(apps = state.topApps)
                    }
                    
                    SectionLabel("Your history")
                    Text("Export a CSV with app names, choice times and optional reasons. Choose where it is saved. Clearing history also resets your milestones.", color = BreatheTextSecondary, fontSize = 14.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { export.launch("breathe-history-${java.time.LocalDate.now()}.csv") }, enabled = !working) { Text("Export history CSV") }
                        TextButton(onClick = { confirmClear = true }, enabled = !working) { Text("Clear history") }
                    }
                    if (working) Text("Working on your history…", color = BreatheTextSecondary)
                    notice?.let { Text(it, color = BreatheTextSecondary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
fun FulfillmentSection(choices: Int, declined: Int, minutesSaved: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Recent choices Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BreathePrimary.copy(alpha = 0.1f)),
            border = BorderStroke(1.dp, BreathePrimary.copy(alpha = 0.4f))
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = BreathePrimary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Recent choices", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = BreatheTextPrimary)
                    Text("${(choices - declined).coerceAtLeast(0)} continued · $declined went back", color = BreatheSecondary, fontSize = 14.sp)
                }
            }
        }

        // Life Won Back Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BreatheSurface.copy(alpha = 0.7f)),
            border = BorderStroke(1.dp, BreatheDivider)
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NaturePeople, contentDescription = null, tint = BreatheSecondary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Estimated skipped session time", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BreatheTextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (minutesSaved > 0) formatDuration(minutesSaved) else "Nothing yet today",
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = BreatheSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (minutesSaved > 0) {
                            "These estimates stay separate from your milestones."
                        } else {
                            "An estimate appears after you choose to go back."
                        },
                        color = BreatheTextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
        
        Text(
            text = "These are estimates based on past app sessions, or a 20-minute fallback when no history is available. Breathe cannot measure what you do after going back. Continuing can be an intentional choice too.",
            fontSize = 12.sp,
            color = BreatheTextMuted,
            fontStyle = FontStyle.Italic,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

private fun formatDuration(minutes: Int): String {
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
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = BreatheSecondary,
        letterSpacing = 2.sp
    )
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    accent: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BreatheSurface.copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, BreatheDivider)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = BreatheTextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatCardLarge(value: String, label: String, subtext: String, accent: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BreatheSurface.copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, BreatheDivider)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = label,
                    fontSize = 14.sp,
                    color = BreatheTextPrimary,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(subtext, fontSize = 12.sp, color = BreatheTextMuted)
        }
    }
}

@Composable
private fun TopAppsCard(apps: List<AppStat>) {
    val max = apps.maxOfOrNull { it.count }?.toFloat() ?: 1f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BreatheSurface.copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, BreatheDivider)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            apps.forEach { app ->
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(app.appName, fontSize = 14.sp, color = BreatheTextPrimary, fontWeight = FontWeight.Medium)
                        Text("${app.count} ${if (app.count == 1) "choice" else "choices"}", fontSize = 12.sp, color = BreatheTextMuted)
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(BreatheDivider, RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(app.count / max)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(listOf(BreathePrimary, BreatheSecondary)),
                                    RoundedCornerShape(4.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}
