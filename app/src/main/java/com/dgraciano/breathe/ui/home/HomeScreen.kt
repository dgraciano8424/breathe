package com.dgraciano.breathe.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dgraciano.breathe.data.model.BlockedApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddApp: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val apps by viewModel.blockedApps.collectAsState()

    LaunchedEffect(Unit) { viewModel.startService() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Breathe") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddApp) {
                Icon(Icons.Default.Add, contentDescription = "Add app")
            }
        }
    ) { padding ->
        if (apps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tap + to add apps you want to\npause before opening.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    BlockedAppRow(app = app, onRemove = { viewModel.removeApp(app) })
                }
            }
        }
    }
}

@Composable
private fun BlockedAppRow(app: BlockedApp, onRemove: () -> Unit) {
    ListItem(
        headlineContent = { Text(app.appName) },
        supportingContent = {
            Text(app.packageName, style = MaterialTheme.typography.labelSmall)
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove")
            }
        }
    )
    HorizontalDivider()
}
