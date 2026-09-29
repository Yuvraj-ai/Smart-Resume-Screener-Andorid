package com.yuvraj.resumescreener.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The design specifies a 20px radius (--radius: 1.25rem) and a squircle
 * superellipse (--shape: squircle). Compose has no squircle primitive, so
 * these are rounded rects at the specified radius. The difference is subtle at
 * this radius and not worth a custom Shape implementation.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
