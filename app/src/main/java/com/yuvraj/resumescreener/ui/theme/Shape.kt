package com.yuvraj.resumescreener.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive shape language.
 *
 * Expressive design leans on shape as a carrier of hierarchy, so this goes
 * further than the design's single 20px radius: chips and badges are fully
 * rounded, interactive elements sit at 16px, cards at 20px, and the top-level
 * containers at 28px. The contrast between levels is what makes the layout read
 * as expressive rather than merely rounded.
 *
 * Compose has no squircle primitive, so these are rounded rects.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Fully rounded, for chips, badges and pills. */
val PillShape = RoundedCornerShape(percent = 50)

/**
 * Expressive motion.
 *
 * Springs rather than tweens, so a press overshoots slightly and settles
 * instead of easing to a stop. Three named feels, so a component can pick one
 * without every call site inventing its own numbers.
 *
 * Damping ratio and stiffness are written as literals because the named
 * constants are not available in this Compose version; the values are the
 * same ones those constants hold.
 */
object AppMotion {

    /** Default for layout and state changes. Damping 0.5, stiffness 300. */
    val Expressive: SpringSpec<Float> = spring(dampingRatio = 0.5f, stiffness = 300f)

    /** Snappier, for chips and icons. Damping 0.7, stiffness 500. */
    val Snappy: SpringSpec<Float> = spring(dampingRatio = 0.7f, stiffness = 500f)

    /** Calm, for large surfaces entering or leaving. Damping 1.0, stiffness 200. */
    val Gentle: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 200f)
}
