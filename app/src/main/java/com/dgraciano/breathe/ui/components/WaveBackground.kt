package com.dgraciano.breathe.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import com.dgraciano.breathe.ui.theme.BreatheBackground
import com.dgraciano.breathe.ui.theme.BreathePrimary
import com.dgraciano.breathe.ui.theme.BreatheSecondary
import kotlin.math.sin

@Composable
fun WaveBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            
            // Background gradient
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(BreatheBackground, BreatheBackground.copy(alpha = 0.8f))
                )
            )

            // Draw three layers of waves
            drawWave(width, height, phase, 0.4f, 40f, BreathePrimary.copy(alpha = 0.2f))
            drawWave(width, height, phase + 1f, 0.5f, 30f, BreatheSecondary.copy(alpha = 0.15f))
            drawWave(width, height, phase + 2f, 0.6f, 20f, BreathePrimary.copy(alpha = 0.1f))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWave(
    width: Float,
    height: Float,
    phase: Float,
    baseHeightPercent: Float,
    amplitude: Float,
    color: androidx.compose.ui.graphics.Color
) {
    val wavePath = Path().apply {
        moveTo(0f, height)
        val baseHeight = height * baseHeightPercent
        for (x in 0..width.toInt() step 5) {
            val y = baseHeight + amplitude * sin(x * (2 * Math.PI / width) + phase).toFloat()
            lineTo(x.toFloat(), y)
        }
        lineTo(width, height)
        close()
    }
    drawPath(path = wavePath, color = color)
}
