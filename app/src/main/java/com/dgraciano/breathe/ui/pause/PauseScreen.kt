package com.dgraciano.breathe.ui.pause

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dgraciano.breathe.data.model.InterventionEvent
import com.dgraciano.breathe.data.repository.MentalHealthTip
import com.dgraciano.breathe.ui.components.ConfettiOverlay
import com.dgraciano.breathe.ui.components.WaveBackground
import com.dgraciano.breathe.ui.components.rememberReducedMotion
import com.dgraciano.breathe.ui.theme.*
import kotlinx.coroutines.delay

private val reasons = listOf(
    InterventionEvent.REASON_BORED to "Bored",
    InterventionEvent.REASON_HABIT to "Habit",
    InterventionEvent.REASON_ESCAPING to "Escaping",
    InterventionEvent.REASON_CURIOUS to "Curious"
)

@Composable
fun PauseScreen(
    appName: String,
    attemptCount: Int,
    tip: MentalHealthTip,
    alternativeActivity: String,
    selectedReason: String?,
    pauseSeconds: Int,
    onReasonSelected: (String) -> Unit,
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    var showContent by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotion()

    // The whole point of the pause: the way out of the app stays shut until the user
    // has actually sat with the breathing for as long as they configured.
    var secondsLeft by remember(pauseSeconds) { mutableIntStateOf(pauseSeconds) }
    LaunchedEffect(pauseSeconds) {
        secondsLeft = pauseSeconds
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        showContent = true
    }

    LaunchedEffect(showConfetti) {
        if (showConfetti) {
            delay(850)
            onNo()
        }
    }

    val brushOffset by animateFloatAsState(
        targetValue = if (showContent) 0f else 1f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "brush"
    )

    val transition = rememberInfiniteTransition(label = "breathe")

    val animatedBreathScale by transition.animateFloat(
        initialValue = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )

    val animatedBreathAlpha by transition.animateFloat(
        initialValue = 0.4f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )

    val animatedPhase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "phase"
    )

    // With motion off the rings hold still, and the breath cue is driven by the
    // countdown instead so the guidance still alternates.
    val breathScale = if (reducedMotion) 0.9f else animatedBreathScale
    val breathAlpha = if (reducedMotion) 0.6f else animatedBreathAlpha
    val isInhale = if (reducedMotion) (secondsLeft / 4) % 2 == 0 else animatedPhase < 0.5f
    val breathLabel = if (isInhale) "Inhale deep sea air..." else "Exhale the tide..."

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = -brushOffset * size.width
                alpha = 1f - (brushOffset * 0.5f)
            }
    ) {
        Box(modifier = Modifier.fillMaxSize().background(BreatheBackground)) {
            WaveBackground(modifier = Modifier.fillMaxSize())

            // Scrolls rather than clipping on short screens, in landscape, and at large
            // font scales, where the fixed-height layout used to push the actions off.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
            ) {
                // Header
                AnimatedVisibility(visible = showContent, enter = fadeIn(tween(800))) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = appName.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BreatheSecondary,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = attemptCountLabel(attemptCount),
                            fontSize = 14.sp,
                            color = BreatheTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Breathing Circle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .scale(breathScale * 1.2f)
                                .background(BreatheRingOuter, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .scale(breathScale * 1.1f)
                                .background(BreatheRingMid, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .scale(breathScale)
                                .background(BreatheRingInner, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(BreathePrimary.copy(alpha = breathAlpha), Color.Transparent)
                                    )
                                )
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = breathLabel,
                        fontSize = 16.sp,
                        color = BreatheTextPrimary,
                        fontWeight = FontWeight.Medium,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }

                // Mental Health Tip
                AnimatedVisibility(
                    visible = showContent,
                    enter = fadeIn(tween(1000)) + scaleIn(initialScale = 0.9f)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = BreatheSurface.copy(alpha = 0.7f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BreatheDivider)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = BreathePrimary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(tip.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BreathePrimary)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                tip.description,
                                fontSize = 15.sp,
                                color = BreatheTextPrimary,
                                lineHeight = 22.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = BreatheDivider)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Alternative: $alternativeActivity",
                                fontSize = 13.sp,
                                color = BreatheSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Reason Selector
                AnimatedVisibility(visible = showContent, enter = fadeIn(tween(1200))) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Why reach for $appName?",
                            fontSize = 13.sp,
                            color = BreatheTextMuted,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            reasons.forEach { (key, label) ->
                                val isSelected = selectedReason == key
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) BreathePrimary.copy(alpha = 0.2f) else Color.Transparent)
                                        .border(1.dp, if (isSelected) BreathePrimary else BreatheDivider, RoundedCornerShape(12.dp))
                                        .clickable { onReasonSelected(key) }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        color = if (isSelected) BreathePrimary else BreatheTextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // Actions
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showConfetti = true },
                        enabled = !showConfetti,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BreathePrimary, contentColor = BreatheOnPrimary)
                    ) {
                        Text("I'll do something else", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = onYes,
                        enabled = secondsLeft <= 0 && !showConfetti,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (secondsLeft > 0) {
                                "Continue to $appName in ${secondsLeft}s"
                            } else {
                                "Continue to $appName"
                            },
                            color = if (secondsLeft > 0) BreatheTextMuted.copy(alpha = 0.5f)
                            else BreatheTextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
        
        if (showConfetti) {
            ConfettiOverlay(modifier = Modifier.fillMaxSize())
        }

        // Final "Wave Brush" that clears the screen
        if (!showContent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color.Transparent, BreathePrimary.copy(alpha = 0.3f), BreatheBackground),
                        )
                    )
            )
        }
    }
}

private fun attemptCountLabel(count: Int): String = when (count) {
    1 -> "A fresh start today"
    2 -> "Your 2nd visit today"
    3 -> "3rd time's a charm?"
    else -> "Visit #$count today"
}
