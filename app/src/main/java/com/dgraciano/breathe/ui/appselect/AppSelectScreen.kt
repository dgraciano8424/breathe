package com.dgraciano.breathe.ui.appselect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectScreen(
    onDone: () -> Unit,
    viewModel: AppSelectViewModel = hiltViewModel()
) {
    val apps by viewModel.apps.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Choose apps to pause") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(apps, key = { it.packageName }) { app ->
                ListItem(
                    headlineContent = { Text(app.appName) },
                    supportingContent = {
                        Text(app.packageName, style = MaterialTheme.typography.labelSmall)
                    },
                    modifier = Modifier.clickable {
                        viewModel.blockApp(app)
                        onDone()
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
