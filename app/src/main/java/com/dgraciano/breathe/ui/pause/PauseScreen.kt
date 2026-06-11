package com.dgraciano.breathe.ui.pause

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dgraciano.breathe.data.model.Quote
import com.dgraciano.breathe.ui.theme.BreatheBackground
import com.dgraciano.breathe.ui.theme.BreatheCircleInner
import com.dgraciano.breathe.ui.theme.BreatheCircleOuter
import com.dgraciano.breathe.ui.theme.BreathePrimary

@Composable
fun PauseScreen(
    appName: String,
    quote: Quote?,
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathe")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle_scale"
    )

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "breath_phase"
    )

    val breathText = if (phase < 0.5f) "Breathe in…" else "Breathe out…"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BreatheBackground)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        // Breathing circle
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(scale)
                    .background(BreatheCircleOuter, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .scale(scale * 0.85f)
                    .background(BreatheCircleInner, CircleShape)
            )
        }

        Text(
            text = breathText,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.7f)
        )

        // Quote
        if (quote != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "\"${quote.text}\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "— ${quote.author}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }

        // Decision buttons
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onNo,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BreathePrimary)
            ) {
                Text("No, go back", color = Color.White)
            }
            OutlinedButton(
                onClick = onYes,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Yes, open $appName", color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}
