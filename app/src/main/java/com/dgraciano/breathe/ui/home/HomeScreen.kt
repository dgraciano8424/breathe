package com.dgraciano.breathe.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dgraciano.breathe.data.model.BlockedApp
import com.dgraciano.breathe.ui.theme.BreatheBackground
import com.dgraciano.breathe.ui.theme.BreathePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddApp: () -> Unit,
    onViewStats: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val apps by viewModel.blockedApps.collectAsState()
    val todayAttempts by viewModel.todayAttempts.collectAsState()
    val todayDeclined by viewModel.todayDeclined.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startService()
        viewModel.refreshStats()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Breathe", fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(onClick = onViewStats) {
                        Icon(Icons.Outlined.BarChart, contentDescription = "Stats")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BreatheBackground)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddApp,
                containerColor = BreathePrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add app", tint = Color.White)
            }
        },
        containerColor = BreatheBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Today summary card ──────────────────────────────────────
            item {
                if (todayAttempts > 0 || todayDeclined > 0) {
                    TodaySummaryCard(
                        attempts = todayAttempts,
                        declined = todayDeclined,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            if (apps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No apps monitored yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFE0DEFF)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Tap + to add apps you want to pause before opening.",
                                textAlign = TextAlign.Center,
                                color = Color(0xFF9090BB),
                                fontSize = 14.sp
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
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(apps, key = { it.packageName }) { app ->
                    BlockedAppRow(app = app, onRemove = { viewModel.removeApp(app) })
                }
            }
        }
    }
}

@Composable
private fun TodaySummaryCard(attempts: Int, declined: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E3A), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        SummaryItem(value = "$attempts", label = "Pauses today")
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(40.dp)
                .background(Color(0xFF2A2A4A))
        )
        SummaryItem(value = "$declined", label = "Resisted")
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(40.dp)
                .background(Color(0xFF2A2A4A))
        )
        SummaryItem(value = "${declined * 20}m", label = "Saved")
    }
}

@Composable
private fun SummaryItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BreathePrimary)
        Text(label, fontSize = 11.sp, color = Color(0xFF9090BB))
    }
}

@Composable
private fun BlockedAppRow(app: BlockedApp, onRemove: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(app.appName, color = Color(0xFFE0DEFF))
        },
        supportingContent = {
            Text(
                app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9090BB)
            )
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color(0xFF9090BB))
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
    HorizontalDivider(color = Color(0xFF2A2A4A))
}
