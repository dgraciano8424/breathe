package com.dgraciano.breathe.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dgraciano.breathe.ui.theme.BreathePrimary
import com.dgraciano.breathe.ui.theme.BreatheSecondary
import com.dgraciano.breathe.ui.theme.BreatheTextMuted
import com.dgraciano.breathe.ui.theme.BreatheTextPrimary
import kotlin.math.sin

/** Strength at which Nimbus starts trailing wind streaks. */
private const val WIND_UNLOCK_STRENGTH = 3

/**
 * Nimbus, the cloud companion. Grows with the user's level and, once they've built
 * up some momentum, starts carrying wind with them.
 *
 * @param strength 1-based level strength (level index + 1).
 * @param label optional name shown under the cloud; hidden when null.
 */
@Composable
fun NimbusBuddy(
    strength: Int = 1,
    label: String? = "Nimbus",
    caption: String? = null,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "nimbus")

    val animatedPulse by transition.animateFloat(
        initialValue = 0.96f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(3400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )
    val animatedDrift by transition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "drift"
    )
    val animatedWindPhase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "wind"
    )
    val animatedShimmer by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "shimmer"
    )

    // Held at a pleasant mid-point when the user has asked the system for no motion.
    // Nimbus still renders in full, it just stops moving.
    val pulse = if (reducedMotion) 1f else animatedPulse
    val drift = if (reducedMotion) 0f else animatedDrift
    val windPhase = if (reducedMotion) 0.5f else animatedWindPhase
    val shimmer = if (reducedMotion) 0.35f else animatedShimmer

    // Deliberately compact at low levels so it reads as a companion, not a centrepiece,
    // then grows as the user progresses.
    val cloudWidth: Dp = (58 + (strength - 1) * 7).coerceAtMost(120).dp
    val boxWidth = cloudWidth * 1.9f
    val boxHeight = cloudWidth * 0.95f
    // Ramps 0f -> 1f over the five levels following the unlock.
    val windStrength = ((strength - WIND_UNLOCK_STRENGTH + 1).coerceAtLeast(0) / 5f)
        .coerceIn(0f, 1f)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Canvas(
            modifier = Modifier
                .width(boxWidth)
                .height(boxHeight)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f + drift * (size.height * 0.05f)
            val r = cloudWidth.toPx() / 2f * pulse

            if (windStrength > 0f) {
                drawWind(cx, cy, r, windPhase, windStrength)
            }
            drawGlow(cx, cy, r, strength)
            drawCloud(cx, cy, r, shimmer)
        }

        if (label != null) {
            Spacer(Modifier.height(6.dp))
            Text(label, color = BreatheTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        if (caption != null) {
            Text(caption, color = BreatheTextMuted, fontSize = 11.sp)
        }
    }
}

/** Soft aura behind the cloud; intensifies with level. */
private fun DrawScope.drawGlow(cx: Float, cy: Float, r: Float, strength: Int) {
    val glowRadius = r * (1.9f + strength * 0.05f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                BreathePrimary.copy(alpha = 0.20f + (strength * 0.012f).coerceAtMost(0.14f)),
                BreathePrimary.copy(alpha = 0.06f),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = glowRadius
        ),
        radius = glowRadius,
        center = Offset(cx, cy)
    )
}

/**
 * Cloud silhouette built from overlapping puffs with a soft vertical gradient,
 * so it reads as a rounded cumulus rather than a flat circle.
 */
private fun DrawScope.drawCloud(cx: Float, cy: Float, r: Float, shimmer: Float) {
    // (offsetX, offsetY, radius) relative to r — overlapping puffs form the silhouette.
    val puffs = listOf(
        Triple(-0.72f, 0.16f, 0.50f),
        Triple(-0.30f, -0.20f, 0.66f),
        Triple(0.24f, -0.26f, 0.58f),
        Triple(0.70f, 0.12f, 0.46f),
        Triple(0.00f, 0.24f, 0.62f)
    )

    val body = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.92f),
            BreatheSecondary.copy(alpha = 0.72f),
            BreathePrimary.copy(alpha = 0.46f)
        ),
        startY = cy - r,
        endY = cy + r
    )

    puffs.forEach { (dx, dy, pr) ->
        drawCircle(brush = body, radius = r * pr, center = Offset(cx + r * dx, cy + r * dy))
    }

    // Travelling highlight so the surface feels alive rather than static.
    val hx = cx + r * (-0.5f + shimmer * 1.0f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.34f), Color.Transparent),
            center = Offset(hx, cy - r * 0.28f),
            radius = r * 0.5f
        ),
        radius = r * 0.5f,
        center = Offset(hx, cy - r * 0.28f)
    )
}

/** Wind streaks trailing the cloud, unlocked once the user has real momentum. */
private fun DrawScope.drawWind(cx: Float, cy: Float, r: Float, phase: Float, intensity: Float) {
    // (verticalOffset, lengthFactor, speedOffset) per streak.
    val streaks = listOf(
        Triple(-0.46f, 1.15f, 0.00f),
        Triple(-0.10f, 1.55f, 0.35f),
        Triple(0.28f, 1.30f, 0.62f),
        Triple(0.56f, 0.90f, 0.18f)
    )

    streaks.forEach { (dy, lengthFactor, speedOffset) ->
        val t = (phase + speedOffset) % 1f
        // Streaks sweep outward from behind the cloud and fade at both ends.
        val travel = r * (0.9f + t * 2.1f)
        val fade = sin(t * Math.PI).toFloat()
        val alpha = 0.30f * fade * intensity
        if (alpha <= 0.01f) return@forEach

        val length = r * lengthFactor * (0.45f + fade * 0.55f)
        val y = cy + r * dy + sin((t + dy) * 6.28f) * r * 0.05f
        val startX = cx - travel
        val thickness = r * 0.055f

        drawRoundRectStreak(startX, y, length, thickness, alpha)
        // Mirrored streak on the leading side keeps the motion balanced.
        drawRoundRectStreak(cx + travel - length, y, length, thickness, alpha * 0.7f)
    }
}

private fun DrawScope.drawRoundRectStreak(
    x: Float,
    y: Float,
    length: Float,
    thickness: Float,
    alpha: Float
) {
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                BreatheSecondary.copy(alpha = alpha),
                Color.Transparent
            ),
            startX = x,
            endX = x + length
        ),
        topLeft = Offset(x, y - thickness / 2f),
        size = Size(length, thickness),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(thickness / 2f)
    )
}
