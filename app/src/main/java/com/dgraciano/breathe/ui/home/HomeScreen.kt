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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dgraciano.breathe.data.model.BlockedApp
import com.dgraciano.breathe.ui.theme.*

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
                    Text(
                        "Breathe",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = BreatheTextPrimary
                    )
                },
                actions = {
                    IconButton(onClick = onViewStats) {
                        Icon(
                            Icons.Outlined.BarChart,
                            contentDescription = "Stats",
                            tint = BreatheTextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BreatheBackground)
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
        containerColor = BreatheBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                if (todayAttempts > 0 || todayDeclined > 0) {
                    TodaySummaryCard(
                        attempts = todayAttempts,
                        declined = todayDeclined,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
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
                                text = "Tap + to add apps you want\na mindful pause before opening.",
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
            .background(
                Brush.horizontalGradient(listOf(BreatheSurface, BreatheSurfaceHigh)),
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
        SummaryItem(value = "${declined * 20}m", label = "Saved")
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
private fun BlockedAppRow(app: BlockedApp, onRemove: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(app.appName, color = BreatheTextPrimary, fontSize = 15.sp)
        },
        supportingContent = {
            Text(
                app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = BreatheTextMuted
            )
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = BreatheTextMuted
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    )
    HorizontalDivider(color = BreatheDivider, thickness = 0.5.dp)
}
