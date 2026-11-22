// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import social.aloha.core.designsystem.AlohaSpacing

/**
 * Rows one feature adds to a list another feature draws, so a page made of several features' parts
 * scrolls as one list, under the one header and tabs the page has. A section's item keys start with a
 * prefix of its own, so two sections on one page never share a key.
 */
@Immutable
public class ListSection(private val rows: LazyListScope.() -> Unit) {
    /** Adds the rows to [scope]. */
    public fun addTo(scope: LazyListScope) {
        scope.rows()
    }
}

/** The title over one part of a list, read as a heading. */
@Composable
public fun ListHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = AlohaSpacing.m, end = AlohaSpacing.m, top = AlohaSpacing.m, bottom = AlohaSpacing.xs)
            .semantics { heading() },
    )
}
