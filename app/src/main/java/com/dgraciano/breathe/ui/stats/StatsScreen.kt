package com.dgraciano.breathe.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dgraciano.breathe.data.model.AppStat
import com.dgraciano.breathe.ui.theme.BreatheBackground
import com.dgraciano.breathe.ui.theme.BreathePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadStats() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Insights") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BreatheBackground)
            )
        },
        containerColor = BreatheBackground
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BreathePrimary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Today ───────────────────────────────────────────────────
            SectionLabel("Today")
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
                    accent = Color(0xFF4CAF50)
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = "${state.todayDeclined * 20}m",
                    label = "Saved",
                    accent = Color(0xFFFF9800)
                )
            }

            // ── This week ───────────────────────────────────────────────
            SectionLabel("This Week")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = "${state.weeklyAttempts}",
                    label = "Total pauses",
                    accent = BreathePrimary
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = "${state.weeklyDeclined}",
                    label = "Times resisted",
                    accent = Color(0xFF4CAF50)
                )
            }

            // ── Top blocked apps ─────────────────────────────────────────
            if (state.topApps.isNotEmpty()) {
                SectionLabel("Most Paused Apps")
                TopAppsCard(apps = state.topApps)
            }

            // ── Motivational footer ──────────────────────────────────────
            if (state.weeklyDeclined > 0) {
                MotivationCard(declined = state.weeklyDeclined)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF6C63FF),
        letterSpacing = 1.5.sp
    )
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    accent: Color
) {
    Column(
        modifier = modifier
            .background(Color(0xFF1E1E3A), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color(0xFF9090BB)
        )
    }
}

@Composable
private fun TopAppsCard(apps: List<AppStat>) {
    val max = apps.maxOfOrNull { it.count }?.toFloat() ?: 1f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E3A), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        apps.forEach { app ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(app.appName, fontSize = 13.sp, color = Color(0xFFE0DEFF))
                    Text("${app.count}×", fontSize = 13.sp, color = Color(0xFF9090BB))
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(Color(0xFF2A2A4A), RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(app.count / max)
                            .fillMaxHeight()
                            .background(
                                Color(0xFF6C63FF).copy(alpha = 0.8f),
                                RoundedCornerShape(3.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun MotivationCard(declined: Int) {
    val minutesSaved = declined * 20
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A2740), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Column {
            Text(
                text = "You've resisted $declined times this week.",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF0EEFF)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "That's roughly $minutesSaved minutes back in your life.",
                fontSize = 13.sp,
                color = Color(0xFF9090BB)
            )
        }
    }
}
