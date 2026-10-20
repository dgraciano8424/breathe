package com.dgraciano.breathe.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Named, font-scale-aware type roles for Quiet Ocean. */
object QuietOceanTypeScale {
    val focal = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    )
    val screenTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    )
    val sectionTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    )
    val body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )
    val supporting = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    val label = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    )
}

/** Material mappings use only the approved four sizes, including a 14sp label floor. */
val QuietOceanTypography = Typography(
    displayLarge = QuietOceanTypeScale.focal,
    displayMedium = QuietOceanTypeScale.focal,
    displaySmall = QuietOceanTypeScale.focal,
    headlineLarge = QuietOceanTypeScale.screenTitle,
    headlineMedium = QuietOceanTypeScale.screenTitle,
    headlineSmall = QuietOceanTypeScale.screenTitle,
    titleLarge = QuietOceanTypeScale.sectionTitle,
    titleMedium = QuietOceanTypeScale.sectionTitle,
    titleSmall = QuietOceanTypeScale.label,
    bodyLarge = QuietOceanTypeScale.body,
    bodyMedium = QuietOceanTypeScale.supporting,
    bodySmall = QuietOceanTypeScale.supporting,
    labelLarge = QuietOceanTypeScale.label,
    labelMedium = QuietOceanTypeScale.label,
    labelSmall = QuietOceanTypeScale.label,
)
