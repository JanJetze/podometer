// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ─── Activity colour container ────────────────────────────────────────────────

/**
 * Centralised activity colours accessible via [LocalActivityColors].
 *
 * Includes background colours for each activity state and a [contentColor]
 * (icon/text colour) to render on top of those backgrounds.
 *
 * @property walking      Background colour for walking activity segments.
 * @property cycling      Background colour for cycling activity segments.
 * @property still        Background colour for still/no-activity segments.
 * @property contentColor Foreground colour (icon + text) rendered on all backgrounds.
 */
@Immutable
data class ActivityColors(
    val walking: Color,
    val cycling: Color,
    val still: Color,
    val contentColor: Color = Color.White,
)

/**
 * Default [ActivityColors] for the **light** theme.
 *
 * All background colours achieve at least 4.5:1 contrast against white text (WCAG AA).
 */
val DefaultActivityColors = ActivityColors(
    walking = ActivityWalking,
    cycling = ActivityCycling,
    still = ActivityStill,
    contentColor = Color.White,
)

/**
 * [ActivityColors] for the **dark** theme.
 *
 * All background colours achieve at least 4.5:1 contrast against black text (WCAG AA).
 */
val DarkActivityColors = ActivityColors(
    walking = ActivityWalkingLight,
    cycling = ActivityCyclingLight,
    still = ActivityStillLight,
    contentColor = Color.Black,
)

/**
 * CompositionLocal that provides [ActivityColors] throughout the composable tree.
 *
 * Access via `LocalActivityColors.current` inside any composable wrapped by [PodometerTheme].
 * Defaults to [DefaultActivityColors] if no provider is present in the tree.
 */
val LocalActivityColors = staticCompositionLocalOf { DefaultActivityColors }

// ─── Material 3 colour schemes ────────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = TealContainer80,
    onPrimaryContainer = OnTealContainer10,
    secondary = TealGrey40,
    onSecondary = Color.White,
    tertiary = Amber40,
    onTertiary = Color.White,
    error = Red40,
    onError = Color.White,
    background = NeutralLightSurface,
    onBackground = NeutralLightOnSurface,
    surface = NeutralLightSurface,
    onSurface = NeutralLightOnSurface,
    surfaceVariant = NeutralLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF3F4947),
)

private val DarkColorScheme = darkColorScheme(
    primary = Teal80,
    onPrimary = OnTeal20,
    primaryContainer = Color(0xFF004F47),
    onPrimaryContainer = TealContainer80,
    secondary = TealGrey80,
    onSecondary = Color(0xFF1C3533),
    tertiary = Amber80,
    onTertiary = Color(0xFF412D00),
    error = Red80,
    onError = Color(0xFF690005),
    background = NeutralDarkSurface,
    onBackground = NeutralDarkOnSurface,
    surface = NeutralDarkSurface,
    onSurface = NeutralDarkOnSurface,
    surfaceVariant = NeutralDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFBEC9C6),
)

// ─── Theme composable ─────────────────────────────────────────────────────────

/**
 * Root Material 3 theme for the Podometer app.
 *
 * Provides the full Material 3 colour scheme, typography scale, and centralised
 * activity colours via [LocalActivityColors].
 *
 * Since minSdk = 33 (Android 13), dynamic colour (Material You) is always available
 * at runtime. The [dynamicColor] parameter is exposed so that previews and tests can
 * opt out and use the static fallback scheme instead.
 *
 * @param darkTheme     Whether to use the dark colour scheme. Defaults to the system setting.
 * @param dynamicColor  Whether to use dynamic colour from the device wallpaper (Material You).
 *                      Defaults to `true`. Set to `false` in previews or tests.
 * @param content       The composable content to theme.
 */
@Composable
fun PodometerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val activityColors = if (darkTheme) DarkActivityColors else DefaultActivityColors

    CompositionLocalProvider(LocalActivityColors provides activityColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PodometerTypography,
            content = content,
        )
    }
}
