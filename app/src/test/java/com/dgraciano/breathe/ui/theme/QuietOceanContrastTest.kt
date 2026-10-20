package com.dgraciano.breathe.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class QuietOceanContrastTest {
    @Test
    fun `semantic colors match the approved opaque argb values`() {
        val expected = mapOf(
            "canvas" to Color(0xFF012A36),
            "surface" to Color(0xFF0B3A48),
            "surfaceRaised" to Color(0xFF124E5E),
            "surfaceQuiet" to Color(0xFF08313E),
            "actionPrimary" to Color(0xFF80CBC4),
            "onActionPrimary" to Color(0xFF012A36),
            "textPrimary" to Color(0xFFF1FAFB),
            "textSupporting" to Color(0xFFC2E1E4),
            "textMutedAccessible" to Color(0xFF9EC9CE),
            "statusHealthy" to Color(0xFF70D6C7),
            "statusAttention" to Color(0xFFFFD166),
            "statusError" to Color(0xFFFF8A80),
            "selection" to Color(0xFF4DB6AC),
            "focusRing" to Color(0xFFB2EBF2),
        )
        val actual = mapOf(
            "canvas" to QuietOceanColors.canvas,
            "surface" to QuietOceanColors.surface,
            "surfaceRaised" to QuietOceanColors.surfaceRaised,
            "surfaceQuiet" to QuietOceanColors.surfaceQuiet,
            "actionPrimary" to QuietOceanColors.actionPrimary,
            "onActionPrimary" to QuietOceanColors.onActionPrimary,
            "textPrimary" to QuietOceanColors.textPrimary,
            "textSupporting" to QuietOceanColors.textSupporting,
            "textMutedAccessible" to QuietOceanColors.textMutedAccessible,
            "statusHealthy" to QuietOceanColors.statusHealthy,
            "statusAttention" to QuietOceanColors.statusAttention,
            "statusError" to QuietOceanColors.statusError,
            "selection" to QuietOceanColors.selection,
            "focusRing" to QuietOceanColors.focusRing,
        )

        assertEquals(expected, actual)
        actual.forEach { (name, color) ->
            assertEquals("$name must stay opaque", 1f, color.alpha, 0f)
        }
    }

    @Test
    fun `material color scheme uses the approved semantic mappings`() {
        with(QuietOceanColorScheme) {
            assertEquals(QuietOceanColors.canvas, background)
            assertEquals(QuietOceanColors.canvas, surfaceDim)
            assertEquals(QuietOceanColors.surface, surface)
            assertEquals(QuietOceanColors.surfaceRaised, surfaceContainerHigh)
            assertEquals(QuietOceanColors.surfaceQuiet, surfaceContainerLow)
            assertEquals(QuietOceanColors.actionPrimary, primary)
            assertEquals(QuietOceanColors.onActionPrimary, onPrimary)
            assertEquals(QuietOceanColors.textPrimary, onBackground)
            assertEquals(QuietOceanColors.textPrimary, onSurface)
            assertEquals(QuietOceanColors.textSupporting, onSurfaceVariant)
            assertEquals(QuietOceanColors.selection, secondary)
            assertEquals(QuietOceanColors.statusHealthy, tertiary)
            assertEquals(QuietOceanColors.statusError, error)
        }
    }

    @Test
    fun `normal text roles meet wcag aa on every quiet ocean surface`() {
        val textRoles = mapOf(
            "textPrimary" to QuietOceanColors.textPrimary,
            "textSupporting" to QuietOceanColors.textSupporting,
            "textMutedAccessible" to QuietOceanColors.textMutedAccessible,
        )
        val surfaces = mapOf(
            "canvas" to QuietOceanColors.canvas,
            "surface" to QuietOceanColors.surface,
            "surfaceRaised" to QuietOceanColors.surfaceRaised,
            "surfaceQuiet" to QuietOceanColors.surfaceQuiet,
        )

        textRoles.forEach { (textName, textColor) ->
            surfaces.forEach { (surfaceName, surfaceColor) ->
                val ratio = contrastRatio(textColor, surfaceColor)
                assertTrue(
                    "$textName on $surfaceName was ${"%.2f".format(ratio)}:1",
                    ratio >= 4.5,
                )
            }
        }
        assertTrue(
            contrastRatio(QuietOceanColors.onActionPrimary, QuietOceanColors.actionPrimary) >= 4.5,
        )
        assertEquals(
            6.83,
            contrastRatio(QuietOceanColors.textMutedAccessible, QuietOceanColors.surface),
            0.01,
        )
    }

    @Test
    fun `status text roles meet wcag aa on their supported status surfaces`() {
        val statusRoles = mapOf(
            "statusHealthy" to QuietOceanColors.statusHealthy,
            "statusAttention" to QuietOceanColors.statusAttention,
            "statusError" to QuietOceanColors.statusError,
        )
        val supportedSurfaces = mapOf(
            "canvas" to QuietOceanColors.canvas,
            "surface" to QuietOceanColors.surface,
            "surfaceQuiet" to QuietOceanColors.surfaceQuiet,
        )

        statusRoles.forEach { (statusName, statusColor) ->
            supportedSurfaces.forEach { (surfaceName, surfaceColor) ->
                val ratio = contrastRatio(statusColor, surfaceColor)
                assertTrue(
                    "$statusName on $surfaceName was ${"%.2f".format(ratio)}:1",
                    ratio >= 4.5,
                )
            }
        }
    }

    @Test
    fun `named type scale matches approved size line height and weight`() {
        val expected = listOf(
            Triple(28.sp, 36.sp, FontWeight.Bold),
            Triple(28.sp, 34.sp, FontWeight.Bold),
            Triple(20.sp, 26.sp, FontWeight.SemiBold),
            Triple(16.sp, 24.sp, FontWeight.Normal),
            Triple(14.sp, 20.sp, FontWeight.Normal),
            Triple(14.sp, 18.sp, FontWeight.SemiBold),
        )
        val actual = listOf(
            QuietOceanTypeScale.focal,
            QuietOceanTypeScale.screenTitle,
            QuietOceanTypeScale.sectionTitle,
            QuietOceanTypeScale.body,
            QuietOceanTypeScale.supporting,
            QuietOceanTypeScale.label,
        )

        actual.zip(expected).forEach { (style, token) ->
            assertEquals(token.first, style.fontSize)
            assertEquals(token.second, style.lineHeight)
            assertEquals(token.third, style.fontWeight)
            assertEquals(FontFamily.Default, style.fontFamily)
        }
        assertEquals(QuietOceanTypeScale.focal, QuietOceanTypography.displayLarge)
        assertEquals(QuietOceanTypeScale.screenTitle, QuietOceanTypography.headlineLarge)
        assertEquals(QuietOceanTypeScale.sectionTitle, QuietOceanTypography.titleLarge)
        assertEquals(QuietOceanTypeScale.body, QuietOceanTypography.bodyLarge)
        assertEquals(QuietOceanTypeScale.supporting, QuietOceanTypography.bodyMedium)
        assertEquals(QuietOceanTypeScale.label, QuietOceanTypography.labelLarge)
        listOf(
            QuietOceanTypography.displayLarge,
            QuietOceanTypography.displayMedium,
            QuietOceanTypography.displaySmall,
            QuietOceanTypography.headlineLarge,
            QuietOceanTypography.headlineMedium,
            QuietOceanTypography.headlineSmall,
            QuietOceanTypography.titleLarge,
            QuietOceanTypography.titleMedium,
            QuietOceanTypography.titleSmall,
            QuietOceanTypography.bodyLarge,
            QuietOceanTypography.bodyMedium,
            QuietOceanTypography.bodySmall,
            QuietOceanTypography.labelLarge,
            QuietOceanTypography.labelMedium,
            QuietOceanTypography.labelSmall,
        ).forEach { style ->
            assertTrue("Material type must not drop below 14sp", style.fontSize >= 14.sp)
        }
    }

    @Test
    fun `shape spacing elevation and focus dimensions match approved scale`() {
        assertEquals(12.dp, QuietOceanRadii.control)
        assertEquals(20.dp, QuietOceanRadii.card)
        assertEquals(28.dp, QuietOceanRadii.focal)
        assertEquals(50, QuietOceanRadii.pillPercent)
        assertEquals(QuietOceanShapeScale.control, QuietOceanShapes.small)
        assertEquals(QuietOceanShapeScale.card, QuietOceanShapes.medium)
        assertEquals(QuietOceanShapeScale.focal, QuietOceanShapes.large)

        assertEquals(
            listOf(4.dp, 8.dp, 16.dp, 24.dp, 32.dp, 48.dp, 64.dp),
            listOf(
                QuietOceanSpacing.xs,
                QuietOceanSpacing.sm,
                QuietOceanSpacing.md,
                QuietOceanSpacing.lg,
                QuietOceanSpacing.xl,
                QuietOceanSpacing.xxl,
                QuietOceanSpacing.xxxl,
            ),
        )
        assertEquals(0.dp, QuietOceanElevation.standard)
        assertEquals(2.dp, QuietOceanElevation.focal)
        assertEquals(2.dp, QuietOceanFocus.outlineWidth)
        assertEquals(2.dp, QuietOceanFocus.separation)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val foregroundLuminance = relativeLuminance(foreground)
        val backgroundLuminance = relativeLuminance(background)
        return (max(foregroundLuminance, backgroundLuminance) + 0.05) /
            (min(foregroundLuminance, backgroundLuminance) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linearize(color.red) +
            0.7152 * linearize(color.green) +
            0.0722 * linearize(color.blue)

    private fun linearize(component: Float): Double =
        if (component <= 0.04045f) {
            component / 12.92
        } else {
            ((component + 0.055) / 1.055).pow(2.4)
        }
}
