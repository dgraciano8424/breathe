package com.dgraciano.breathe.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dgraciano.breathe.ui.theme.BreathePrimary
import com.dgraciano.breathe.ui.theme.BreatheTextPrimary

@Composable
fun NimbusBuddy(strength: Int = 1) {
    val transition = rememberInfiniteTransition(label = "nimbus")
    val pulse by transition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )
    val drift by transition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "drift"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(100.dp)
                .offset(y = (drift * 6).dp)
                .scale(pulse)
        ) {
            // Wispy Cloud Body
            Box(Modifier.size(80.dp).background(Color.White.copy(alpha = 0.3f), CircleShape).blur(8.dp))
            // Inner Core (Strength Glow)
            Box(
                Modifier
                    .size((50 + (strength * 5)).dp)
                    .background(BreathePrimary.copy(alpha = 0.2f), CircleShape)
                    .blur(4.dp)
            )
        }
        Text("Nimbus", color = BreatheTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Strength Lvl $strength", color = BreathePrimary, fontSize = 12.sp)
    }
}