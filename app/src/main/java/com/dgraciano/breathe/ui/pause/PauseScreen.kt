package com.dgraciano.breathe.ui.pause

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.dgraciano.breathe.data.model.InterventionEvent
import com.dgraciano.breathe.data.model.Quote
import com.dgraciano.breathe.data.repository.MentalHealthTip
import com.dgraciano.breathe.ui.components.WaveBackground
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
    quote: Quote?,
    tip: MentalHealthTip,
    alternativeActivity: String,
    selectedReason: String?,
    onReasonSelected: (String) -> Unit,
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "breathe")

    val breathScale by transition.animateFloat(
        initialValue = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )

    val breathAlpha by transition.animateFloat(
        initialValue = 0.4f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )

    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "phase"
    )

    val isInhale = phase < 0.5f
    val breathLabel = if (isInhale) "Inhale deep sea air..." else "Exhale the tide..."

    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(800)
        showContent = true
    }

    Box(modifier = Modifier.fillMaxSize().background(BreatheBackground)) {
        WaveBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            AnimatedVisibility(visible = showContent, enter = fadeIn()) {
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
                    // Ripple effect
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
                    // Core
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

            // Mental Health Tip or Quote
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
                        Divider(color = BreatheDivider)
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
                    onClick = onNo,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BreathePrimary, contentColor = BreatheOnPrimary)
                ) {
                    Text("I'll do something else", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = onYes,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue to $appName", color = BreatheTextMuted, fontSize = 13.sp)
                }
            }
        }
    }
}

private fun attemptCountLabel(count: Int): String = when (count) {
    1 -> "A fresh start today"
    2 -> "Your 2nd visit today"
    3 -> "3rd time's a charm?"
    else -> "Visit #$count today"
}
