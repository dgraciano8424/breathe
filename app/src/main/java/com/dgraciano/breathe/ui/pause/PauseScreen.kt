package com.dgraciano.breathe.ui.pause


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import com.dgraciano.breathe.data.model.pauseReasons
import com.dgraciano.breathe.data.repository.MentalHealthTip
import com.dgraciano.breathe.ui.components.WaveBackground
import com.dgraciano.breathe.ui.components.rememberReducedMotion
import com.dgraciano.breathe.ui.theme.*


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PauseScreen(
    appName: String,
    attemptCount: Int,
    tip: MentalHealthTip,
    alternativeActivity: String,
    selectedReason: String?,
    personalReminder: String = "",
    secondsLeft: Int,
    sessionId: Int = 0,
    ready: Boolean = true,
    saving: Boolean = false,
    saveError: String? = null,
    onDisplayed: () -> Unit = {},
    onReasonSelected: (String) -> Unit,
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    val showContent = true
    var showReasons by remember(sessionId) { mutableStateOf(false) }
    var showTip by remember(sessionId) { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotion()

    LaunchedEffect(sessionId, ready) { if (ready) onDisplayed() }

    val motion = if (reducedMotion) BreathMotion(0.9f, 0.6f, 0f) else animatedBreathMotion()
    val breathScale = motion.scale
    val breathAlpha = motion.alpha
    val isInhale = if (reducedMotion) (secondsLeft / 4) % 2 == 0 else motion.phase < 0.5f
    val breathLabel = if (isInhale) "Breathe in gently" else "Breathe out slowly"

    Box(
        modifier = Modifier
            .fillMaxSize()
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

                TextButton(onClick = { showTip = !showTip }) {
                    Text(if (showTip) "Hide grounding tip" else "Want a grounding tip?", color = BreatheTextSecondary)
                }
                AnimatedVisibility(
                    visible = showContent && showTip,
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

                if (personalReminder.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = BreatheSurface.copy(alpha = 0.7f))
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Your reminder", color = BreatheSecondary, fontSize = 12.sp)
                            Text(personalReminder, color = BreatheTextPrimary, fontSize = 16.sp)
                        }
                    }
                }
                TextButton(onClick = { showReasons = !showReasons }) {
                    Text(
                        if (showReasons) "Hide intentions" else selectedReason?.let { key ->
                            "Your intention: ${pauseReasons.firstOrNull { it.key == key }?.label.orEmpty()}"
                        } ?: "Add an intention (optional)",
                        color = BreatheTextSecondary
                    )
                }

                // Reason Selector
                AnimatedVisibility(visible = showContent && showReasons, enter = fadeIn(tween(1200))) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "What brings you to $appName? Tap again to clear.",
                            fontSize = 13.sp,
                            color = BreatheTextMuted,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().selectableGroup()
                        ) {
                            pauseReasons.forEach { (key, label) ->
                                val isSelected = selectedReason == key
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) BreathePrimary.copy(alpha = 0.2f) else Color.Transparent)
                                        .border(1.dp, if (isSelected) BreathePrimary else BreatheDivider, RoundedCornerShape(12.dp))
                                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onReasonSelected(key) })
                                        .defaultMinSize(minHeight = 48.dp)
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
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
                    saveError?.let { Text(it, color = BreatheTextPrimary, textAlign = TextAlign.Center) }
                    Button(
                        onClick = onNo,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BreathePrimary, contentColor = BreatheOnPrimary)
                    ) {
                        Text(if (saving) "Saving your choice…" else "Go back", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = onYes,
                        enabled = ready && secondsLeft <= 0 && !saving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (!ready) {
                                "Preparing your pause…"
                            } else if (secondsLeft > 0) {
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
        
    }
}

private fun attemptCountLabel(count: Int): String = when (count) {
    1 -> "A fresh start today"
    2 -> "Your 2nd visit today"
    3 -> "Your 3rd visit today"
    else -> "Visit #$count today"
}

private data class BreathMotion(val scale: Float, val alpha: Float, val phase: Float)
@Composable
private fun animatedBreathMotion(): BreathMotion {
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

    return BreathMotion(animatedBreathScale, animatedBreathAlpha, animatedPhase)
}
