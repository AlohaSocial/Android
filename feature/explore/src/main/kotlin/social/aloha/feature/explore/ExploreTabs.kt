// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.time.Instant
import social.aloha.core.data.explore.Explore
import social.aloha.core.data.explore.People
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.Account
import social.aloha.core.model.Card
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.model.StarterPack
import social.aloha.core.model.Status
import social.aloha.core.model.Tag
import social.aloha.core.network.endpoints.DirectoryOrder
import social.aloha.core.ui.AccountRow
import social.aloha.core.ui.LocalSensitiveMediaPolicy
import social.aloha.core.ui.RichLinkTarget
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusCard
import social.aloha.core.ui.StatusRowMapper

@Composable
internal fun PostsTab(posts: List<Status>, viewer: String, mapper: StatusRowMapper, rowActions: StatusActions) {
    if (posts.isEmpty()) return Empty(R.string.explore_posts_none)
    val rows = remember(posts, mapper) { posts.map { mapper.map(it, viewer) } }
    val now = remember { Instant.now() }
    LazyColumn(Modifier.fillMaxSize()) {
        items(rows, key = { it.rowId }) { row ->
            StatusCard(row, now, LocalSensitiveMediaPolicy.current, rowActions, showActions = false)
            HorizontalDivider()
        }
    }
}

/**
 * Trending hashtags, each with how much it was used. Nextcloud Social keeps one day of history and no
 * count of people, so no curve is drawn and no number of people is claimed.
 */
@Composable
internal fun HashtagsTab(tags: List<Tag>, state: ExploreUiState, actions: ExploreActions, rowActions: StatusActions) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (state.periods) {
            item(key = "periods") {
                Chips {
                    Explore.PERIODS.forEach { period ->
                        FilterChip(
                            selected = period == state.period,
                            onClick = { actions.onPeriod(period) },
                            label = { Text(stringResource(periodName(period))) },
                        )
                    }
                }
            }
        }
        if (tags.isEmpty()) item(key = "none") { Empty(R.string.explore_hashtags_none) }
        items(tags, key = { it.name }) { tag ->
            // a tag the server sent no history for says nothing of its use, rather than none
            val uses = tag.history.firstOrNull()?.usesCount
            ListItem(
                leadingContent = { Icon(AlohaIcons.Hashtag, contentDescription = null) },
                headlineContent = { Text("#${tag.name}") },
                supportingContent = uses?.let { { Text(pluralStringResource(R.plurals.explore_uses, it, it)) } },
                modifier = Modifier.clickable { rowActions.onLink(RichLinkTarget.Hashtag(tag.name)) },
            )
        }
    }
}

