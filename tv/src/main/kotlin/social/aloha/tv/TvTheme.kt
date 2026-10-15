// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import social.aloha.core.designsystem.ALOHA_SEED
import social.aloha.core.designsystem.ContrastLevel
import social.aloha.core.designsystem.seededColorScheme

/** The phone's dark scheme from the brand's seed, in Compose for TV's terms: a TV room is dark. */
private val scheme = seededColorScheme(ALOHA_SEED, dark = true, ContrastLevel.Standard).let {
    darkColorScheme(
        primary = it.primary,
        onPrimary = it.onPrimary,
        primaryContainer = it.primaryContainer,
        onPrimaryContainer = it.onPrimaryContainer,
        secondary = it.secondary,
        onSecondary = it.onSecondary,
        secondaryContainer = it.secondaryContainer,
        onSecondaryContainer = it.onSecondaryContainer,
        background = it.background,
        onBackground = it.onBackground,
        surface = it.surface,
        onSurface = it.onSurface,
        surfaceVariant = it.surfaceVariant,
        onSurfaceVariant = it.onSurfaceVariant,
        inverseSurface = it.inverseSurface,
        inverseOnSurface = it.inverseOnSurface,
        error = it.error,
        onError = it.onError,
        errorContainer = it.errorContainer,
        onErrorContainer = it.onErrorContainer,
        border = it.outline,
        borderVariant = it.outlineVariant,
    )
}

@Composable
internal fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

/** The 10-foot safe area: nothing that matters within 5 % of a TV's edges, where overscan may cut it. */
internal object TvInsets {
    val horizontal = 48.dp
    val vertical = 27.dp
}
