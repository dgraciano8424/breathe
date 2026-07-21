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
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadStats()
        showContent = true
    }

    Box(modifier = Modifier.fillMaxSize().background(BreatheBackground)) {
        WaveBackground(modifier = Modifier.fillMaxSize())

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("Insights & Fulfillment", color = BreatheTextPrimary, fontWeight = FontWeight.SemiBold)
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BreathePrimary)
                }
                return@Scaffold
            }

            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { 50 }
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
                        streak = state.focusStreak,
                        activity = state.lifeWonBackActivity
                    )

                    SectionLabel("Daily Rhythm")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            value = "${state.todayAttempts}",
                            label = "Pauses",
                            accent = BreathePrimary
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            value = "${state.todayDeclined}",
                            label = "Resisted",
                            accent = BreatheSecondary
                        )
                    }

                    SectionLabel("Weekly Growth")
                    StatCardLarge(
                        value = "${state.weeklyDeclined}",
                        label = "Total times you chose presence over scrolling",
                        subtext = "That's roughly ${state.weeklyDeclined * 20} minutes saved this week.",
                        accent = BreatheSecondary
                    )

                    if (state.topApps.isNotEmpty()) {
                        SectionLabel("Most Frequent Pauses")
                        TopAppsCard(apps = state.topApps)
                    }
                    
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
fun FulfillmentSection(streak: Int, activity: String) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Focus Streak Card
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
                    Text("Focus Streak", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = BreatheTextPrimary)
                    Text("$streak consecutive mindful choices", color = BreatheSecondary, fontSize = 14.sp)
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
                    Text("Time Won Back", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BreatheTextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "You've earned enough time today to $activity.",
                        color = BreatheTextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
        
        Text(
            text = "Tip: High-dopamine scrolling creates 'attention residue'. Even a 5-minute walk clears your mind more than 50 minutes of scrolling.",
            fontSize = 12.sp,
            color = BreatheTextMuted,
            fontStyle = FontStyle.Italic,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
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
                        Text("${app.count} pauses", fontSize = 12.sp, color = BreatheTextMuted)
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
