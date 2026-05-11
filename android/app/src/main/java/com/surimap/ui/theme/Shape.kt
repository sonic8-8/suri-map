package com.surimap.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val PoliRadiusSmall = 4.dp
val PoliRadiusMedium = 8.dp
val PoliRadiusLarge = 12.dp
val PoliRadiusBottomSheet = 20.dp

val PoliShapes =
    Shapes(
        extraSmall = RoundedCornerShape(PoliRadiusSmall),
        small = RoundedCornerShape(PoliRadiusSmall),
        medium = RoundedCornerShape(PoliRadiusMedium),
        large = RoundedCornerShape(PoliRadiusLarge),
        extraLarge = RoundedCornerShape(PoliRadiusBottomSheet)
    )
