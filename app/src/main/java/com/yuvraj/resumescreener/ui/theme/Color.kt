package com.yuvraj.resumescreener.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tokens taken verbatim from the Sleek design project's `:root` block, kept in
 * `design/screens` for reference. Do not eyeball new colors; add them here and
 * use the token, so the palette stays a system rather than a pile of hex values.
 *
 * The dark scheme is the original. The light scheme is derived from the same
 * hue families rather than inverted, so the mint stays mint and the ink stays
 * ink: only lightness and contrast change with the mode.
 */

// ---- Dark: base surfaces -------------------------------------------------
val Ink = Color(0xFF10151C) // --background
val InkCard = Color(0xFF161D25) // --card
val InkPopover = Color(0xFF1C2630) // --popover
val InkSecondary = Color(0xFF1B242E) // --secondary
val InkMuted = Color(0xFF27313C) // --muted
val InkInput = Color(0xFF1A232C) // --input
val InkBorder = Color(0xFF2B3743) // --border

// ---- Dark: text ----------------------------------------------------------
val TextPrimary = Color(0xFFE8EDF2) // --foreground
val TextSecondary = Color(0xFFD6DEE7) // --secondary-foreground
val TextMuted = Color(0xFF91A0AF) // --muted-foreground

// ---- Dark: accents -------------------------------------------------------
val Mint = Color(0xFFA7F3D0) // --primary
val MintInk = Color(0xFF0B1915) // --primary-foreground
val Amber = Color(0xFFF2B84B) // --accent
val AmberInk = Color(0xFF231A08) // --accent-foreground
val Danger = Color(0xFFE57373) // --destructive

// ---- Dark: score spectrum, low to high -----------------------------------
val DarkScore1 = Color(0xFFC86B6B)
val DarkScore2 = Color(0xFFD99453)
val DarkScore3 = Color(0xFFE6B85C)
val DarkScore4 = Color(0xFF82B879)
val DarkScore5 = Color(0xFF4FAE7D)

// ---- Light: base surfaces ------------------------------------------------
// A paper white rather than pure #FFF, which is kinder at length and keeps
// the same "quiet instrument" feel the dark scheme has.
val Paper = Color(0xFFF6F8F7)
val PaperCard = Color(0xFFFFFFFF)
val PaperRaised = Color(0xFFFFFFFF)
val PaperMuted = Color(0xFFE9EEEC)
val PaperInput = Color(0xFFF1F4F3)
val PaperBorder = Color(0xFFC6CFCC)

// ---- Light: text ---------------------------------------------------------
val PaperTextPrimary = Color(0xFF0D1411)
val PaperTextSecondary = Color(0xFF33413C)
val PaperTextMuted = Color(0xFF5D6B66)

// ---- Light: accents ------------------------------------------------------
// The same mint hue, darkened until it clears 4.5:1 on paper. The pale mint
// from the dark scheme would be unreadable here.
val MintDeep = Color(0xFF0C6B54)
val MintDeepInk = Color(0xFFFFFFFF)
val MintWash = Color(0xFFC7F2E2)
val AmberDeep = Color(0xFF8A5300)
val DangerDeep = Color(0xFFB3261E)

// ---- Light: score spectrum, low to high ----------------------------------
// Slightly deeper than the dark ramp: mid-tones wash out against white.
val LightScore1 = Color(0xFFB3261E)
val LightScore2 = Color(0xFFA85A18)
val LightScore3 = Color(0xFF8A6A00)
val LightScore4 = Color(0xFF3F7A34)
val LightScore5 = Color(0xFF0C6B54)

/** Map a 1-10 fit score onto the spectrum for the given mode. */
fun scoreColor(score: Float, dark: Boolean = true): Color = if (dark) {
    when {
        score < 3f -> DarkScore1
        score < 5f -> DarkScore2
        score < 6.5f -> DarkScore3
        score < 8f -> DarkScore4
        else -> DarkScore5
    }
} else {
    when {
        score < 3f -> LightScore1
        score < 5f -> LightScore2
        score < 6.5f -> LightScore3
        score < 8f -> LightScore4
        else -> LightScore5
    }
}