@Composable
internal fun NewsTab(links: List<Card>, onWeb: (String) -> Unit) {
    if (links.isEmpty()) return Empty(R.string.explore_news_none)
    LazyColumn(Modifier.fillMaxSize()) {
        items(links, key = { it.url ?: it.title }) { card ->
            ListItem(
                overlineContent = { Text(card.displayProvider, maxLines = 1) },
                headlineContent = { Text(card.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = card.description.takeIf { it.isNotBlank() }?.let {
                    { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                },
                trailingContent = card.image?.let { image ->
                    {
                        AsyncImage(
                            model = image,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(THUMBNAIL).clip(RoundedCornerShape(AlohaSpacing.xs)),
                        )
                    }
                },
                modifier = Modifier.clickable(enabled = card.url != null) { card.url?.let(onWeb) },
            )
            HorizontalDivider()
        }
    }
}

/** Who to follow: those suggested, which can be dismissed, the starter packs, and those popular here. */
@Composable
internal fun PeopleTab(
    people: People,
    state: ExploreUiState,
    mapper: StatusRowMapper,
    actions: ExploreActions,
    rowActions: StatusActions,
) {
    if (people.isEmpty) return Empty(R.string.explore_people_none)
    var asking by remember { mutableStateOf<StarterPack?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        section(R.string.explore_suggested, people.suggestions.isNotEmpty()) {
            items(people.suggestions, key = { "s:${it.id}" }) { suggestion ->
                Person(suggestion.account, mapper, rowActions) {
                    IconButton(onClick = { actions.onDismiss(suggestion.id) }) {
                        Icon(
                            AlohaIcons.Close,
                            stringResource(R.string.explore_dismiss, suggestion.account.bestDisplayName),
                        )
                    }
                }
            }
        }
        section(R.string.explore_packs, people.packs.isNotEmpty()) {
            items(people.packs, key = { "k:${it.slug}" }) { pack ->
                Pack(pack, followed = pack.slug in state.followed) { asking = pack }
            }
        }
        section(R.string.explore_popular, people.popular.isNotEmpty()) {
            items(people.popular, key = { "p:${it.id}" }) { Person(it, mapper, rowActions) }
        }
    }
    asking?.let { pack ->
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(stringResource(R.string.explore_follow_all_title, pack.name)) },
            text = { Text(pluralStringResource(R.plurals.explore_follow_all_body, pack.size, pack.size)) },
            confirmButton = {
                TextButton(onClick = {
                    asking = null
                    actions.onFollowAll(pack.slug)
                }) { Text(stringResource(R.string.explore_follow_all)) }
            },
            dismissButton = {
                TextButton(onClick = { asking = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun Pack(pack: StarterPack, followed: Boolean, onFollowAll: () -> Unit) {
    ListItem(
        headlineContent = { Text(pack.name) },
        supportingContent = {
            Text(
                listOfNotNull(
                    pack.description.takeIf { it.isNotBlank() },
                    pluralStringResource(R.plurals.explore_pack_size, pack.size, pack.size),
                ).joinToString(" · "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            if (followed) {
                Text(stringResource(R.string.explore_pack_followed), style = MaterialTheme.typography.labelLarge)
            } else {
                OutlinedButton(onClick = onFollowAll) { Text(stringResource(R.string.explore_follow_all)) }
            }
        },
    )
}

/**
 * The server's own directory: only the accounts here that chose to be found, by most recently active
 * or most recently joined, as far as the reader pages it. The footer says who is in it.
 */
@Composable
internal fun DirectoryTab(
    directory: Directory,
    mapper: StatusRowMapper,
    actions: ExploreActions,
    rowActions: StatusActions,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "order") {
            Chips {
                DirectoryOrder.entries.forEach { order ->
                    FilterChip(
                        selected = order == directory.order,
                        onClick = { actions.onOrder(order) },
                        label = { Text(stringResource(orderName(order))) },
                    )
                }
            }
        }
        items(directory.accounts, key = { it.id }) { Person(it, mapper, rowActions) }
        item(key = "footer") { DirectoryFooter(directory, actions) }
    }
}

@Composable
private fun DirectoryFooter(directory: Directory, actions: ExploreActions) {
    when {
        directory.trouble != null -> Failed(directory.trouble, actions::onMoreDirectory)

        !directory.end && !directory.loading -> TextButton(
            onClick = actions::onMoreDirectory,
            modifier = Modifier.fillMaxWidth().padding(AlohaSpacing.s),
        ) { Text(stringResource(R.string.explore_more)) }

        else -> Unit
    }
    Text(
        stringResource(R.string.explore_directory_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(AlohaSpacing.m),
    )
}

@Composable
private fun Person(
    account: Account,
    mapper: StatusRowMapper,
    rowActions: StatusActions,
    trailing: (@Composable () -> Unit)? = null,
) {
    val row = remember(account, mapper) { mapper.author(account) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        AccountRow(row, account.emojis, onOpen = { rowActions.onProfile(account.id) }, Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun Chips(content: @Composable () -> Unit) {
    Row(
        Modifier.horizontalScroll(
            rememberScrollState(),
        ).padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
    ) { content() }
}

/** A heading and what is under it, where there is anything. */
private fun LazyListScope.section(heading: Int, shown: Boolean, content: LazyListScope.() -> Unit) {
    if (!shown) return
    item(key = "h:$heading") {
        Text(
            stringResource(heading),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s)
                .semantics { heading() },
        )
    }
    content()
}

private fun periodName(period: String): Int = when (period) {
    "1h" -> R.string.explore_period_hour
    "12h" -> R.string.explore_period_half_day
    "3d" -> R.string.explore_period_three_days
    "10d" -> R.string.explore_period_ten_days
    else -> R.string.explore_period_day
}

private fun orderName(order: DirectoryOrder): Int = when (order) {
    DirectoryOrder.Active -> R.string.explore_order_active
    DirectoryOrder.New -> R.string.explore_order_new
}

private val THUMBNAIL = 64.dp
