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
import com.dgraciano.breathe.ui.theme.*

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
                title = {
                    Text("Your Insights", color = BreatheTextPrimary, fontWeight = FontWeight.SemiBold)
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
                    accent = BreatheSecondary
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = "${state.todayDeclined * 20}m",
                    label = "Saved",
                    accent = BreathePrimary.copy(alpha = 0.75f)
                )
            }

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
                    accent = BreatheSecondary
                )
            }

            if (state.topApps.isNotEmpty()) {
                SectionLabel("Most Paused Apps")
                TopAppsCard(apps = state.topApps)
            }

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
        color = BreathePrimary,
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
            .background(BreatheSurface, RoundedCornerShape(16.dp))
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
            color = BreatheTextMuted
        )
    }
}

@Composable
private fun TopAppsCard(apps: List<AppStat>) {
    val max = apps.maxOfOrNull { it.count }?.toFloat() ?: 1f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BreatheSurface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        apps.forEach { app ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(app.appName, fontSize = 13.sp, color = BreatheTextPrimary)
                    Text("${app.count}×", fontSize = 13.sp, color = BreatheTextMuted)
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(BreatheDivider, RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(app.count / max)
                            .fillMaxHeight()
                            .background(
                                BreathePrimary.copy(alpha = 0.8f),
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
            .background(BreatheSurfaceHigh, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Column {
            Text(
                text = "You've resisted $declined times this week.",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = BreatheTextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "That's roughly $minutesSaved minutes back in your life.",
                fontSize = 13.sp,
                color = BreatheTextSecondary
            )
        }
    }
}
