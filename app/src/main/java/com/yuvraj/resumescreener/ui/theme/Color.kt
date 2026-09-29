package com.yuvraj.resumescreener.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tokens taken verbatim from the Sleek design project's `:root` block, kept in
 * `design/screens` for reference. Do not eyeball new colors; add them here and
 * use the token, so the palette stays a system rather than a pile of hex values.
 */

// Base surfaces
val Ink = Color(0xFF10151C) // --background
val InkCard = Color(0xFF161D25) // --card
val InkPopover = Color(0xFF1C2630) // --popover
val InkSecondary = Color(0xFF1B242E) // --secondary
val InkMuted = Color(0xFF27313C) // --muted
val InkInput = Color(0xFF1A232C) // --input
val InkBorder = Color(0xFF2B3743) // --border

// Text
val TextPrimary = Color(0xFFE8EDF2) // --foreground
val TextSecondary = Color(0xFFD6DEE7) // --secondary-foreground
val TextMuted = Color(0xFF91A0AF) // --muted-foreground

// The single saturated accent: a mint used for the affirmative end of the
// score spectrum and for primary actions. Saturation is deliberately scarce.
val Mint = Color(0xFFA7F3D0) // --primary
val MintInk = Color(0xFF0B1915) // --primary-foreground
val Amber = Color(0xFFF2B84B) // --accent
val AmberInk = Color(0xFF231A08) // --accent-foreground
val Danger = Color(0xFFE57373) // --destructive

// Score spectrum, low to high. The score is the app's one data visualization,
// so it gets a deliberate ramp rather than an arbitrary palette.
val Score1 = Color(0xFFC86B6B) // --chart-1
val Score2 = Color(0xFFD99453) // --chart-2
val Score3 = Color(0xFFE6B85C) // --chart-3
val Score4 = Color(0xFF82B879) // --chart-4
val Score5 = Color(0xFF4FAE7D) // --chart-5

/** Map a 1-10 fit score onto the design's spectrum. */
fun scoreColor(score: Float): Color = when {
    score < 3f -> Score1
    score < 5f -> Score2
    score < 6.5f -> Score3
    score < 8f -> Score4
    else -> Score5
}
