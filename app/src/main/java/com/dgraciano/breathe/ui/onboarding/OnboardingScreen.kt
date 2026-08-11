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
    val hasAccessibility by viewModel.hasAccessibility.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showDisclosure by remember { mutableStateOf(false) }

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

    // Interception needs accessibility plus the overlay. Usage access is optional now —
    // it only enriches the stats screens.
    LaunchedEffect(hasAccessibility, hasOverlay) {
        if (hasAccessibility && hasOverlay) {
            onPermissionsGranted()
        }
    }

    if (showDisclosure) {
        AccessibilityDisclosureDialog(
            onDismiss = { showDisclosure = false },
            onAgree = {
                showDisclosure = false
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )
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

            // Card titles match the labels shown in system Settings. "Ocean Brush
            // Overlay" gave no clue it meant "Display over other apps", which made the
            // hand-off to Settings a dead end.
            PermissionCard(
                title = "Accessibility access",
                description = "Lets Breathe notice the moment you open an app you've chosen to pause.",
                isGranted = hasAccessibility,
                onClick = { showDisclosure = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            PermissionCard(
                title = "Display over other apps",
                description = "Lets the pause appear on top of the app you're opening.",
                isGranted = hasOverlay,
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            PermissionCard(
                title = "Usage access (optional)",
                description = "Adds how long you've spent in each app to your stats.",
                isGranted = hasUsage,
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (hasAccessibility && hasOverlay) {
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

/**
 * Prominent disclosure, shown before the user is sent to accessibility settings.
 *
 * Google Play requires this in-app (not only in the store listing) and requires an
 * affirmative action to consent — hence a dialog with an explicit agree button rather
 * than passive copy on the card.
 */
@Composable
private fun AccessibilityDisclosureDialog(
    onDismiss: () -> Unit,
    onAgree: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BreatheSurface,
        title = {
            Text(
                "How Breathe uses accessibility access",
                color = BreatheTextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Breathe uses Android's accessibility service to detect which app has " +
                        "just come to the front. That is the only way to show your pause " +
                        "before the app opens.",
                    color = BreatheTextSecondary,
                    fontSize = 14.sp
                )
                Text(
                    "It reads only the name of the app being opened. It does not read the " +
                        "contents of your screen, your messages, or anything you type, and " +
                        "it never performs actions on your behalf.",
                    color = BreatheTextSecondary,
                    fontSize = 14.sp
                )
                Text(
                    "This information stays on your device. It is not collected, shared, " +
                        "or sent anywhere.",
                    color = BreatheTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAgree) {
                Text("I understand — open settings", color = BreathePrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now", color = BreatheTextSecondary)
            }
        }
    )
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
