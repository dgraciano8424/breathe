package com.dgraciano.breathe.ui.pause

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.dgraciano.breathe.data.repository.MentalHealthTip
import com.dgraciano.breathe.ui.theme.BreatheBackground
import android.os.SystemClock
import com.dgraciano.breathe.domain.PausePolicy
import kotlinx.coroutines.delay

/** Uses the real pause UI without granting approval, launching an app, or recording events. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticePauseScreen(onBack: () -> Unit) {
    val policy = remember { PausePolicy(SystemClock::elapsedRealtime).apply { start(8) } }
    var secondsLeft by remember { mutableIntStateOf(8) }
    LaunchedEffect(policy) {
        while (secondsLeft > 0) {
            delay(100)
            secondsLeft = policy.remainingSeconds()
        }
    }
    var selectedReason by rememberSaveable { mutableStateOf<String?>(null) }
    var outcome by rememberSaveable { mutableStateOf<String?>(null) }
    var leaving by remember { mutableStateOf(false) }
    // Navigation keeps the outgoing screen composed during its transition. Close the
    // dialog first and accept only one exit, so a second tap cannot pop its parent too.
    val finishPractice = {
        if (!leaving) {
            leaving = true
            outcome = null
            onBack()
        }
    }

    Scaffold(
        containerColor = BreatheBackground,
        topBar = {
            TopAppBar(
                title = { Text("Practice pause") },
                navigationIcon = {
                    IconButton(onClick = finishPractice) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BreatheBackground)
            )
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            PauseScreen(
                appName = "your chosen app",
                attemptCount = 1,
                tip = MentalHealthTip(
                    "A moment to choose",
                    "Feel your feet on the floor. Take a slow breath. What would you like to do next?",
                    "ground"
                ),
                alternativeActivity = "Look away from your phone and notice your surroundings",
                selectedReason = selectedReason,
                secondsLeft = secondsLeft,
                onReasonSelected = { selectedReason = it },
                onYes = { outcome = "You chose to continue intentionally." },
                onNo = { outcome = "You made room for something else." }
            )
        }
    }

    outcome?.let { message ->
        AlertDialog(
            onDismissRequest = finishPractice,
            title = { Text("That's the pause") },
            text = {
                Text("$message Both choices are yours to make. This practice didn't open another app or change your stats.")
            },
            confirmButton = {
                TextButton(onClick = finishPractice) { Text("Done") }
            }
        )
    }
}
