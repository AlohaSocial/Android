// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing

/** All the app shows while locked: that it is, and the way in, which it offers at once. */
@Composable
internal fun LockScreen(onUnlock: () -> Unit) {
    val title = stringResource(R.string.lock_title)
    LaunchedEffect(Unit) { onUnlock() }
    Surface(Modifier.fillMaxSize().semantics { paneTitle = title }) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(AlohaSpacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.l, Alignment.CenterVertically),
        ) {
            Icon(
                AlohaIcons.VisibilityPrivate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(ICON),
            )
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onUnlock) { Text(stringResource(R.string.lock_unlock)) }
        }
    }
}

private val ICON = 56.dp
