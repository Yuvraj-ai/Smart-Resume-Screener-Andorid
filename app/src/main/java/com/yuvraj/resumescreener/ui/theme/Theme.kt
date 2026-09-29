package com.yuvraj.resumescreener.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The design system is dark-only: the palette is built on a deep ink base and
 * there is no designed light theme. Rather than ship a half-considered light
 * scheme, the app stays dark regardless of system setting.
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
    surfaceContainer = InkCard,
    surfaceContainerHigh = InkPopover,
    surfaceContainerLow = InkSecondary,
    outline = InkBorder,
    outlineVariant = InkMuted,
    error = Danger,
    onError = Ink,
    scrim = Ink,
)

@Composable
fun ResumeScreenerTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = InkColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
