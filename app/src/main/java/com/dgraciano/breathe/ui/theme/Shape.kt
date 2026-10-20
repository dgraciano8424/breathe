package com.dgraciano.breathe.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object QuietOceanRadii {
    val control = 12.dp
    val card = 20.dp
    val focal = 28.dp
    const val pillPercent = 50
}

object QuietOceanShapeScale {
    val control = RoundedCornerShape(QuietOceanRadii.control)
    val card = RoundedCornerShape(QuietOceanRadii.card)
    val focal = RoundedCornerShape(QuietOceanRadii.focal)
    val pill = RoundedCornerShape(percent = QuietOceanRadii.pillPercent)
}

val QuietOceanShapes = Shapes(
    extraSmall = QuietOceanShapeScale.control,
    small = QuietOceanShapeScale.control,
    medium = QuietOceanShapeScale.card,
    large = QuietOceanShapeScale.focal,
    extraLarge = QuietOceanShapeScale.focal,
)
