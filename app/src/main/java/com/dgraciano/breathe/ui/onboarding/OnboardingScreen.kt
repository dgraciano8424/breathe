package com.dgraciano.breathe.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dgraciano.breathe.ui.components.WaveBackground
import com.dgraciano.breathe.ui.theme.*

@Composable
fun OnboardingScreen(
    onPermissionsGranted: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val hasUsage by viewModel.hasUsagePermission.collectAsState()
    val hasOverlay by viewModel.hasOverlayPermission.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(hasUsage, hasOverlay) {
        if (hasUsage && hasOverlay) {
            onPermissionsGranted()
        }
    }

    val transition = rememberInfiniteTransition(label = "onboard")
    val pulse by transition.animateFloat(
        initialValue = 0.8f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Box(modifier = Modifier.fillMaxSize().background(BreatheBackground)) {
        WaveBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Breathing orb
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(pulse)
                        .background(BreatheRingOuter, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.radialGradient(
                                listOf(BreathePrimary.copy(alpha = 0.9f), BreatheSecondary.copy(alpha = 0.6f))
                            ),
                            CircleShape
                        )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Digital Sanctuary",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = BreatheTextPrimary
            )
            Text(
                text = "Let's set up your mindful space.",
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                color = BreatheTextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Permission 1: Usage Stats
            PermissionCard(
                title = "Digital Awareness",
                description = "Lets Breathe notice when you open distracting apps.",
                isGranted = hasUsage,
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Permission 2: Overlay
            PermissionCard(
                title = "Ocean Brush Overlay",
                description = "Required to show the mindful pause over other apps.",
                isGranted = hasOverlay,
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (hasUsage && hasOverlay) {
                Button(
                    onClick = { onPermissionsGranted() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BreatheSecondary)
                ) {
                    Text("Enter the Sanctuary", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BreatheSurface.copy(alpha = 0.7f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isGranted) BreathePrimary.copy(alpha = 0.5f) else BreatheDivider)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = if (isGranted) BreathePrimary else BreatheTextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, fontSize = 12.sp, color = BreatheTextSecondary, lineHeight = 16.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = onClick,
                enabled = !isGranted,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BreathePrimary,
                    disabledContainerColor = BreatheDivider
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(if (isGranted) "OK ✓" else "Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
