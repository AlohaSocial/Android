// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing

/**
 * The line where the previous visit ended: what is above it is new since, what is below was seen. It is
 * a place to stop, and a tap on it goes back to the newest posts.
 */
@Composable
public fun CaughtUpDivider(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.caught_up)
    val spoken = stringResource(R.string.caught_up_spoken, label)
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = spoken }
            .padding(horizontal = AlohaSpacing.l, vertical = AlohaSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
        Icon(
            AlohaIcons.Check,
            contentDescription = null,
            Modifier.padding(start = AlohaSpacing.m, end = AlohaSpacing.xs).size(ICON),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            label,
            Modifier.padding(end = AlohaSpacing.m),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
    }
}

private val ICON = 18.dp
