package com.dgraciano.breathe.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dgraciano.breathe.data.repository.SettingsRepository
import com.dgraciano.breathe.ui.components.WaveBackground
import com.dgraciano.breathe.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val pauseSeconds by viewModel.pauseSeconds.collectAsState()
    val isMonitoringActive by viewModel.isMonitoringActive.collectAsState()
    val historyCleared by viewModel.historyCleared.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshMonitoringState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(historyCleared) {
        if (historyCleared) {
            snackbarHost.showSnackbar("History cleared")
            viewModel.acknowledgeHistoryCleared()
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = BreatheSurface,
            title = { Text("Clear your history?", color = BreatheTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This permanently deletes every recorded pause, along with your stats " +
                        "and streak. Your chosen apps stay put.",
                    color = BreatheTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearHistory()
                }) { Text("Clear", color = BreatheWarning, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Keep it", color = BreatheTextSecondary)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(BreatheBackground)) {
        WaveBackground(modifier = Modifier.fillMaxSize())

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHost) },
            topBar = {
                TopAppBar(
                    title = { Text("Settings", fontWeight = FontWeight.Bold, color = BreatheTextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = BreatheTextSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                SectionLabel("Monitoring")

                SettingRow(
                    title = if (isMonitoringActive) "Breathe is running" else "Breathe is paused",
                    subtitle = if (isMonitoringActive) {
                        "Turn it off in Android's accessibility settings to stop all pauses."
                    } else {
                        "No pauses will appear until you turn accessibility access back on."
                    },
                    actionLabel = if (isMonitoringActive) "Turn off" else "Turn on",
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                )

                SectionLabel("Pause length")

                Text(
                    "How long you sit with the breath before you can continue.",
                    color = BreatheTextSecondary,
                    fontSize = 13.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SettingsRepository.PAUSE_OPTIONS.forEach { seconds ->
                        val selected = seconds == pauseSeconds
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) BreathePrimary.copy(alpha = 0.2f) else Color.Transparent
                                )
                                .border(
                                    1.dp,
                                    if (selected) BreathePrimary else BreatheDivider,
                                    RoundedCornerShape(12.dp)
                                )
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { viewModel.setPauseSeconds(seconds) }
                                )
                        ) {
                            Text(
                                "${seconds}s",
                                color = if (selected) BreathePrimary else BreatheTextSecondary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                SectionLabel("Your data")

                SettingRow(
                    title = "Clear history",
                    subtitle = "Deletes every recorded pause, your stats and your streak. Nothing was ever sent off your device.",
                    actionLabel = "Clear",
                    onClick = { confirmClear = true }
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = BreatheSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BreatheSurface.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = BreatheTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = BreatheTextSecondary, fontSize = 13.sp)
        }
        Text(actionLabel, color = BreathePrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
