package com.dgraciano.breathe.ui.appselect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dgraciano.breathe.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectScreen(
    onDone: () -> Unit,
    viewModel: AppSelectViewModel = hiltViewModel()
) {
    val apps by viewModel.apps.collectAsState()
    val isLoading = apps.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Choose apps to pause",
                        color = BreatheTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = BreatheTextSecondary
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onDone) {
                        Text(
                            "Done",
                            color = BreathePrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BreatheBackground)
            )
        },
        containerColor = BreatheBackground
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BreathePrimary)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Text(
                    text = "TAP TO BLOCK",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BreathePrimary,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
            items(apps, key = { it.packageName }) { app ->
                ListItem(
                    headlineContent = {
                        Text(app.appName, color = BreatheTextPrimary, fontSize = 15.sp)
                    },
                    supportingContent = {
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = BreatheTextMuted
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = BreathePrimary.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier.clickable { viewModel.blockApp(app) },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    )
                )
                HorizontalDivider(color = BreatheDivider, thickness = 0.5.dp)
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
