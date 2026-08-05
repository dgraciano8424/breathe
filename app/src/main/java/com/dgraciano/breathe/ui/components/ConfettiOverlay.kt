package com.dgraciano.breathe.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.dgraciano.breathe.ui.theme.BreathePrimary
import com.dgraciano.breathe.ui.theme.BreatheSand
import com.dgraciano.breathe.ui.theme.BreatheSecondary
import com.dgraciano.breathe.ui.theme.OceanFoam
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiPiece(
    val startXFraction: Float,
    val delay: Float,
    val driftAmplitude: Float,
    val driftPhase: Float,
    val rotationSpeed: Float,
    val sizeDp: Float,
    val color: Color
)

@Composable
fun ConfettiOverlay(modifier: Modifier = Modifier, pieceCount: Int = 60) {
    val colors = remember { listOf(BreathePrimary, BreatheSecondary, BreatheSand, OceanFoam) }
    val pieces = remember {
        List(pieceCount) {
            ConfettiPiece(
                startXFraction = Random.nextFloat(),
                delay = Random.nextFloat() * 0.25f,
                driftAmplitude = 20f + Random.nextFloat() * 40f,
                driftPhase = Random.nextFloat() * 6.28f,
                rotationSpeed = 180f + Random.nextFloat() * 360f,
                sizeDp = 6f + Random.nextFloat() * 8f,
                color = colors[Random.nextInt(colors.size)]
            )
        }
    }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(1000, easing = LinearEasing))
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        pieces.forEach { piece ->
            val local = ((progress.value - piece.delay) / (1f - piece.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val y = -40f + local * (height + 80f)
            val x = piece.startXFraction * width + sin(local * 6.28f + piece.driftPhase) * piece.driftAmplitude
            val alpha = if (local > 0.75f) (1f - (local - 0.75f) / 0.25f).coerceIn(0f, 1f) else 1f
            val sizePx = piece.sizeDp.dp.toPx()
            rotate(degrees = piece.rotationSpeed * local, pivot = Offset(x, y)) {
                drawRect(
                    color = piece.color.copy(alpha = alpha),
                    topLeft = Offset(x - sizePx / 2f, y - sizePx / 4f),
                    size = Size(sizePx, sizePx / 2f)
                )
            }
        }
    }
}
