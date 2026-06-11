package com.dgraciano.breathe.ui.pause

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dgraciano.breathe.data.model.InterventionEvent
import com.dgraciano.breathe.data.model.Quote
import kotlinx.coroutines.delay

private val BgTop = Color(0xFF0D0D1A)
private val BgBottom = Color(0xFF1A1633)
private val GlowPrimary = Color(0xFF6C63FF)
private val GlowSecondary = Color(0xFF9C94FF)
private val RingOuter = Color(0x1A6C63FF)
private val RingMid = Color(0x336C63FF)
private val RingInner = Color(0x806C63FF)
private val RingCore = Color(0xCC6C63FF)
private val TextPrimary = Color(0xFFF0EEFF)
private val TextSecondary = Color(0x99C4BFFF)
private val ChipSelected = Color(0x336C63FF)
private val ChipBorder = Color(0x806C63FF)

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
    selectedReason: String?,
    onReasonSelected: (String) -> Unit,
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "breathe")

    val breathScale by transition.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )

    val midScale by transition.animateFloat(
        initialValue = 0.6f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "midScale"
    )

    val coreScale by transition.animateFloat(
        initialValue = 0.65f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "coreScale"
    )

    val breathAlpha by transition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
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
    val breathLabel = if (isInhale) "Breathe in..." else "Breathe out..."

    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(600)
        showContent = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Top: attempt counter ──────────────────────────────────────
            AnimatedVisibility(visible = showContent, enter = fadeIn(tween(600))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = appName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = attemptCountLabel(attemptCount),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ── Center: breathing circles + label ────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
                    // Outermost glow ring
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .scale(breathScale)
                            .background(RingOuter, CircleShape)
                    )
                    // Mid ring
                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .scale(midScale)
                            .background(RingMid, CircleShape)
                    )
                    // Inner ring
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(coreScale)
                            .background(RingInner, CircleShape)
                    )
                    // Core glow
                    Box(
                        modifier = Modifier
                            .size(75.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        GlowSecondary.copy(alpha = breathAlpha),
                                        GlowPrimary.copy(alpha = breathAlpha * 0.7f)
                                    )
                                ),
                                CircleShape
                            )
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = breathLabel,
                    fontSize = 14.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            }

            // ── Quote ─────────────────────────────────────────────────────
            AnimatedVisibility(visible = showContent && quote != null, enter = fadeIn(tween(800))) {
                quote?.let {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "“${it.text}”",
                            fontSize = 15.sp,
                            color = TextPrimary,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "— ${it.author}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // ── Reason chips ──────────────────────────────────────────────
            AnimatedVisibility(visible = showContent, enter = fadeIn(tween(1000))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Why are you opening this?",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        reasons.forEach { (key, label) ->
                            val isSelected = selectedReason == key
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSelected) ChipSelected else Color.Transparent,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) GlowPrimary else ChipBorder,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .clickable { onReasonSelected(key) }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    color = if (isSelected) GlowSecondary else TextSecondary,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // ── Action buttons ────────────────────────────────────────────
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onNo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GlowPrimary)
                ) {
                    Text(
                        "No, go back",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                OutlinedButton(
                    onClick = onYes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x40FFFFFF))
                ) {
                    Text(
                        "Yes, open $appName",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

private fun attemptCountLabel(count: Int): String = when (count) {
    1 -> "First time today"
    2 -> "2nd time today"
    3 -> "3rd time today"
    else -> "${count}th time today"
}
