package com.dgraciano.breathe.ui.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dgraciano.breathe.data.model.Achievements
import com.dgraciano.breathe.data.model.MilestoneBadge
import com.dgraciano.breathe.data.model.UserProgress
import com.dgraciano.breathe.ui.components.WaveBackground
import com.dgraciano.breathe.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = hiltViewModel()
) {
    val progress by viewModel.progress.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    Box(modifier = Modifier.fillMaxSize().background(BreatheBackground)) {
        WaveBackground(modifier = Modifier.fillMaxSize())

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("Your Journey", color = BreatheTextPrimary, fontWeight = FontWeight.SemiBold)
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
            if (progress == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BreathePrimary)
                }
                return@Scaffold
            }

            val p = progress!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }

                // Level card
                item { LevelCard(p) }

                // Time saved summary
                item { TimeSavedCard(p) }

                // Level path
                item {
                    Text(
                        "PROGRESS PATH",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = BreathePrimary, letterSpacing = 2.sp
                    )
                }
                item { LevelPath(currentIndex = p.currentLevel.index) }

                // Milestone badges
                item {
                    Text(
                        "MILESTONES",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = BreathePrimary, letterSpacing = 2.sp
                    )
                }
                item { BadgeGrid(badges = p.badges) }

                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun LevelCard(p: UserProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BreatheSurface.copy(alpha = 0.8f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, BreatheDivider)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(24.dp)
        ) {
            Text(p.currentLevel.emoji, fontSize = 64.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                p.currentLevel.name,
                fontSize = 26.sp, fontWeight = FontWeight.Bold, color = BreathePrimary
            )
            Text(
                p.currentLevel.description,
                fontSize = 14.sp, color = BreatheTextSecondary,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)
            )

            if (p.nextLevel != null) {
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Current", fontSize = 11.sp, color = BreatheTextMuted)
                    Text("Next Level", fontSize = 11.sp, color = BreatheTextMuted)
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(BreatheDivider, RoundedCornerShape(5.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(p.progressToNext)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(listOf(BreathePrimary, BreatheSecondary)),
                                RoundedCornerShape(5.dp)
                            )
                    )
                }
                Spacer(Modifier.height(8.dp))
                val needed = p.nextLevel.minMinutes - p.totalMinutesSaved
                Text(
                    "${formatMinutes(needed)} until ${p.nextLevel.name} ${p.nextLevel.emoji}",
                    fontSize = 12.sp, color = BreatheTextMuted, textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun TimeSavedCard(p: UserProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BreatheSurface.copy(alpha = 0.6f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, BreatheDivider)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            TimeStat(value = p.hoursDisplay, label = "Time saved")
            Box(Modifier.width(1.dp).height(40.dp).background(BreatheDivider))
            TimeStat(value = "${p.lifetimeDeclines}", label = "Resisted")
            Box(Modifier.width(1.dp).height(40.dp).background(BreatheDivider))
            TimeStat(value = "${p.badges.count { it.unlocked }}", label = "Badges")
        }
    }
}

@Composable
private fun TimeStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = BreathePrimary)
        Text(label, fontSize = 11.sp, color = BreatheTextMuted)
    }
}

@Composable
private fun LevelPath(currentIndex: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BreatheSurface.copy(alpha = 0.6f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, BreatheDivider)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Achievements.LEVELS.forEachIndexed { i, level ->
                val reached = i <= currentIndex
                val isCurrent = i == currentIndex
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(if (isCurrent) 36.dp else 28.dp)
                                .background(
                                    if (reached) BreathePrimary.copy(alpha = if (isCurrent) 1f else 0.5f)
                                    else BreatheDivider,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(level.emoji, fontSize = if (isCurrent) 18.sp else 14.sp)
                        }
                        if (i < Achievements.LEVELS.lastIndex) {
                            Box(
                                Modifier
                                    .width(2.dp)
                                    .height(28.dp)
                                    .background(if (reached) BreathePrimary.copy(alpha = 0.3f) else BreatheDivider)
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            level.name,
                            fontSize = 15.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (reached) BreatheTextPrimary else BreatheTextMuted
                        )
                        Text(
                            formatMinutes(level.minMinutes),
                            fontSize = 11.sp,
                            color = BreatheTextMuted
                        )
                    }
                    if (isCurrent) {
                        Text(
                            "CURRENT",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = BreatheOnPrimary,
                            modifier = Modifier
                                .background(BreathePrimary, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeGrid(badges: List<MilestoneBadge>) {
    val rows = badges.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { badge ->
                    BadgeCard(badge = badge, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BadgeCard(badge: MilestoneBadge, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BreatheSurface.copy(alpha = if (badge.unlocked) 0.7f else 0.4f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (badge.unlocked) BreathePrimary.copy(alpha = 0.3f) else BreatheDivider)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                badge.emoji,
                fontSize = 32.sp,
                modifier = Modifier.graphicsLayer { if (!badge.unlocked) alpha = 0.3f }
            )
            Text(
                badge.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (badge.unlocked) BreatheTextPrimary else BreatheTextMuted,
                textAlign = TextAlign.Center
            )
            Text(
                badge.description,
                fontSize = 11.sp,
                color = BreatheTextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            )
        }
    }
}

private fun formatMinutes(minutes: Long): String = when {
    minutes <= 0   -> "unlocked"
    minutes < 60   -> "${minutes}m"
    minutes < 1440 -> "${minutes / 60}h"
    else           -> "${minutes / 1440}d"
}
