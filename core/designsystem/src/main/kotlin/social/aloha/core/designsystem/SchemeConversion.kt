// SPDX-FileCopyrightText: 2024 Nextcloud GmbH and Nextcloud contributors
// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT
//
// Adapted from nextcloud/android-common (ui/…/extensions/SchemeExtensions.kt, MIT).

package social.aloha.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.scheme.DynamicScheme

/** A MaterialKolor scheme as a Compose Material 3 [ColorScheme]. */
@Suppress("LongMethod") // a one-to-one field mapping; splitting it hides nothing
internal fun DynamicScheme.toColorScheme(): ColorScheme {
    val c = MaterialDynamicColors()
    fun argb(color: DynamicColor) = Color(color.getArgb(this))
    return ColorScheme(
        primary = argb(c.primary()),
        onPrimary = argb(c.onPrimary()),
        primaryContainer = argb(c.primaryContainer()),
        onPrimaryContainer = argb(c.onPrimaryContainer()),
        inversePrimary = argb(c.inversePrimary()),
        secondary = argb(c.secondary()),
        onSecondary = argb(c.onSecondary()),
        secondaryContainer = argb(c.secondaryContainer()),
        onSecondaryContainer = argb(c.onSecondaryContainer()),
        tertiary = argb(c.tertiary()),
        onTertiary = argb(c.onTertiary()),
        tertiaryContainer = argb(c.tertiaryContainer()),
        onTertiaryContainer = argb(c.onTertiaryContainer()),
        background = argb(c.background()),
        onBackground = argb(c.onBackground()),
        surface = argb(c.surface()),
        onSurface = argb(c.onSurface()),
        surfaceVariant = argb(c.surfaceVariant()),
        onSurfaceVariant = argb(c.onSurfaceVariant()),
        surfaceTint = argb(c.surfaceTint()),
        inverseSurface = argb(c.inverseSurface()),
        inverseOnSurface = argb(c.inverseOnSurface()),
        error = argb(c.error()),
        onError = argb(c.onError()),
        errorContainer = argb(c.errorContainer()),
        onErrorContainer = argb(c.onErrorContainer()),
        outline = argb(c.outline()),
        outlineVariant = argb(c.outlineVariant()),
        scrim = argb(c.scrim()),
        surfaceBright = argb(c.surfaceBright()),
        surfaceDim = argb(c.surfaceDim()),
        surfaceContainer = argb(c.surfaceContainer()),
        surfaceContainerHigh = argb(c.surfaceContainerHigh()),
        surfaceContainerHighest = argb(c.surfaceContainerHighest()),
        surfaceContainerLow = argb(c.surfaceContainerLow()),
        surfaceContainerLowest = argb(c.surfaceContainerLowest()),
        primaryFixed = argb(c.primaryFixed()),
        primaryFixedDim = argb(c.primaryFixedDim()),
        onPrimaryFixed = argb(c.onPrimaryFixed()),
        onPrimaryFixedVariant = argb(c.onPrimaryFixedVariant()),
        secondaryFixed = argb(c.secondaryFixed()),
        secondaryFixedDim = argb(c.secondaryFixedDim()),
        onSecondaryFixed = argb(c.onSecondaryFixed()),
        onSecondaryFixedVariant = argb(c.onSecondaryFixedVariant()),
        tertiaryFixed = argb(c.tertiaryFixed()),
        tertiaryFixedDim = argb(c.tertiaryFixedDim()),
        onTertiaryFixed = argb(c.onTertiaryFixed()),
        onTertiaryFixedVariant = argb(c.onTertiaryFixedVariant()),
    )
}
