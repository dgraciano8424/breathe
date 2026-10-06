package com.dgraciano.breathe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.dgraciano.breathe.ui.theme.BreatheBackground
import com.dgraciano.breathe.ui.theme.OceanSurface

/** A quiet, static backdrop keeps text contrast stable on every screen. */
@Composable
fun WaveBackground(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BreatheBackground, OceanSurface))))
}
