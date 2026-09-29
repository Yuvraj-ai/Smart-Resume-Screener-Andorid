package com.yuvraj.resumescreener.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Dark is the original design. Light is derived from the same hue families in
 * Color.kt, not inverted, so switching modes does not change the app's
 * identity, only its contrast.
 */
private val InkColorScheme = darkColorScheme(
    primary = Mint,
    onPrimary = MintInk,
    primaryContainer = InkSecondary,
    onPrimaryContainer = Mint,
    secondary = TextSecondary,
    onSecondary = Ink,
    secondaryContainer = InkSecondary,
    onSecondaryContainer = TextPrimary,
    tertiary = Amber,
    onTertiary = AmberInk,
    background = Ink,
    onBackground = TextPrimary,
    surface = Ink,
    onSurface = TextPrimary,
    surfaceVariant = InkSecondary,
    onSurfaceVariant = TextMuted,
    surfaceContainerLow = InkSecondary,
    surfaceContainer = InkCard,
    surfaceContainerHigh = InkPopover,
    surfaceContainerHighest = InkPopover,
    outline = InkBorder,
    outlineVariant = InkMuted,
    error = Danger,
    onError = Ink,
    scrim = Ink,
)

private val PaperColorScheme = lightColorScheme(
    primary = MintDeep,
    onPrimary = MintDeepInk,
    primaryContainer = MintWash,
    onPrimaryContainer = Color(0xFF00281D),
    secondary = PaperTextSecondary,
    onSecondary = Color.White,
    secondaryContainer = PaperMuted,
    onSecondaryContainer = PaperTextPrimary,
    tertiary = AmberDeep,
    onTertiary = Color.White,
    background = Paper,
    onBackground = PaperTextPrimary,
    surface = Paper,
    onSurface = PaperTextPrimary,
    surfaceVariant = PaperInput,
    onSurfaceVariant = PaperTextMuted,
    surfaceContainerLow = PaperInput,
    surfaceContainer = PaperCard,
    surfaceContainerHigh = PaperRaised,
    surfaceContainerHighest = PaperMuted,
    outline = PaperBorder,
    outlineVariant = PaperBorder,
    error = DangerDeep,
    onError = Color.White,
    scrim = Color(0x66000000),
)

/**
 * Exposes the active mode so score colours can pick the right ramp.
 *
 * The score spectrum is the app's only data visualization, and the dark ramp's
 * mid-tones wash out on paper, so it genuinely needs to differ per mode.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { true }

object AppTheme {
    val isDark: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalIsDarkTheme.current
}

@Composable
fun ResumeScreenerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) InkColorScheme else PaperColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
