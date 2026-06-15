package com.dgraciano.breathe.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dgraciano.breathe.ui.theme.*
import androidx.compose.animation.core.*

@Composable
fun OnboardingScreen(
    onPermissionsGranted: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val hasUsage by viewModel.hasUsagePermission.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshPermissionState() }
    LaunchedEffect(hasUsage) { if (hasUsage) onPermissionsGranted() }

    val transition = rememberInfiniteTransition(label = "onboard")
    val pulse by transition.animateFloat(
        initialValue = 0.8f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BreatheBackground, BreatheSurface)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Breathing orb
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(pulse)
                        .background(BreatheRingOuter, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(pulse * 0.95f)
                        .background(BreatheRingMid, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Brush.radialGradient(listOf(BreathePrimary.copy(alpha = 0.9f), BreatheSecondary.copy(alpha = 0.6f))),
                            CircleShape
                        )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Breathe",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = BreatheTextPrimary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "A mindful pause before you open\ndistracting apps.",
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = BreatheTextSecondary
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Permission card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BreatheSurface, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Text(
                    "Usage Access",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = BreatheTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Lets Breathe see which app you just opened — required to show the pause screen.",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = BreatheTextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    enabled = !hasUsage,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BreathePrimary,
                        contentColor = BreatheOnPrimary,
                        disabledContainerColor = BreatheSecondary.copy(alpha = 0.3f),
                        disabledContentColor = BreatheTextSecondary
                    )
                ) {
                    Text(
                        if (hasUsage) "Granted ✓" else "Grant Access",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.refreshPermissionState() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BreathePrimary,
                    contentColor = BreatheOnPrimary
                )
            ) {
                Text("I've granted access — continue", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
