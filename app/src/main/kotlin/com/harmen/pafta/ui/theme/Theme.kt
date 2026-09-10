package com.harmen.pafta.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing and size constants, so the chrome stays on one rhythm. */
public data class HarmenMetrics(
    val gutter: Dp = 12.dp,
    val gutterTight: Dp = 8.dp,
    val hairline: Dp = 1.dp,
    val topBarRowHeight: Dp = 44.dp,
    val topBarTabRowHeight: Dp = 36.dp,
    val toolRailWidth: Dp = 64.dp,
    val toolRailWidthCompact: Dp = 52.dp,
    val rightPanelWidth: Dp = 232.dp,
    val monogram: Dp = 28.dp,
    val cornerRadius: Dp = 2.dp,
    /** Minimum touch target; the rail and tabs never go below it. */
    val touchTarget: Dp = 44.dp,
)

public val LocalHarmenMetrics: ProvidableCompositionLocal<HarmenMetrics> =
    staticCompositionLocalOf { HarmenMetrics() }

/** True when the window is too narrow for the full three-column layout. */
public val LocalCompactLayout: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }

/**
 * PAFTA's theme.
 *
 * The app is dark in every system theme: a CAD sheet on a light ground would
 * wash out the hairline linework, so the system light/dark setting is
 * deliberately not consulted. Material 3 is wired up only so that its components inherit the
 * palette — the chrome is drawn by PAFTA's own composables.
 */
@Composable
public fun PaftaTheme(
    metrics: HarmenMetrics = HarmenMetrics(),
    content: @Composable () -> Unit,
) {
    val scheme = darkColorScheme(
        primary = HarmenColours.Accent,
        onPrimary = HarmenColours.Ground,
        primaryContainer = HarmenColours.AccentDeep,
        onPrimaryContainer = HarmenColours.Text,
        secondary = HarmenColours.TextMuted,
        onSecondary = HarmenColours.Ground,
        background = HarmenColours.Ground,
        onBackground = HarmenColours.Text,
        surface = HarmenColours.Panel,
        onSurface = HarmenColours.Text,
        surfaceVariant = HarmenColours.PanelRaised,
        onSurfaceVariant = HarmenColours.TextMuted,
        outline = HarmenColours.Hairline,
        outlineVariant = HarmenColours.Hairline,
    )

    CompositionLocalProvider(LocalHarmenMetrics provides metrics) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(
                bodyMedium = HarmenType.Body,
                bodySmall = HarmenType.PropertyKey,
                labelSmall = HarmenType.MenuCaps,
                titleMedium = HarmenType.ProjectTitle,
            ),
            content = content,
        )
    }
}

/** Convenience accessor mirroring `MaterialTheme.colorScheme`. */
public val metrics: HarmenMetrics
    @Composable @ReadOnlyComposable get() = LocalHarmenMetrics.current
