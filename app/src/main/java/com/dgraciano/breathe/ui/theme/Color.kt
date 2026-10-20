package com.dgraciano.breathe.ui.theme

import androidx.compose.ui.graphics.Color

/** Exact, opaque semantic colors for the Quiet Ocean visual system. */
object QuietOceanColors {
    val canvas = Color(0xFF012A36)
    val surface = Color(0xFF0B3A48)
    val surfaceRaised = Color(0xFF124E5E)
    val surfaceQuiet = Color(0xFF08313E)
    val actionPrimary = Color(0xFF80CBC4)
    val onActionPrimary = Color(0xFF012A36)
    val textPrimary = Color(0xFFF1FAFB)
    val textSupporting = Color(0xFFC2E1E4)
    val textMutedAccessible = Color(0xFF9EC9CE)
    val statusHealthy = Color(0xFF70D6C7)
    val statusAttention = Color(0xFFFFD166)
    val statusError = Color(0xFFFF8A80)
    val selection = Color(0xFF4DB6AC)
    val focusRing = Color(0xFFB2EBF2)
}

// Compatibility aliases remain until Tasks 11–15 migrate each screen to semantic roles.
val OceanDeep = QuietOceanColors.canvas
val OceanSurface = QuietOceanColors.surface
val OceanMisty = QuietOceanColors.surfaceRaised
val OceanFoam = QuietOceanColors.textPrimary

val BreathePrimary = QuietOceanColors.actionPrimary
val BreatheSecondary = QuietOceanColors.selection
val BreatheOnPrimary = QuietOceanColors.onActionPrimary

val BreatheBackground = QuietOceanColors.canvas
val BreatheSurface = QuietOceanColors.surface
val BreatheSurfaceHigh = QuietOceanColors.surfaceRaised
val BreatheDivider = QuietOceanColors.focusRing.copy(alpha = 0.2f)

val BreatheTextPrimary = QuietOceanColors.textPrimary
val BreatheTextSecondary = QuietOceanColors.textSupporting
val BreatheTextMuted = QuietOceanColors.textMutedAccessible

val BreatheSand = QuietOceanColors.statusAttention

val BreatheRingOuter = QuietOceanColors.selection.copy(alpha = 0.1f)
val BreatheRingMid = QuietOceanColors.selection.copy(alpha = 0.2f)
val BreatheRingInner = QuietOceanColors.selection.copy(alpha = 0.5f)
