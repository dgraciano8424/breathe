package com.dgraciano.breathe.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary          = BreathePrimary,
    onPrimary        = BreatheOnPrimary,
    secondary        = BreatheSecondary,
    background       = BreatheBackground,
    surface          = OceanSurface.copy(alpha = 0.7f), // Refined for wave visibility
    onBackground     = BreatheTextPrimary,
    onSurface        = BreatheTextPrimary,
    outline          = BreatheDivider,
)

@Composable
fun BreatheTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme
    ) {
        // Transparent scaffolds inherit their content color. Supply the dark
        // background's foreground for unstyled text, including recovery messages.
        CompositionLocalProvider(LocalContentColor provides DarkColorScheme.onBackground) {
            content()
        }
    }
}
