package com.dgraciano.breathe.ui.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dgraciano.breathe.data.model.Achievements
import com.dgraciano.breathe.data.model.MilestoneBadge
import com.dgraciano.breathe.data.model.UserProgress
import com.dgraciano.breathe.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = hiltViewModel()
) {
    val progress by viewModel.progress.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Achievements", color = BreatheTextPrimary, fontWeight = FontWeight.SemiBold)
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
        if (progress == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // ── Level card ────────────────────────────────────────────────
            item { LevelCard(p) }

            // ── Time saved summary ────────────────────────────────────────
            item { TimeSavedCard(p) }

            // ── Level path ────────────────────────────────────────────────
            item {
                Text(
                    "LEVEL PATH",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = BreathePrimary, letterSpacing = 1.5.sp
                )
            }
            item { LevelPath(currentIndex = p.currentLevel.index) }

            // ── Milestone badges ──────────────────────────────────────────
            item {
                Text(
                    "BADGES",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = BreathePrimary, letterSpacing = 1.5.sp
                )
            }
            item { BadgeGrid(badges = p.badges) }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LevelCard(p: UserProgress) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(BreatheSurface, BreatheSurfaceHigh)),
                RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(p.currentLevel.emoji, fontSize = 52.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                p.currentLevel.name,
                fontSize = 24.sp, fontWeight = FontWeight.Bold, color = BreathePrimary
            )
            Text(
                p.currentLevel.description,
                fontSize = 13.sp, color = BreatheTextSecondary,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp)
            )

            if (p.nextLevel != null) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(p.currentLevel.name, fontSize = 11.sp, color = BreatheTextMuted)
                    Text(p.nextLevel.name, fontSize = 11.sp, color = BreatheTextMuted)
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(BreatheDivider, RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(p.progressToNext)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(listOf(BreathePrimary, BreatheSecondary)),
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
                Spacer(Modifier.height(6.dp))
                val needed = p.nextLevel.minMinutes - p.totalMinutesSaved
                Text(
                    "${formatMinutes(needed)} until ${p.nextLevel.name} ${p.nextLevel.emoji}",
                    fontSize = 11.sp, color = BreatheTextMuted, textAlign = TextAlign.Center
                )
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Maximum level reached. Truly enlightened. 🌌",
                    fontSize = 12.sp, color = BreatheTextSecondary, textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun TimeSavedCard(p: UserProgress) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BreatheSurface, RoundedCornerShape(16.dp))
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        TimeStat(value = p.hoursDisplay, label = "Time saved")
        Box(Modifier.width(1.dp).height(36.dp).background(BreatheDivider))
        TimeStat(value = "${p.lifetimeDeclines}", label = "Total declines")
        Box(Modifier.width(1.dp).height(36.dp).background(BreatheDivider))
        TimeStat(value = "${p.badges.count { it.unlocked }}", label = "Badges")
    }
}

@Composable
private fun TimeStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BreathePrimary)
        Text(label, fontSize = 10.sp, color = BreatheTextMuted)
    }
}

@Composable
private fun LevelPath(currentIndex: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BreatheSurface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Achievements.LEVELS.forEachIndexed { i, level ->
            val reached = i <= currentIndex
            val isCurrent = i == currentIndex
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Dot + connector
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(if (isCurrent) 32.dp else 24.dp)
                            .background(
                                if (reached) BreathePrimary.copy(alpha = if (isCurrent) 1f else 0.5f)
                                else BreatheDivider,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            level.emoji,
                            fontSize = if (isCurrent) 16.sp else 12.sp
                        )
                    }
                    if (i < Achievements.LEVELS.lastIndex) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(24.dp)
                                .background(if (reached) BreathePrimary.copy(alpha = 0.3f) else BreatheDivider)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        level.name,
                        fontSize = 14.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (reached) BreatheTextPrimary else BreatheTextMuted
                    )
                    Text(
                        formatMinutes(level.minMinutes),
                        fontSize = 10.sp,
                        color = BreatheTextMuted
                    )
                }
                if (isCurrent) {
                    Text(
                        "YOU",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = BreatheOnPrimary,
                        modifier = Modifier
                            .background(BreathePrimary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgeGrid(badges: List<MilestoneBadge>) {
    // Non-scrollable grid since we're already inside LazyColumn
    val rows = badges.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
    Column(
        modifier = modifier
            .background(
                if (badge.unlocked) BreatheSurface else BreatheSurface.copy(alpha = 0.5f),
                RoundedCornerShape(14.dp)
            )
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            badge.emoji,
            fontSize = 28.sp,
            color = if (badge.unlocked) Color.Unspecified else Color.Gray
        )
        Text(
            badge.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (badge.unlocked) BreatheTextPrimary else BreatheTextMuted,
            textAlign = TextAlign.Center
        )
        Text(
            badge.description,
            fontSize = 10.sp,
            color = BreatheTextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp
        )
        if (!badge.unlocked) {
            Text(
                "LOCKED",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = BreatheDivider,
                letterSpacing = 1.sp
            )
        }
    }
}

private fun formatMinutes(minutes: Long): String = when {
    minutes <= 0   -> "reached"
    minutes < 60   -> "${minutes}m"
    minutes < 1440 -> "${minutes / 60}h"
    else           -> "${minutes / 1440}d"
}
