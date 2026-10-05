// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import social.aloha.core.datastore.toRecord
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.PinnedFeed

/** The icons a reader can give a feed, by the name it is kept under. */
internal val FeedIcons: Map<String, ImageVector> = linkedMapOf(
    "home" to AlohaIcons.Home,
    "place" to AlohaIcons.Place,
    "public" to AlohaIcons.VisibilityPublic,
    "language" to AlohaIcons.Language,
    "notifications" to AlohaIcons.Notifications,
    "bookmark" to AlohaIcons.Bookmark,
    "favourite" to AlohaIcons.Favourite,
    "lists" to AlohaIcons.Lists,
    "group" to AlohaIcons.Group,
    "hashtag" to AlohaIcons.Hashtag,
    "explore" to AlohaIcons.Explore,
    "news" to AlohaIcons.News,
    "photos" to AlohaIcons.Photos,
    "video" to AlohaIcons.Video,
    "audio" to AlohaIcons.Audio,
    "recent" to AlohaIcons.Recent,
)

/** The icon a feed shows: the one the reader picked, or its kind's. */
internal fun feedIcon(feed: PinnedFeed): ImageVector = feed.icon?.let(FeedIcons::get) ?: when (feed.kind) {
    PinnedFeed.Kind.Following -> AlohaIcons.Home
    PinnedFeed.Kind.ThisServer -> AlohaIcons.Place
    PinnedFeed.Kind.Everyone -> AlohaIcons.VisibilityPublic
    PinnedFeed.Kind.Notified -> AlohaIcons.Notifications
    PinnedFeed.Kind.Bookmarks -> AlohaIcons.Bookmark
    PinnedFeed.Kind.Favourites -> AlohaIcons.Favourite
    is PinnedFeed.Kind.List -> AlohaIcons.Lists
    is PinnedFeed.Kind.Hashtag -> AlohaIcons.Hashtag
}

/** The name a feed goes by: the reader's, or its kind's. */
@Composable
internal fun feedName(feed: PinnedFeed): String = feed.name?.takeIf { it.isNotBlank() } ?: kindName(feed.kind)

@Composable
internal fun kindName(kind: PinnedFeed.Kind): String = when (kind) {
    PinnedFeed.Kind.Following -> stringResource(R.string.timeline_source_home)
    PinnedFeed.Kind.ThisServer -> stringResource(R.string.timeline_source_local)
    PinnedFeed.Kind.Everyone -> stringResource(R.string.timeline_source_federated)
    PinnedFeed.Kind.Notified -> stringResource(R.string.timeline_feed_notified)
    PinnedFeed.Kind.Bookmarks -> stringResource(R.string.timeline_feed_bookmarks)
    PinnedFeed.Kind.Favourites -> stringResource(R.string.timeline_feed_favourites)
    is PinnedFeed.Kind.List -> kind.title
    is PinnedFeed.Kind.Hashtag -> "#${kind.tags.name}"
}

/** What a kind of feed holds, said once on its first visit; nothing for Following, which every reader knows. */
private fun about(kind: PinnedFeed.Kind): Int? = when (kind) {
    PinnedFeed.Kind.Following -> null
    PinnedFeed.Kind.ThisServer -> R.string.timeline_feed_about_local
    PinnedFeed.Kind.Everyone -> R.string.timeline_feed_about_federated
    PinnedFeed.Kind.Notified -> R.string.timeline_feed_about_notified
    PinnedFeed.Kind.Bookmarks -> R.string.timeline_feed_about_bookmarks
    PinnedFeed.Kind.Favourites -> R.string.timeline_feed_about_favourites
    is PinnedFeed.Kind.List -> R.string.timeline_feed_about_list
    is PinnedFeed.Kind.Hashtag -> R.string.timeline_feed_about_hashtag
}

/** The name a kind of feed is remembered as explained under. */
internal val PinnedFeed.kindKey: String get() = toRecord().type

/** The explanation of [feed]'s kind until the reader puts it away, above its posts. */
@Composable
internal fun FeedBanner(feed: PinnedFeed, explained: Set<String>, onDismiss: () -> Unit) {
    val text = about(feed.kind)?.takeIf { feed.kindKey !in explained } ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            Modifier.padding(start = AlohaSpacing.m, end = AlohaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f).padding(vertical = AlohaSpacing.s),
            )
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.timeline_feed_about_dismiss)) }
        }
    }
}
