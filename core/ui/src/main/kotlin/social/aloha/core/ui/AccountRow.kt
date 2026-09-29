// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import social.aloha.core.model.CustomEmoji

/** One account in a list: its avatar, its name with custom emoji, and its handle. */
@Composable
public fun AccountRow(
    author: StatusRowUi.AuthorUi,
    emojis: List<CustomEmoji>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable(onClick = onOpen),
        leadingContent = { Avatar(author.avatarUrl, ACCOUNT_AVATAR) },
        headlineContent = {
            Text(
                author.name,
                inlineContent = rememberEmojiContent(emojis, animate = true),
                style = MaterialTheme.typography.titleMedium.contentDirection(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = { Text(author.handle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

/**
 * Rich text below draws its links as addresses; this turns a tap on one back into a destination:
 * a profile, a hashtag, or a web page.
 */
@Composable
public fun ProvideLinkRouting(onLink: (RichLinkTarget) -> Unit, content: @Composable () -> Unit) {
    val route by rememberUpdatedState(onLink)
    val handler = remember { RoutingUriHandler { route(it) } }
    CompositionLocalProvider(LocalUriHandler provides handler, content = content)
}

private class RoutingUriHandler(private val route: (RichLinkTarget) -> Unit) : UriHandler {
    override fun openUri(uri: String) = route(RichLinkTarget.parse(uri))
}

private val ACCOUNT_AVATAR = 40.dp
